package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdSocialSecurityIncomeCalculatorTest {

    private final HouseholdSocialSecurityIncomeCalculator calculator =
            new HouseholdSocialSecurityIncomeCalculator();

    @Test
    void householdWithoutSocialSecurityReturnsZeroAuditResult() {

        Household household = new Household(
                new Person("David", "Dunn", LocalDate.of(1963, 6, 4)),
                new Person("Lisa", "Dunn", LocalDate.of(1965, 2, 28)));

        assertEquals(
                HouseholdSocialSecurityResult.zero(),
                calculator.calculate(
                        household,
                        LocalDate.of(2035, 12, 31),
                        new DeathScenarioAssumptions(
                                DeathScenario.BOTH_SURVIVE,
                                null),
                        BigDecimal.ZERO));
    }

    @Test
    void bothSurviveIncludesBothActiveBenefits() {

        Household household = household(5000, 3000, 67, 67, 2030, 2032);

        assertEquals(new BigDecimal("96000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null),
                BigDecimal.ZERO));
    }

    @Test
    void bothSurviveRetainsOwnerBenefitsAndReconcilesHouseholdTotal() {

        Household household = household(4000, 3000, 67, 67, 2030, 2032);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("48000.00"), result.primaryOwnBenefit());
        assertEquals(new BigDecimal("36000.00"), result.spouseOwnBenefit());
        assertEquals(BigDecimal.ZERO, result.primarySurvivorCandidate());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.spouseSurvivorCandidate()));
        assertEquals(new BigDecimal("84000.00"), result.householdBenefit());
        assertEquals(
                result.primaryOwnBenefit().add(result.spouseOwnBenefit()),
                result.householdBenefit());
    }

    @Test
    void onlyStartedOwnBenefitAppearsInAuditResult() {

        Household household = household(4000, 3000, 67, 67, 2030, 2040);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.BOTH_SURVIVE,
                        null),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("48000.00"), result.primaryOwnBenefit());
        assertEquals(BigDecimal.ZERO, result.spouseOwnBenefit());
        assertEquals(new BigDecimal("48000.00"), result.householdBenefit());
    }

    @Test
    void primaryDeathUsesHigherSurvivorBenefitWithoutDoubleCounting() {

        Household household = household(5000, 3000, 67, 67, 2030, 2032);

        assertEquals(new BigDecimal("60000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2035, 67),
                BigDecimal.ZERO));
    }

    @Test
    void deathScenarioRetainsCandidatesAndSelectedSurvivorBenefit() {

        Household household = household(5000, 3000, 67, 67, 2030, 2032);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67),
                BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, result.primaryOwnBenefit());
        assertEquals(new BigDecimal("36000.00"), result.spouseOwnBenefit());
        assertEquals(new BigDecimal("60000.00"), result.spouseSurvivorCandidate());
        assertEquals(SocialSecurityBenefitSelection.SURVIVOR,
                result.spouseSelection());
        assertEquals(new BigDecimal("60000.00"), result.householdBenefit());
    }

    @Test
    void deathScenarioRetainsCandidatesAndSelectedOwnBenefit() {

        Household household = household(3000, 5000, 67, 67, 2030, 2032);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("60000.00"), result.spouseOwnBenefit());
        assertEquals(new BigDecimal("36000.00"), result.spouseSurvivorCandidate());
        assertEquals(SocialSecurityBenefitSelection.OWN,
                result.spouseSelection());
        assertEquals(new BigDecimal("60000.00"), result.householdBenefit());
    }

    @Test
    void survivingOwnBenefitWinsWhenItIsHigher() {

        Household household = household(3000, 5000, 67, 67, 2030, 2032);

        assertEquals(new BigDecimal("60000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2035, 67),
                BigDecimal.ZERO));
    }

    @Test
    void unclaimedOwnBenefitDoesNotReduceSurvivorBenefit() {

        Household household = household(5000, 3000, 67, 67, 2030, 2040);

        assertEquals(new BigDecimal("60000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2035, 67),
                BigDecimal.ZERO));
    }

    @Test
    void auditShowsSurvivorWhenOwnBenefitHasNotStarted() {

        Household household = household(5000, 3000, 67, 67, 2030, 2040);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2035, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2035,
                        67),
                BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, result.spouseOwnBenefit());
        assertEquals(new BigDecimal("60000.00"), result.spouseSurvivorCandidate());
        assertEquals(SocialSecurityBenefitSelection.SURVIVOR,
                result.spouseSelection());
        assertEquals(new BigDecimal("60000.00"), result.householdBenefit());
    }

    @Test
    void deathBeforePlannedClaimUsesFullRetirementBenefitAtDeath() {

        Household household = household(5000, 3000, 70, 67, 2033, 2032);

        assertEquals(new BigDecimal("60000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2032, 12, 31),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2032, 67),
                BigDecimal.ZERO));
    }

    @Test
    void survivorClaimingAgeEarlierThanOwnStartPaysSurvivorThenHigherOwnBenefit() {

        Household household = household(2000, 3000, 67, 67, 2028, 2032);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62);

        assertEquals(new BigDecimal("19114.32"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2030, 12, 31), death, BigDecimal.ZERO));
        assertEquals(new BigDecimal("19114.32"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2031, 12, 31), death, BigDecimal.ZERO));
        assertEquals(new BigDecimal("36000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2032, 12, 31), death, BigDecimal.ZERO));
    }

    @Test
    void laterSurvivorClaimingAgeDoesNotSuspendAlreadyStartedOwnBenefit() {

        Household household = household(5000, 3000, 67, 62, 2028, 2027);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        67);

        assertEquals(new BigDecimal("25200.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2030, 12, 31), death, BigDecimal.ZERO));
        assertEquals(new BigDecimal("60000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2032, 12, 31), death, BigDecimal.ZERO));
    }

    @Test
    void auditShowsZeroSurvivorCandidateBeforeEligibility() {

        Household household = household(5000, 3000, 67, 62, 2028, 2027);

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2030, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        67),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("25200.00"), result.spouseOwnBenefit());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.spouseSurvivorCandidate()));
        assertEquals(SocialSecurityBenefitSelection.OWN,
                result.spouseSelection());
        assertEquals(new BigDecimal("25200.00"), result.householdBenefit());
    }

    @Test
    void deathYearIsAnAnnualSwitchWithoutDeathMonthProration() {

        Household household = household(5000, 3000, 67, 67, 2028, 2032);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        65);

        assertEquals(new BigDecimal("55114.32"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2030, 12, 31), death, BigDecimal.ZERO));
    }

    @Test
    void unclaimedDeceasedBenefitBasisDoesNotChangeAtHypotheticalStartDate() {

        Household household = household(3000, 0, 70, 67, 2035, 2040);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62);

        BigDecimal expected = new BigDecimal("28671.48");

        for (int year : new int[]{2030, 2031, 2034, 2035, 2036}) {
            assertEquals(expected, calculator.calculateAnnualIncome(
                    household, LocalDate.of(year, 12, 31), death, BigDecimal.ZERO));
        }
    }

    @Test
    void frozenUnclaimedBasisStillReceivesPostDeathCola() {

        Household household = household(3000, 0, 70, 67, 2035, 2040);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62);
        BigDecimal cola = new BigDecimal("0.03");

        BigDecimal deathYear = calculator.calculateAnnualIncome(
                household, LocalDate.of(2030, 12, 31), death, cola);
        BigDecimal laterYear = calculator.calculateAnnualIncome(
                household, LocalDate.of(2036, 12, 31), death, cola);

        assertEquals(new BigDecimal("28671.48"), deathYear);
        assertEquals(new BigDecimal("34235.16"), laterYear);
    }

    @Test
    void claimedDeceasedBenefitBasisRemainsClaimingAgeAdjustedAfterDeath() {

        Household household = household(3000, 0, 62, 67, 2025, 2040);
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62);

        BigDecimal expected = new BigDecimal("20070.00");

        assertEquals(expected, calculator.calculateAnnualIncome(
                household, LocalDate.of(2030, 12, 31), death, BigDecimal.ZERO));
        assertEquals(expected, calculator.calculateAnnualIncome(
                household, LocalDate.of(2036, 12, 31), death, BigDecimal.ZERO));
    }

    @Test
    void postDeathOwnBenefitUsesStartMonthProration() {

        assertEquals(new BigDecimal("36000.00"),
                postDeathOwnIncomeForStartDate(LocalDate.of(2032, 1, 1)));
        assertEquals(new BigDecimal("21000.00"),
                postDeathOwnIncomeForStartDate(LocalDate.of(2032, 6, 1)));
        assertEquals(new BigDecimal("3000.00"),
                postDeathOwnIncomeForStartDate(LocalDate.of(2032, 12, 1)));
    }

    @Test
    void postDeathAuditRetainsProratedOwnCandidate() {

        Household household = household(
                0,
                3000,
                67,
                67,
                LocalDate.of(2028, 1, 1),
                LocalDate.of(2032, 6, 1));

        HouseholdSocialSecurityResult result = calculator.calculate(
                household,
                LocalDate.of(2032, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("21000.00"), result.spouseOwnBenefit());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.spouseSurvivorCandidate()));
        assertEquals(new BigDecimal("21000.00"), result.householdBenefit());
    }

    @Test
    void survivorThenProratedOwnThenFullOwnUsesHigherAnnualCandidate() {

        Household household = household(
                2000,
                3000,
                67,
                67,
                LocalDate.of(2028, 1, 1),
                LocalDate.of(2032, 6, 1));
        DeathScenarioAssumptions death =
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62);

        assertEquals(new BigDecimal("19114.32"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2031, 12, 31), death, BigDecimal.ZERO));
        assertEquals(new BigDecimal("21000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2032, 12, 31), death, BigDecimal.ZERO));
        assertEquals(new BigDecimal("36000.00"), calculator.calculateAnnualIncome(
                household, LocalDate.of(2033, 12, 31), death, BigDecimal.ZERO));
    }

    private BigDecimal postDeathOwnIncomeForStartDate(
            LocalDate ownStartDate) {

        Household household = household(
                0,
                3000,
                67,
                67,
                LocalDate.of(2028, 1, 1),
                ownStartDate);

        return calculator.calculateAnnualIncome(
                household,
                LocalDate.of(2032, 12, 31),
                new DeathScenarioAssumptions(
                        DeathScenario.PRIMARY_DIES,
                        2030,
                        62),
                BigDecimal.ZERO);
    }

    private Household household(
            int primaryBenefit,
            int spouseBenefit,
            int primaryClaimingAge,
            int spouseClaimingAge,
            int primaryStartYear,
            int spouseStartYear) {

        return household(
                primaryBenefit,
                spouseBenefit,
                primaryClaimingAge,
                spouseClaimingAge,
                LocalDate.of(primaryStartYear, 1, 1),
                LocalDate.of(spouseStartYear, 1, 1));
    }

    private Household household(
            int primaryBenefit,
            int spouseBenefit,
            int primaryClaimingAge,
            int spouseClaimingAge,
            LocalDate primaryStartDate,
            LocalDate spouseStartDate) {

        Person primary = new Person("David", "Dunn", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Lisa", "Dunn", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(source("David", AccountOwnership.PRIMARY,
                primaryBenefit, primaryClaimingAge, primaryStartDate));
        spouse.addIncomeSource(source("Lisa", AccountOwnership.SPOUSE,
                spouseBenefit, spouseClaimingAge, spouseStartDate));
        return new Household(primary, spouse);
    }

    private SocialSecurityIncome source(
            String name, AccountOwnership ownership, int benefit,
            int claimingAge, int startYear) {

        return source(
                name,
                ownership,
                benefit,
                claimingAge,
                LocalDate.of(startYear, 1, 1));
    }

    private SocialSecurityIncome source(
            String name, AccountOwnership ownership, int benefit,
            int claimingAge, LocalDate startDate) {

        return new SocialSecurityIncome(name, ownership,
                startDate, null,
                BigDecimal.valueOf(benefit), claimingAge,
                BigDecimal.ZERO, 2030);
    }
}
