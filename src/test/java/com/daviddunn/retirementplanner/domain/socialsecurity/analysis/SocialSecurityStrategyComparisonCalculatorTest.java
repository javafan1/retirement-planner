package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityStrategyComparisonCalculatorTest {

    private final SocialSecurityStrategyComparisonCalculator calculator =
            new SocialSecurityStrategyComparisonCalculator();

    @Test
    void identicalStrategiesTieUnderEveryMeasure() {
        SocialSecurityStrategyRequest strategy = singlePrimaryStrategy(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2035, 12, 31),
                BigDecimal.ZERO);

        SocialSecurityStrategyComparisonResult result = compare(
                strategy,
                strategy,
                BigDecimal.ZERO);

        assertMoney(BigDecimal.ZERO, result.nominalDifference());
        assertMoney(BigDecimal.ZERO, result.realDifference());
        assertMoney(BigDecimal.ZERO, result.presentValueDifference());
        assertEquals(StrategyComparisonWinner.TIE, result.nominalWinner());
        assertEquals(StrategyComparisonWinner.TIE, result.realWinner());
        assertEquals(StrategyComparisonWinner.TIE,
                result.presentValueWinner());
        assertEquals(SocialSecurityBreakEvenStatus.IDENTICAL,
                result.nominalBreakEven().status());
    }

    @Test
    void simpleEarlyVersusFraClaimHasExactMonthlyCrossover() {
        SocialSecurityStrategyRequest early = singlePrimaryStrategy(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2042, 12, 31),
                BigDecimal.ZERO);
        SocialSecurityStrategyRequest fra = singlePrimaryStrategy(
                LocalDate.of(2030, 1, 2),
                LocalDate.of(2042, 12, 31),
                BigDecimal.ZERO);

        SocialSecurityStrategyComparisonResult result = compare(
                early,
                fra,
                BigDecimal.ZERO);

        assertEquals(SocialSecurityBreakEvenStatus.CROSSOVER,
                result.nominalBreakEven().status());
        assertEquals(YearMonth.of(2041, 8),
                result.nominalBreakEven().firstCrossover().crossoverMonth());
        assertEquals(StrategyComparisonWinner.STRATEGY_A,
                result.nominalBreakEven().initiallyAhead());
        assertEquals(StrategyComparisonWinner.STRATEGY_B,
                result.nominalBreakEven().aheadAtEnd());
    }

    @Test
    void finiteHorizonCanEndWithoutCrossover() {
        SocialSecurityStrategyComparisonResult result = compare(
                singlePrimaryStrategy(
                        LocalDate.of(2025, 1, 2),
                        LocalDate.of(2035, 12, 31),
                        BigDecimal.ZERO),
                singlePrimaryStrategy(
                        LocalDate.of(2030, 1, 2),
                        LocalDate.of(2035, 12, 31),
                        BigDecimal.ZERO),
                BigDecimal.ZERO);

        assertEquals(SocialSecurityBreakEvenStatus.ALWAYS_AHEAD_A,
                result.nominalBreakEven().status());
        assertEquals(StrategyComparisonWinner.STRATEGY_A,
                result.nominalWinner());
    }

    @Test
    void zeroDiscountMakesPresentValueEqualRealLifetimeBenefits() {
        SocialSecurityStrategyComparisonResult result = compare(
                singlePrimaryStrategy(
                        LocalDate.of(2025, 1, 2),
                        LocalDate.of(2035, 12, 31),
                        new BigDecimal("0.03")),
                singlePrimaryStrategy(
                        LocalDate.of(2030, 1, 2),
                        LocalDate.of(2035, 12, 31),
                        new BigDecimal("0.03")),
                BigDecimal.ZERO);

        assertMoney(result.strategyA().realLifetimeBenefits(),
                result.strategyA().presentValue());
        assertMoney(result.strategyB().realLifetimeBenefits(),
                result.strategyB().presentValue());
    }

    @Test
    void positiveDiscountReducesFuturePresentValueAndReconciles() {
        SocialSecurityStrategyComparisonResult result = compare(
                singlePrimaryStrategy(
                        LocalDate.of(2025, 1, 2),
                        LocalDate.of(2060, 12, 31),
                        new BigDecimal("0.03")),
                singlePrimaryStrategy(
                        LocalDate.of(2030, 1, 2),
                        LocalDate.of(2060, 12, 31),
                        new BigDecimal("0.03")),
                new BigDecimal("0.015"));

        assertTrue(result.strategyA().presentValue().compareTo(
                result.strategyA().realLifetimeBenefits()) < 0);
        SocialSecurityMonthlyComparison last =
                result.monthlyComparisons().getLast();
        assertMoney(result.strategyA().nominalLifetimeBenefits(),
                last.cumulativeNominalA());
        assertMoney(result.strategyA().realLifetimeBenefits(),
                last.cumulativeRealA());
        assertMoney(result.strategyA().presentValue(),
                last.cumulativePresentValueA());
        assertMoney(result.nominalDifference(),
                last.cumulativeNominalDifference());
        assertMoney(result.realDifference(),
                last.cumulativeRealDifference());
        assertMoney(result.presentValueDifference(),
                last.cumulativePresentValueDifference());
    }

    @Test
    void earlyDeathBeforeDelayedClaimUsesExistingSurvivorTimeline() {
        LocalDate primaryDeath = LocalDate.of(2028, 1, 15);
        LocalDate spouseDeath = LocalDate.of(2050, 1, 15);
        SocialSecurityStrategyRequest delayed = marriedStrategy(
                LocalDate.of(2033, 1, 2),
                LocalDate.of(2032, 1, 2),
                LocalDate.of(2025, 1, 2),
                primaryDeath,
                spouseDeath);
        SocialSecurityStrategyRequest early = marriedStrategy(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2032, 1, 2),
                LocalDate.of(2025, 1, 2),
                primaryDeath,
                spouseDeath);

        SocialSecurityStrategyComparisonResult result = compare(
                delayed,
                early,
                new BigDecimal("0.01"));

        SocialSecurityMonthlyComparison afterDeath = result
                .monthlyComparisons().stream()
                .filter(value -> value.month().equals(YearMonth.of(2035, 1)))
                .findFirst()
                .orElseThrow();
        assertTrue(afterDeath.strategyAMonth().spouseSurvivorBenefit()
                .signum() > 0);
        assertTrue(afterDeath.strategyBMonth().spouseSurvivorBenefit()
                .signum() > 0);
        assertTrue(result.strategyA().presentValue().signum() > 0);
    }

    @Test
    void comparisonRetainsIndependentImmutableStrategyResults() {
        SocialSecurityStrategyRequest strategyA = singlePrimaryStrategy(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2035, 12, 31),
                BigDecimal.ZERO);
        SocialSecurityStrategyRequest strategyB = singlePrimaryStrategy(
                LocalDate.of(2030, 1, 2),
                LocalDate.of(2035, 12, 31),
                BigDecimal.ZERO);

        SocialSecurityStrategyComparisonResult result = compare(
                strategyA,
                strategyB,
                BigDecimal.ZERO);

        assertEquals(strategyA, result.strategyA().strategyResult().request());
        assertEquals(strategyB, result.strategyB().strategyResult().request());
        assertNotSame(result.strategyA().strategyResult(),
                result.strategyB().strategyResult());
        assertThrows(UnsupportedOperationException.class,
                () -> result.monthlyComparisons().clear());
    }

    @Test
    void incompatibleHouseholdsAndHorizonsAreRejected() {
        SocialSecurityStrategyRequest standard = singlePrimaryStrategy(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2035, 12, 31),
                BigDecimal.ZERO);
        SocialSecurityStrategyRequest otherHorizon = singlePrimaryStrategy(
                LocalDate.of(2030, 1, 2),
                LocalDate.of(2036, 12, 31),
                BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityStrategyComparisonRequest(
                        standard,
                        otherHorizon,
                        standard.analysisDate(),
                        BigDecimal.ZERO));

        SocialSecurityStrategyRequest differentDob = new SocialSecurityStrategyRequest(
                standard.analysisDate(),
                standard.analysisEndDate(),
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1964, 1, 2), 3000, 2025,
                        LocalDate.of(2026, 1, 2)),
                standard.spouseElection(),
                null,
                null,
                null,
                standard.spouseDeathDate(),
                BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityStrategyComparisonRequest(
                        standard,
                        differentDob,
                        standard.analysisDate(),
                        BigDecimal.ZERO));
    }

    private SocialSecurityStrategyComparisonResult compare(
            SocialSecurityStrategyRequest strategyA,
            SocialSecurityStrategyRequest strategyB,
            BigDecimal realDiscountRate) {
        return calculator.calculate(new SocialSecurityStrategyComparisonRequest(
                strategyA,
                strategyB,
                strategyA.analysisDate(),
                realDiscountRate));
    }

    private SocialSecurityStrategyRequest singlePrimaryStrategy(
            LocalDate primaryClaimDate,
            LocalDate endDate,
            BigDecimal cola) {
        LocalDate analysisDate = LocalDate.of(2025, 1, 1);
        return new SocialSecurityStrategyRequest(
                analysisDate,
                endDate,
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), 3000, 2025,
                        primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), 0, 2025,
                        LocalDate.of(2032, 1, 2)),
                null,
                null,
                null,
                analysisDate,
                cola);
    }

    private SocialSecurityStrategyRequest marriedStrategy(
            LocalDate primaryClaimDate,
            LocalDate spouseClaimDate,
            LocalDate spouseSurvivorClaimDate,
            LocalDate primaryDeath,
            LocalDate spouseDeath) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2050, 1, 31),
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), 3000, 2025,
                        primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), 1200, 2025,
                        spouseClaimDate),
                null,
                spouseSurvivorClaimDate,
                primaryDeath,
                spouseDeath,
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int fraBenefit,
            int valuationYear,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(fraBenefit),
                valuationYear,
                claimDate);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
