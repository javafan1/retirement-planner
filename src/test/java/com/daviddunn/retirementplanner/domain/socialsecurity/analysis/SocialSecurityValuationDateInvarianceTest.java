package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixed cash flows and mortality must not acquire strategy-specific cent-rounding noise. */
class SocialSecurityValuationDateInvarianceTest {
    @Test
    void valuationOriginAloneMustNotReverseTheSameStrategiesWithFixedMortality() {
        var strategyA = strategy(62);
        var strategyB = strategy(70);
        var mortality = new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(90, BigDecimal.ONE)));
        var conditioningDate = LocalDate.of(2025, 1, 1);
        var rate = new BigDecimal("0.053418731689453125");
        var calculator = new SocialSecurityMortalityWeightedComparisonCalculator();
        var earlier = calculator.calculate(new SocialSecurityMortalityWeightedComparisonRequest(
                strategyA, strategyB, mortality, mortality, conditioningDate,
                LocalDate.of(2025, 1, 1), rate));
        var later = calculator.calculate(new SocialSecurityMortalityWeightedComparisonRequest(
                strategyA, strategyB, mortality, mortality, conditioningDate,
                LocalDate.of(2026, 1, 1), rate));

        assertEquals(earlier.expectedNominalStrategyA(), later.expectedNominalStrategyA());
        assertEquals(earlier.expectedNominalStrategyB(), later.expectedNominalStrategyB());
        assertEquals(earlier.totalJointProbability(), later.totalJointProbability());
        assertTrue(earlier.expectedPresentValueDifference().signum() > 0);
        assertEquals(new BigDecimal("589456.08"), earlier.expectedPresentValueStrategyA().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("589456.07"), earlier.expectedPresentValueStrategyB().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("633362.96"), later.expectedPresentValueStrategyA().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("633362.95"), later.expectedPresentValueStrategyB().setScale(2, RoundingMode.HALF_UP));
        BigDecimal factor = new BigDecimal("1.02").multiply(BigDecimal.ONE.add(rate));
        assertScaled(earlier.expectedPresentValueStrategyA(), later.expectedPresentValueStrategyA(), factor);
        assertScaled(earlier.expectedPresentValueStrategyB(), later.expectedPresentValueStrategyB(), factor);
        assertEquals(earlier.expectedPresentValueDifference().signum(),
                later.expectedPresentValueDifference().signum(),
                () -> "Valuation-only change reversed the comparison: 2025 A="
                        + earlier.expectedPresentValueStrategyA() + ", B="
                        + earlier.expectedPresentValueStrategyB() + "; 2026 A="
                        + later.expectedPresentValueStrategyA() + ", B="
                        + later.expectedPresentValueStrategyB());
    }

    @ParameterizedTest
    @CsvSource({"62,70,true,0.053418731689453125", "63,69,true,0.05559856779873371124267578125",
            "67,68,false,0.01", "62,70,false,0.01", "67,67,true,0.01",
            "67,67,false,0", "62,70,true,-0.01"})
    void commonYearScalingPreservesOrderAndTies(int ageA, int ageB, boolean survivor, String discount) {
        var a = withSurvivor(strategy(ageA), survivor);
        var b = withSurvivor(strategy(ageB), survivor);
        var rate = new BigDecimal(discount);
        var scenarios = HouseholdLongevityScenarioFactory.combine(a.primaryElection().birthDate(),
                a.spouseElection().birthDate(), certain(90), certain(90));
        var calculator = new SocialSecurityMortalityWeightedStrategyCalculator();
        var a1 = calculator.calculate(a, scenarios, LocalDate.of(2025, 1, 1), rate);
        var b1 = calculator.calculate(b, scenarios, LocalDate.of(2025, 1, 1), rate);
        var a2 = calculator.calculate(a, scenarios, LocalDate.of(2026, 1, 1), rate);
        var b2 = calculator.calculate(b, scenarios, LocalDate.of(2026, 1, 1), rate);
        var factor = new BigDecimal("1.02").multiply(BigDecimal.ONE.add(rate));
        assertScaled(a1.expectedPresentValue(), a2.expectedPresentValue(), factor);
        assertScaled(b1.expectedPresentValue(), b2.expectedPresentValue(), factor);
        assertEquals(a1.expectedPresentValue().compareTo(b1.expectedPresentValue()),
                a2.expectedPresentValue().compareTo(b2.expectedPresentValue()));
        if (ageA == ageB) {
            assertEquals(a1.expectedPresentValue(), b1.expectedPresentValue());
            assertEquals(a2.expectedPresentValue(), b2.expectedPresentValue());
        } else {
            assertTrue(a1.expectedPresentValue().compareTo(b1.expectedPresentValue()) != 0);
        }
        assertEquals(a1.expectedNominalBenefits(), a2.expectedNominalBenefits());
        assertEquals(2, a1.expectedNominalBenefits().scale());
        assertTrue(a1.expectedPresentValue().scale() > 2);
    }

    @Test
    void monthAndYearOriginShiftHasTheSameDecimalFactorForDifferentStrategies() {
        var rate = new BigDecimal("1.01").pow(12).subtract(BigDecimal.ONE);
        var factor = new BigDecimal("1.02").multiply(new BigDecimal("1.01").pow(16));
        var calculator = new SocialSecurityStrategyValuationCalculator();
        for (int age : List.of(62, 63, 67, 69, 70)) {
            var before = calculator.calculateSummary(strategy(age), LocalDate.of(2025, 3, 1), rate);
            var after = calculator.calculateSummary(strategy(age), LocalDate.of(2026, 7, 1), rate);
            assertScaled(before.presentValue(), after.presentValue(), factor);
        }
    }

    private static void assertScaled(BigDecimal before, BigDecimal after, BigDecimal factor) {
        // Each monthly operation has 34 significant digits. 1E-30 relative allows
        // guard-digit propagation across a long horizon, far below a currency cent.
        var expected = before.multiply(factor);
        var error = after.subtract(expected).abs();
        assertTrue(error.compareTo(expected.abs().max(BigDecimal.ONE).scaleByPowerOfTen(-30)) <= 0,
                () -> "Common scaling residual: " + error);
    }

    private static SocialSecurityMortalityDistribution certain(int age) {
        return new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(age, BigDecimal.ONE)));
    }

    private static SocialSecurityStrategyRequest withSurvivor(SocialSecurityStrategyRequest base, boolean enabled) {
        return new SocialSecurityStrategyRequest(base.analysisDate(), base.analysisEndDate(),
                base.primaryElection(), base.spouseElection(),
                enabled ? base.primarySurvivorClaimDate() : null,
                enabled ? base.spouseSurvivorClaimDate() : null,
                base.primaryDeathDate(), base.spouseDeathDate(), base.socialSecurityColaRate());
    }

    private static SocialSecurityStrategyRequest strategy(int primaryClaimAge) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                new SocialSecurityClaimingElection(
                        AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"),
                        2025,
                        LocalDate.of(1963 + primaryClaimAge, 6, 4)),
                new SocialSecurityClaimingElection(
                        AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 2, 28),
                        new BigDecimal("2000"),
                        2025,
                        LocalDate.of(2032, 2, 28)),
                LocalDate.of(2030, 6, 4),
                LocalDate.of(2032, 2, 28),
                LocalDate.of(2053, 6, 4),
                LocalDate.of(2055, 2, 28),
                new BigDecimal("0.02"));
    }
}
