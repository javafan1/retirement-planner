package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.GovernmentRuleProjectionService;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MichiganAttributedProjectionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multiYearTaxUsesOwnerSourcesAndIndexedRules(boolean couple) throws Exception {
        var filing = couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var plan = SinglePersonCoreProjectionTest.plan(couple, filing);
        var base = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var projection = new ProjectionEngine().project(plan);
        for (var year : projection.getYears()) {
            var rules = new GovernmentRuleProjectionService().project(base, plan.getPlanningAssumptions(), year.getCalendarYear());
            var tax = year.michiganTaxCalculation().orElseThrow();
            BigDecimal eligible = year.getIncomeSources().stream()
                    .filter(IncomeSourceAttribution::isRetirementDistribution)
                    .filter(source -> source.account().isPresent()
                            && source.account().get().type() == AccountType.TRADITIONAL_IRA)
                    .map(source -> source.modeledTaxableAmount().orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal limit = couple ? rules.getMichiganTaxRules().getRetirementDeductionMarried()
                    : rules.getMichiganTaxRules().getRetirementDeductionSingle();
            BigDecimal exemption = rules.getMichiganTaxRules().getPersonalExemption()
                    .multiply(BigDecimal.valueOf(couple ? 2 : 1));
            assertEquals(0, eligible.min(limit).compareTo(tax.retirementDeduction()));
            assertEquals(0, exemption.compareTo(tax.personalExemption()));
            assertEquals(0, tax.ordinaryIncome().subtract(eligible.min(limit)).subtract(exemption)
                    .max(BigDecimal.ZERO).multiply(new BigDecimal("0.0425")).compareTo(year.getMichiganIncomeTax()));
            assertTrue(tax.usedConservativeEligibility()); // pension qualification is unknown
            assertTrue(year.getSocialSecurityResult().householdBenefit().signum() > 0);
            assertTrue(year.getRequiredMinimumDistribution().signum() > 0);
            assertTrue(year.getRothConversion().signum() > 0);
            // Investment interest is a separate scalar; adding it cannot consume IRA capacity.
            var attributed = new TaxIncome(tax.retirementIncome().subtract(eligible), BigDecimal.ZERO,
                    eligible.subtract(year.getRothConversion()), year.getRothConversion(), new BigDecimal("10000"),
                    year.getIncomeSources());
            var interestTax = new MichiganTaxCalculator().calculate(attributed, filing, rules.getMichiganTaxRules(),
                    MichiganTaxContext.capture(plan.getHousehold(), LocalDate.of(year.getCalendarYear(), 7, 1), null));
            assertEquals(0, interestTax.retirementDeduction().compareTo(tax.retirementDeduction()));
            assertEquals(0, interestTax.incomeTax().subtract(tax.incomeTax()).compareTo(new BigDecimal("425.00")));
        }
    }

    @Test
    void deathYearUsesConfiguredJointReturnAndLaterSingleWithoutReassigningSources() throws Exception {
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1960, 1, 1)),
                new Person("Spouse", "Fixture", LocalDate.of(1961, 1, 1)));
        var deaths = new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2026, 67);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getMichiganTaxRules();
        var sourceAccount = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("Primary IRA",
                AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var source = IncomeSourceAttribution.withdrawal(sourceAccount, new BigDecimal("100000"),
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var income = new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(source));
        var deathYear = new MichiganTaxCalculator().calculate(income, FilingStatus.MARRIED_FILING_JOINTLY, rules,
                MichiganTaxContext.capture(household, LocalDate.of(2026, 1, 1), deaths));
        var later = new MichiganTaxCalculator().calculate(income, FilingStatus.SINGLE, rules,
                MichiganTaxContext.capture(household, LocalDate.of(2027, 1, 1), deaths));
        assertEquals(0, new BigDecimal("11800").compareTo(deathYear.personalExemption()));
        assertEquals(0, new BigDecimal("5900").compareTo(later.personalExemption()));
        assertEquals(0, later.retirementDeduction().signum());
        assertTrue(later.usedConservativeEligibility());
        assertEquals(AccountOwnership.PRIMARY, later.eligibilityDecisions().getFirst().source().owner().orElseThrow());
    }

    @Test
    void michiganAssessmentCannotMutateFederalCalculationAtFixedIncome() throws Exception {
        var income = new TaxIncome(BigDecimal.ZERO, new BigDecimal("30000"), new BigDecimal("100000"),
                new BigDecimal("20000"), new BigDecimal("10000"));
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var federal = new FederalTaxCalculator();
        var before = federal.calculate(income, FilingStatus.SINGLE, rules);
        var household = new Household(new Person("Primary", "Fixture", LocalDate.of(1960, 1, 1)));
        new MichiganTaxCalculator().calculate(income, FilingStatus.SINGLE, rules.getMichiganTaxRules(),
                MichiganTaxContext.capture(household, LocalDate.of(2026, 1, 1), null));
        var after = federal.calculate(income, FilingStatus.SINGLE, rules);
        assertEquals(before.getFederalIncomeTax(), after.getFederalIncomeTax());
        assertEquals(before.getTaxableIncome(), after.getTaxableIncome());
    }
}
