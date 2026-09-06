package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SocialSecurityColaSourceOfTruthTest {

    @Test
    void changingOnlyLegacyColaDoesNotChangeAnyProjectionResult() {

        Projection zeroLegacy = new ProjectionEngine().project(
                plan(
                        BigDecimal.ZERO,
                        new BigDecimal("0.025"),
                        new DeathScenarioAssumptions(
                                DeathScenario.BOTH_SURVIVE,
                                null)));

        Projection absurdLegacy = new ProjectionEngine().project(
                plan(
                        new BigDecimal("0.20"),
                        new BigDecimal("0.025"),
                        new DeathScenarioAssumptions(
                                DeathScenario.BOTH_SURVIVE,
                                null)));

        assertEquals(zeroLegacy.size(), absurdLegacy.size());

        for (int index = 0; index < zeroLegacy.size(); index++) {
            assertFinanciallyEqual(
                    zeroLegacy.getYearAt(index),
                    absurdLegacy.getYearAt(index));
        }
    }

    @Test
    void changingEconomicColaChangesFutureHouseholdSocialSecurity() {

        Projection zeroEconomic = new ProjectionEngine().project(
                plan(
                        new BigDecimal("0.20"),
                        BigDecimal.ZERO,
                        new DeathScenarioAssumptions(
                                DeathScenario.BOTH_SURVIVE,
                                null)));

        Projection threePercentEconomic = new ProjectionEngine().project(
                plan(
                        new BigDecimal("0.20"),
                        new BigDecimal("0.03"),
                        new DeathScenarioAssumptions(
                                DeathScenario.BOTH_SURVIVE,
                                null)));

        ProjectionYear zeroYear = zeroEconomic.getLastYear();
        ProjectionYear colaYear = threePercentEconomic.getLastYear();

        assertNotEquals(
                0,
                zeroYear.getSocialSecurityResult()
                        .primaryOwnBenefit()
                        .compareTo(colaYear.getSocialSecurityResult()
                                .primaryOwnBenefit()));

        assertNotEquals(
                0,
                zeroYear.getSocialSecurityResult()
                        .householdBenefit()
                        .compareTo(colaYear.getSocialSecurityResult()
                                .householdBenefit()));
    }

    @Test
    void survivorProjectionIgnoresLegacyColaAndUsesEconomicCola() {

        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2031,
                        62);

        Projection zeroLegacy = new ProjectionEngine().project(
                plan(BigDecimal.ZERO, new BigDecimal("0.03"), death));
        Projection absurdLegacy = new ProjectionEngine().project(
                plan(new BigDecimal("0.20"), new BigDecimal("0.03"), death));
        Projection zeroEconomic = new ProjectionEngine().project(
                plan(new BigDecimal("0.20"), BigDecimal.ZERO, death));

        ProjectionYear economicYear = zeroLegacy.getLastYear();
        ProjectionYear sameEconomicYear = absurdLegacy.getLastYear();
        ProjectionYear noColaYear = zeroEconomic.getLastYear();

        assertMoney(
                economicYear.getSocialSecurityResult()
                        .spouseSurvivorCandidate(),
                sameEconomicYear.getSocialSecurityResult()
                        .spouseSurvivorCandidate());

        assertNotEquals(
                0,
                economicYear.getSocialSecurityResult()
                        .spouseSurvivorCandidate()
                        .compareTo(noColaYear.getSocialSecurityResult()
                                .spouseSurvivorCandidate()));
    }

    private RetirementPlan plan(
            BigDecimal legacyCola,
            BigDecimal economicCola,
            DeathScenarioAssumptions deathAssumptions) {

        Person primary = new Person(
                "Primary",
                "Planner",
                LocalDate.of(1963, 6, 4));
        Person spouse = new Person(
                "Spouse",
                "Planner",
                LocalDate.of(1965, 2, 28));

        primary.addIncomeSource(new SocialSecurityIncome(
                "Primary Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4),
                null,
                new BigDecimal("3000"),
                67,
                legacyCola,
                2030));

        spouse.addIncomeSource(new SocialSecurityIncome(
                "Spouse Social Security",
                AccountOwnership.SPOUSE,
                LocalDate.of(2030, 2, 28),
                null,
                new BigDecimal("2000"),
                65,
                legacyCola,
                2030));

        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        economicCola),
                new TaxAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                new WithdrawalAssumptions(
                        WithdrawalStrategyType.TAXABLE_FIRST),
                deathAssumptions,
                4,
                LocalDate.of(2030, 1, 1));

        return new RetirementPlan(
                new Household(primary, spouse),
                new AccountPortfolio(),
                assumptions);
    }

    private void assertFinanciallyEqual(
            ProjectionYear expected,
            ProjectionYear actual) {

        assertMoney(expected.getSocialSecurityResult().primaryOwnBenefit(),
                actual.getSocialSecurityResult().primaryOwnBenefit());
        assertMoney(expected.getSocialSecurityResult().spouseOwnBenefit(),
                actual.getSocialSecurityResult().spouseOwnBenefit());
        assertMoney(expected.getSocialSecurityResult().primarySurvivorCandidate(),
                actual.getSocialSecurityResult().primarySurvivorCandidate());
        assertMoney(expected.getSocialSecurityResult().spouseSurvivorCandidate(),
                actual.getSocialSecurityResult().spouseSurvivorCandidate());
        assertMoney(expected.getSocialSecurityResult().householdBenefit(),
                actual.getSocialSecurityResult().householdBenefit());
        assertMoney(expected.getGuaranteedIncome(), actual.getGuaranteedIncome());
        assertMoney(expected.getTaxableSocialSecurity(), actual.getTaxableSocialSecurity());
        assertMoney(expected.getFederalIncomeTax(), actual.getFederalIncomeTax());
        assertMoney(expected.getMichiganIncomeTax(), actual.getMichiganIncomeTax());
        assertMoney(expected.getTotalIncomeTax(), actual.getTotalIncomeTax());
        assertMoney(expected.getRothConversion(), actual.getRothConversion());
        assertMoney(expected.getPortfolioWithdrawal(), actual.getPortfolioWithdrawal());
        assertMoney(expected.getEndingInvestableAssets(), actual.getEndingInvestableAssets());
        assertMoney(expected.getAfterTaxEstateValue(), actual.getAfterTaxEstateValue());
    }

    private void assertMoney(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(0, expected.compareTo(actual));
    }
}
