package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecuritySurvivorBenefitCalculatorTest {

    @Test
    void age60ProducesSeventyOnePointFivePercent() {

        assertDecimal(new BigDecimal("0.715000000000"),
                SocialSecuritySurvivorBenefitCalculator
                        .calculateReductionFactor(
                                LocalDate.of(1962, 1, 2),
                                YearMonth.of(2022, 1)));
    }

    @Test
    void exactSurvivorFraAndLaterAreCappedAtOneHundredPercent() {

        assertDecimal(BigDecimal.ONE,
                SocialSecuritySurvivorBenefitCalculator
                        .calculateReductionFactor(
                                LocalDate.of(1962, 1, 2),
                                YearMonth.of(2029, 1)));
        assertDecimal(BigDecimal.ONE,
                SocialSecuritySurvivorBenefitCalculator
                        .calculateReductionFactor(
                                LocalDate.of(1962, 1, 2),
                                YearMonth.of(2032, 1)));
    }

    @Test
    void survivorFraSupportsMonthComponent() {

        assertEquals(LocalDate.of(2026, 9, 15),
                SocialSecuritySurvivorBenefitCalculator
                        .calculateSurvivorFullRetirementDate(
                                LocalDate.of(1960, 1, 15)));
    }

    @Test
    void januaryFirstBirthUsesPriorYearConvention() {

        assertEquals(LocalDate.of(2028, 10, 31),
                SocialSecuritySurvivorBenefitCalculator
                        .calculateSurvivorFullRetirementDate(
                                LocalDate.of(1962, 1, 1)));
    }

    @Test
    void survivorClaimBeforeAge60IsRejected() {

        assertThrows(IllegalArgumentException.class,
                () -> SocialSecuritySurvivorBenefitCalculator
                        .calculateReductionFactor(
                                LocalDate.of(1962, 1, 2),
                                YearMonth.of(2021, 12)));
    }

    @Test
    void workerClaimedAtFraUsesFraBasis() {

        SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis =
                basis(
                        LocalDate.of(1960, 1, 2),
                        LocalDate.of(2027, 1, 2),
                        YearMonth.of(2030, 1));

        assertMoney(new BigDecimal("3000.00"), basis.unreducedBasis());
        assertMoney(new BigDecimal("3000.00"),
                basis.maximumPayableBenefit());
    }

    @Test
    void workerDelayedTo70PreservesDelayedCredits() {

        SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis =
                basis(
                        LocalDate.of(1960, 1, 2),
                        LocalDate.of(2030, 1, 2),
                        YearMonth.of(2031, 1));

        assertMoney(new BigDecimal("3720.00"), basis.unreducedBasis());
    }

    @Test
    void workerDyingAt68BeforeFilingAccruesCreditsOnlyThroughDeath() {

        SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis =
                basis(
                        LocalDate.of(1960, 1, 2),
                        LocalDate.of(2030, 1, 2),
                        YearMonth.of(2028, 1));

        assertMoney(new BigDecimal("3240.00"), basis.unreducedBasis());
    }

    @Test
    void accruedCreditsUseActualFraToAge70Window() {

        SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis =
                basis(
                        LocalDate.of(1954, 1, 2),
                        LocalDate.of(2024, 1, 2),
                        YearMonth.of(2025, 1));

        assertMoney(new BigDecimal("3960.00"), basis.unreducedBasis());
    }

    @Test
    void earlyWorkerClaimAppliesWidowerLimitWithoutReducingInitialBasis() {

        SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis =
                basis(
                        LocalDate.of(1960, 1, 2),
                        LocalDate.of(2022, 1, 2),
                        YearMonth.of(2030, 1));

        assertMoney(new BigDecimal("3000.00"), basis.unreducedBasis());
        assertMoney(new BigDecimal("2475.00"),
                basis.maximumPayableBenefit());

        SocialSecuritySurvivorBenefitCalculator.Calculation atFra =
                SocialSecuritySurvivorBenefitCalculator.calculate(
                        basis,
                        LocalDate.of(1962, 1, 2),
                        YearMonth.of(2029, 1));
        assertMoney(new BigDecimal("2475.00"), atFra.survivorBenefit());
    }

    private SocialSecuritySurvivorBenefitCalculator.WorkerBasis basis(
            LocalDate birthDate,
            LocalDate claimDate,
            YearMonth deathMonth) {

        return SocialSecuritySurvivorBenefitCalculator
                .calculateDeceasedWorkerBasis(
                        new BigDecimal("3000"),
                        birthDate,
                        claimDate,
                        deathMonth);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
