package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rmd.*;
import com.daviddunn.retirementplanner.domain.rules.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MichiganRmdFeedbackTest {
    private static BigDecimal money(String text) { return new BigDecimal(text); }

    private static GovernmentRules rules() throws Exception {
        return new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
    }

    private static GovernmentRules oldMichiganRules(GovernmentRules current) {
        return new GovernmentRules(current.getRulesVersion(), current.getTaxYear(), current.getEffectiveDate(),
                current.getFederalTaxRules(), new MichiganTaxRules(money("0.0425"), money("40762"), money("81524")),
                current.getIrmaaRules(), current.getSocialSecurityTaxRules(), current.getRmdRules());
    }

    @Test
    void identicalBalancesAndAgesHaveIdenticalRmdUnderBothMichiganRuleSets() throws Exception {
        var current = rules();
        var previous = oldMichiganRules(current);
        for (int age : List.of(73, 76, 77, 85)) {
            for (String balance : List.of("0", "600000", "905847.30", "907734.48")) {
                assertEquals(new RmdCalculator().calculateRmd(money(balance), age, previous),
                        new RmdCalculator().calculateRmd(money(balance), age, current));
            }
        }
        assertSame(previous.getRmdRules(), current.getRmdRules());
    }

    @Test
    void documentedBalanceFeedbackUsesSameDivisor() throws Exception {
        var rules = rules();
        assertEquals(0, money("22.9").compareTo(rules.getRmdRules().getLifeExpectancyFactor(77).getDistributionPeriod()));
        assertEquals(money("39556.65"), new RmdCalculator().calculateRmd(money("905847.30"), 77, rules));
        assertEquals(money("39639.06"), new RmdCalculator().calculateRmd(money("907734.48"), 77, rules));
    }

    @Test
    void correctedFundingProducesDocumentedBalanceAndFollowingRmd() throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, money("1000000"));
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1950, 1, 1)));
        var allocator = new ProjectedWithdrawalAllocator();
        var strategy = new TaxDeferredFirstWithdrawalStrategy();
        var spending = allocator.allocateAdditionalWithdrawal(new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(account, account.getCurrentBalance()))), money("80000"), strategy);
        var funding = new TaxFundingCalculator().calculate(household, LocalDate.of(2026, 1, 1),
                spending.getPortfolio(), spending.getWithdrawalBreakdown(), strategy, FilingStatus.SINGLE,
                rules(), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(money("12265.52"), funding.getAdditionalWithdrawal().setScale(2, RoundingMode.HALF_UP));
        BigDecimal ending = money("920000").subtract(funding.getAdditionalWithdrawal());
        assertEquals(money("907734.48"), ending.setScale(2, RoundingMode.HALF_UP));
        assertEquals(money("39639.06"), new RmdCalculator().calculateRmd(ending, 77, rules()));
        assertTrue(funding.getIncomeSources().stream().anyMatch(source ->
                source.purpose() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING));
        assertTrue(funding.getAdditionalWithdrawal().subtract(funding.getFederalTaxCalculation().getFederalIncomeTax()
                .add(funding.getMichiganTaxCalculation().incomeTax())).abs().compareTo(money("0.01")) <= 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openingRmdAndOwnerAttributionAreUnchangedAndConversionsFollowRmd(boolean couple) throws Exception {
        var plan = SinglePersonCoreProjectionTest.plan(couple,
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        var current = rules();
        var old = oldMichiganRules(current);
        var calculator = new OpeningRmdCalculator();
        var before = calculator.calculate(plan, 2027, old);
        var after = calculator.calculate(plan, 2027, current);
        assertEquals(before.getAnnualRequirement().getTotalRmd(), after.getAnnualRequirement().getTotalRmd());
        assertEquals(before.getDistributedBeforeProjection(), after.getDistributedBeforeProjection());
        assertEquals(before.getRemainingRequirement().getTotalRmd(), after.getRemainingRequirement().getTotalRmd());
        var result = new ProjectionEngine().project(plan);
        var first = result.getYearAt(0);
        assertEquals(0, first.getRequiredMinimumDistribution().compareTo(after.getAnnualRequirement().getTotalRmd()));
        assertEquals(0, first.getRmdDistributedBeforeProjection().compareTo(money(couple ? "20000" : "15000")));
        assertEquals(0, first.getRothConversion().compareTo(money("30000")));
        var kinds = first.getIncomeSources().stream().map(source -> source.kind()).toList();
        assertTrue(kinds.contains(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.RMD));
        assertTrue(kinds.contains(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION));
        assertTrue(kinds.indexOf(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.RMD)
                < kinds.indexOf(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION));
        if (!couple) {
            assertTrue(first.getIncomeSources().stream().noneMatch(source ->
                    source.owner().orElse(null) == AccountOwnership.SPOUSE));
        } else {
            assertTrue(first.getIncomeSources().stream().anyMatch(source -> source.isRetirementDistribution()
                    && source.owner().orElse(null) == AccountOwnership.SPOUSE));
        }
    }
}
