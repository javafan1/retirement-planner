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

class VirginiaFundingTest {
    private final LocalDate date = LocalDate.of(2026, 1, 1);
    private final Household household = new Household(new Person("Primary", "Example", LocalDate.of(1966, 6, 1)));
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
        withinCent(b("7150.03"), funded.getAdditionalWithdrawal());
        withinCent(b("2472.03"), funded.getStateTaxCalculation().incomeTax());
        withinCent(b("4678.00"), funded.getFederalTaxCalculation().getFederalIncomeTax());
        assertEquals(b("2060.90"), fund(allocation, "0", "100000").getStateTaxCalculation().incomeTax());
    }

    @Test
    void stateAwareBracketFillIncludesFundingAndRetirementCap() throws Exception {
        var allocation = spending("1000000", "15000");
        var amount = fill(allocation, "40000", "0");
        // Independent Phase 6E-I worksheet: AGI 56100, modeled federal tax 4552.
        withinCent(b("34136.35"), amount);
        withinCent(b("2411.65"), fund(allocation, amount.toPlainString(), "0").getStateTaxCalculation().incomeTax());
        withinCent(b("41100"), fill(allocation, "40000", "100000"));
    }

    private BigDecimal fill(ProjectedWithdrawalAllocation allocation, String target, String cash) throws Exception {
        return new RothConversionBracketFillCalculator().calculateConversion(household, date, allocation.getPortfolio(),
                allocation.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                new GovernmentRulesRepository().load("/rules/government-rules-2026.json"), b(target), b("0"), null, null,
                b("0"), null, b(cash), Optional.empty(), List.of(), TaxHouseholdContext.capture(household, date, null),
                Set.of(AccountOwnership.PRIMARY), StateTaxAssumptions.simplified(StateTaxJurisdiction.VA));
    }

    @Test
    void fundingRecomputesSeniorPhaseoutRatherThanUsingFlatMarginalRate() throws Exception {
        var senior=new Household(new Person("Primary","Example",LocalDate.of(1961,6,1)));
        var allocation=spending("1000000","50000");
        var funded=new TaxFundingCalculator().calculate(senior,date,allocation.getPortfolio(),allocation.getWithdrawalBreakdown(),
                strategy,FilingStatus.SINGLE,new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                b("0"),b("0"),null,null,b("0"),null,b("0"),Optional.empty(),List.of(),
                TaxHouseholdContext.capture(senior,date,null),StateTaxAssumptions.simplified(StateTaxJurisdiction.VA));
        // (3820 federal + 1324.90 VA)/(1-.12-.115), unchanged modeled federal coverage.
        withinCent(b("6725.36"),funded.getAdditionalWithdrawal());
        withinCent(b("4627.04"),funded.getFederalTaxCalculation().getFederalIncomeTax());
        withinCent(b("2098.32"),funded.getStateTaxCalculation().incomeTax());
    }
    @Test
    void fundingCrossesStrictNoTaxThresholdAndConverges() throws Exception {
        var senior=new Household(new Person("Primary","Example",LocalDate.of(1961,6,1)));
        var allocation=spending("1000000","23900");
        var funded=new TaxFundingCalculator().calculate(senior,date,allocation.getPortfolio(),allocation.getWithdrawalBreakdown(),
                strategy,FilingStatus.SINGLE,new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                b("0"),b("0"),null,null,b("0"),null,b("0"),Optional.empty(),List.of(),
                TaxHouseholdContext.capture(senior,date,null),StateTaxAssumptions.simplified(StateTaxJurisdiction.VA));
        // Initially VAGI 11900 is exempt. Funding moves it above 11950.
        // W=(780 federal + .02*1420)/(1-.10-.02)=918.63636..., using existing cent tolerance.
        withinCent(b("918.64"),funded.getAdditionalWithdrawal());
        withinCent(b("871.86"),funded.getFederalTaxCalculation().getFederalIncomeTax());
        withinCent(b("46.77"),funded.getStateTaxCalculation().incomeTax());
    }

    private TaxFundingResult fund(ProjectedWithdrawalAllocation allocation, String conversion, String cash) throws Exception {
        return new TaxFundingCalculator().calculate(household, date, allocation.getPortfolio(), allocation.getWithdrawalBreakdown(),
                strategy, FilingStatus.SINGLE, new GovernmentRulesRepository().load("/rules/government-rules-2026.json"),
                b(conversion), b("0"), null, null, b("0"), null, b(cash), Optional.empty(),
                List.of(GeorgiaProductionTaxCalculatorTest.conversion(AccountOwnership.PRIMARY, conversion, 2026)),
                TaxHouseholdContext.capture(household, date, null), StateTaxAssumptions.simplified(StateTaxJurisdiction.VA));
    }
    private static BigDecimal b(String value) { return new BigDecimal(value); }
    private static void withinCent(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.subtract(actual).abs().compareTo(b("0.01")) <= 0, () -> expected + " vs " + actual);
    }
}
