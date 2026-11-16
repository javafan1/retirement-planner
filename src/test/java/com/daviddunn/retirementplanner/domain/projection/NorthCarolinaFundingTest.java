package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.RothConversionBracketFillCalculator;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NorthCarolinaFundingTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);
    private final Household household = new Household(new Person("Primary", "Example", LocalDate.of(1965, 6, 1)));
    private final TaxDeferredFirstWithdrawalStrategy strategy = new TaxDeferredFirstWithdrawalStrategy();
    private final TaxHouseholdContext context = TaxHouseholdContext.capture(household, DATE, null);

    private ProjectedWithdrawalAllocation spending(String amount) {
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, b("1000000"));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, b("100000"));
        return new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(ira, ira.getCurrentBalance()),
                new ProjectedAccountBalance(roth, roth.getCurrentBalance()))), b(amount), strategy);
    }

    private TaxFundingResult funding(ProjectedWithdrawalAllocation spending, String conversion, String cash) throws Exception {
        return new TaxFundingCalculator().calculate(household, DATE, spending.getPortfolio(),
                spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"), b(conversion),
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, b(cash), Optional.empty(), List.of(),
                context, StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
    }

    @Test
    void independentStateOnlyGrossUpAndCashFunding() throws Exception {
        // T = .0399 * (15000 + T - 12750); federal AGI remains below 16100.
        var traditional = funding(spending("15000"), "0", "0");
        assertEquals(b("93.51"), cents(traditional.getAdditionalWithdrawal()));
        assertEquals(b("93.51"), traditional.getStateTaxCalculation().incomeTax());
        assertEquals(b("15093.51"), cents(traditional.getFederalTaxCalculation().getAdjustedGrossIncome()));
        assertEquals(0, traditional.getFederalTaxCalculation().getFederalIncomeTax().signum());
        var cash = funding(spending("15000"), "0", "10000");
        assertEquals(0, cash.getAdditionalWithdrawal().signum());
        assertEquals(b("89.78"), cash.getStateTaxCalculation().incomeTax());
    }

    @Test
    void conversionCrossesDeductionFloorAndFundsItsTax() throws Exception {
        assertEquals(b("0.00"), funding(spending("12250"), "0", "0").getStateTaxCalculation().incomeTax());
        // T = .0399 * (12250 + 1000 + T - 12750).
        var result = funding(spending("12250"), "1000", "0");
        assertEquals(b("20.78"), cents(result.getAdditionalWithdrawal()));
        assertEquals(b("20.78"), result.getStateTaxCalculation().incomeTax());
        assertEquals(0, result.getFederalTaxCalculation().getFederalIncomeTax().signum());
    }

    @Test
    void independentCombinedFundingAndStateAwareBracketFill() throws Exception {
        var spending = spending("15000");
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var calculator = new RothConversionBracketFillCalculator();
        var conversion = calculator.calculateConversion(household, DATE, spending.getPortfolio(),
                spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE, rules, b("5000"),
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, BigDecimal.ZERO, Optional.empty(),
                List.of(), context, Set.of(AccountOwnership.PRIMARY), StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
        // IRS 2026 1040-ES: AGI 21100, federal taxable 5000, federal tax 500.
        // NC (21100 - 12750)*.0399 = 333.17; conversion = 21100-15000-833.17.
        assertEquals(b("5266.83"), cents(conversion));
        var result = funding(spending, conversion.toPlainString(), "0");
        // Existing funding/bracket solvers admit a cent of residual. At the exact
        // target NC raw tax is 333.165, so a sub-cent AGI residual can round to 333.16.
        withinCent(b("833.17"), result.getAdditionalWithdrawal());
        withinCent(b("333.17"), result.getStateTaxCalculation().incomeTax());
        assertEquals(b("500.00"), cents(result.getFederalTaxCalculation().getFederalIncomeTax()));
        assertTrue(result.getFederalTaxCalculation().getTaxableIncome().subtract(b("5000")).abs().compareTo(b("0.01")) <= 0);
        var cashConversion = calculator.calculateConversion(household, DATE, spending.getPortfolio(),
                spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE, rules, b("5000"),
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, b("10000"), Optional.empty(),
                List.of(), context, Set.of(AccountOwnership.PRIMARY), StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
        assertEquals(b("6100.00"), cents(cashConversion));
    }

    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static BigDecimal cents(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static void withinCent(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.subtract(cents(actual)).abs().compareTo(b("0.01")) <= 0, () -> expected + " versus " + actual);
    }
}
