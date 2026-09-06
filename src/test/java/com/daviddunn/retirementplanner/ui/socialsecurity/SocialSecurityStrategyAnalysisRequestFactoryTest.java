package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityCategory;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityStrategyAnalysisRequestFactoryTest {

    @Test
    void mapsPlanInputsWithoutMutatingPlanOrIncomeSources() {
        RetirementPlan plan = plan(true);
        Person primary = plan.getHousehold().getPrimaryPerson();
        SocialSecurityIncome primarySource = (SocialSecurityIncome)
                primary.getIncomeSources().getFirst();
        LocalDate originalStart = primarySource.getStartDate();
        BigDecimal originalBenefit = primarySource.getFullRetirementMonthlyBenefit();

        SocialSecurityStrategyAnalysisContext context =
                new SocialSecurityStrategyAnalysisRequestFactory().create(
                        plan,
                        SocialSecurityMortalityCategory.MALE,
                        SocialSecurityMortalityCategory.FEMALE,
                        new BigDecimal("0.01"),
                        LocalDate.of(2026, 7, 1));

        var base = context.request().retirementGridRequest().baseStrategy();
        assertEquals(primary.getBirthDate(), base.primaryElection().birthDate());
        assertEquals(originalBenefit,
                base.primaryElection().fullRetirementMonthlyBenefit());
        assertEquals(primarySource.getBenefitValuationYear(),
                base.primaryElection().benefitValuationYear());
        assertEquals(plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                base.socialSecurityColaRate());
        assertEquals(LocalDate.of(2026, 7, 1),
                context.request().retirementGridRequest().presentValueBaseDate());
        assertEquals(SocialSecurityMortalityCategory.MALE,
                context.primaryMortalityCategory());
        assertEquals(originalStart, primarySource.getStartDate());
        assertEquals(originalBenefit, primarySource.getFullRetirementMonthlyBenefit());
        assertEquals(1, primary.getIncomeSources().size());
    }

    @Test
    void missingSourceAndMortalityCategoryHaveActionableMessages() {
        IllegalArgumentException missingSource = assertThrows(
                IllegalArgumentException.class,
                () -> new SocialSecurityStrategyAnalysisRequestFactory().create(
                        plan(false),
                        SocialSecurityMortalityCategory.MALE,
                        SocialSecurityMortalityCategory.FEMALE,
                        new BigDecimal("0.01"),
                        LocalDate.of(2026, 7, 1)));
        assertEquals("Spouse Social Security source is required.",
                missingSource.getMessage());

        IllegalArgumentException missingCategory = assertThrows(
                IllegalArgumentException.class,
                () -> new SocialSecurityStrategyAnalysisRequestFactory().create(
                        plan(true), null,
                        SocialSecurityMortalityCategory.FEMALE,
                        new BigDecimal("0.01"),
                        LocalDate.of(2026, 7, 1)));
        assertEquals("Primary mortality category is required.",
                missingCategory.getMessage());
    }

    @Test
    void mapsIndependentAdjustmentsAndDefaultRemainsStandard() {
        SocialSecurityStrategyAnalysisRequestFactory factory =
                new SocialSecurityStrategyAnalysisRequestFactory();
        SocialSecurityStrategyAnalysisContext adjusted = factory.create(
                plan(true), SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.of(new BigDecimal("0.80")),
                SocialSecurityMortalityAdjustment.of(new BigDecimal("1.50")),
                new BigDecimal("0.01"), LocalDate.of(2026, 7, 1));
        SocialSecurityStrategyAnalysisContext standard = factory.create(
                plan(true), SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityCategory.FEMALE,
                new BigDecimal("0.01"), LocalDate.of(2026, 7, 1));

        assertEquals(new BigDecimal("0.80"),
                adjusted.primaryMortalityAdjustment().factor());
        assertEquals(new BigDecimal("1.50"),
                adjusted.spouseMortalityAdjustment().factor());
        assertEquals(0, BigDecimal.ONE.compareTo(
                standard.primaryMortalityAdjustment().factor()));
        assertEquals(0, BigDecimal.ONE.compareTo(
                standard.spouseMortalityAdjustment().factor()));
    }

    private RetirementPlan plan(boolean includeSpouseSource) {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(source(
                AccountOwnership.PRIMARY, LocalDate.of(2030, 6, 4), "3000"));
        if (includeSpouseSource) {
            spouse.addIncomeSource(source(
                    AccountOwnership.SPOUSE, LocalDate.of(2027, 2, 28), "1200"));
        }
        return new RetirementPlan(
                new Household(primary, spouse),
                new AccountPortfolio(),
                new PlanningAssumptions(
                        new BigDecimal("0.07"),
                        new BigDecimal("0.025"),
                        40,
                        LocalDate.of(2026, 7, 1)));
    }

    private SocialSecurityIncome source(
            AccountOwnership ownership,
            LocalDate startDate,
            String benefit) {
        return new SocialSecurityIncome(
                "Social Security",
                ownership,
                startDate,
                null,
                new BigDecimal(benefit),
                ownership == AccountOwnership.PRIMARY ? 67 : 62,
                BigDecimal.ZERO,
                2025);
    }
}
