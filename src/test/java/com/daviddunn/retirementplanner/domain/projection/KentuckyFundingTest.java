package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.RothConversionBracketFillCalculator;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KentuckyFundingTest {
    private final LocalDate date = LocalDate.of(2026, 1, 1);
    private final Household household = new Household(new Person("Primary", "Example", LocalDate.of(1966, 1, 1)));
    private final WithdrawalStrategy strategy = new TaxDeferredFirstWithdrawalStrategy();

    private ProjectedWithdrawalAllocation spending(String opening, String amount) {
        var ira = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, b(opening));
        var roth = new RothIRA("Roth", AccountOwnership.PRIMARY, b("0"));
        var portfolio = new ProjectedPortfolio(List.of(new ProjectedAccountBalance(ira, b(opening)), new ProjectedAccountBalance(roth, b("0"))));
        return new ProjectedWithdrawalAllocator().allocateAdditionalWithdrawal(portfolio, b(amount), strategy);
    }

    @Test
    void independentFederalAndStateFeedbackAndCashFunding() throws Exception {
        var allocation = spending("1000000", "50000");
        var funded = fund(allocation, "0", "0");
        withinCent(b("5163.97"), funded.getAdditionalWithdrawal());
        withinCent(b("724.29"), funded.getStateTaxCalculation().incomeTax());
        withinCent(b("4439.68"), funded.getFederalTaxCalculation().getFederalIncomeTax());
        assertEquals(b("543.55"), fund(allocation, "0", "100000").getStateTaxCalculation().incomeTax());
    }

    @Test
    void stateAwareBracketFillIncludesFundingAndRetirementCap() throws Exception {
        var allocation = spending("1000000", "15000");
        var amount = fill(allocation, "40000", "0");
        // AGI 56100; federal tax 4552; KY (56100-31110-3360)*.035=757.05.
        withinCent(b("35790.95"), amount);
        withinCent(b("757.05"), fund(allocation, amount.toPlainString(), "0").getStateTaxCalculation().incomeTax());
        withinCent(b("41100"), fill(allocation, "40000", "100000"));
    }

    @Test
    void capacityLimitedTrialDoesNotInventUnownedIncome() throws Exception {
        assertEquals(0, b("1000").compareTo(fill(spending("1000", "0"), "20000", "100000")));
    }

    private BigDecimal fill(ProjectedWithdrawalAllocation allocation, String target, String cash) throws Exception {
        return new RothConversionBracketFillCalculator().calculateConversion(household, date, allocation.getPortfolio(),
                allocation.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"), b(target), b("0"), null, null,
                b("0"), null, b(cash), Optional.empty(), List.of(), TaxHouseholdContext.capture(household, date, null),
                Set.of(AccountOwnership.PRIMARY), StateTaxAssumptions.simplified(StateTaxJurisdiction.KY));
    }

    private TaxFundingResult fund(ProjectedWithdrawalAllocation allocation, String conversion, String cash) throws Exception {
        return new TaxFundingCalculator().calculate(household, date, allocation.getPortfolio(), allocation.getWithdrawalBreakdown(),
                strategy, FilingStatus.SINGLE, new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                b(conversion), b("0"), null, null, b("0"), null, b(cash), Optional.empty(),
                List.of(KentuckyProductionTaxCalculatorTest.conversion(AccountOwnership.PRIMARY, conversion, 2026)),
                TaxHouseholdContext.capture(household, date, null), StateTaxAssumptions.simplified(StateTaxJurisdiction.KY));
    }
    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static void withinCent(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.subtract(actual).abs().compareTo(b("0.01")) <= 0, () -> expected + " vs " + actual);
    }
}
