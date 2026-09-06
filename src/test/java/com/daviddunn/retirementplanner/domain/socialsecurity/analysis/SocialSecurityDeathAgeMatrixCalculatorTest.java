package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityDeathAgeMatrixCalculatorTest {

    private final SocialSecurityDeathAgeMatrixCalculator calculator =
            new SocialSecurityDeathAgeMatrixCalculator();

    @Test
    void oneByOneCellEqualsDirectComparisonForDerivedDeaths() {
        SocialSecurityDeathAgeMatrixRequest request = request(
                baseStrategy(LocalDate.of(2033, 1, 2)),
                baseStrategy(LocalDate.of(2030, 1, 2)),
                List.of(85),
                List.of(95));

        SocialSecurityDeathAgeMatrixCell cell = calculator.calculate(request)
                .cells().getFirst();
        SocialSecurityStrategyRequest directA =
                SocialSecurityDeathAgeMatrixCalculator.deriveStrategy(
                        request.strategyA(),
                        cell.primaryDeathDate(),
                        cell.spouseDeathDate());
        SocialSecurityStrategyRequest directB =
                SocialSecurityDeathAgeMatrixCalculator.deriveStrategy(
                        request.strategyB(),
                        cell.primaryDeathDate(),
                        cell.spouseDeathDate());
        SocialSecurityStrategyComparisonResult direct =
                new SocialSecurityStrategyComparisonCalculator().calculate(
                        new SocialSecurityStrategyComparisonRequest(
                                directA,
                                directB,
                                request.presentValueBaseDate(),
                                request.realDiscountRate()));

        assertEquals(LocalDate.of(2048, 1, 2), cell.primaryDeathDate());
        assertEquals(LocalDate.of(2060, 1, 2), cell.spouseDeathDate());
        assertEquals(direct, cell.comparison());
    }

    @Test
    void twoByThreeMatrixUsesPrimaryRowsAndSpouseColumns() {
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(
                        baseStrategy(LocalDate.of(2033, 1, 2)),
                        baseStrategy(LocalDate.of(2030, 1, 2)),
                        List.of(80, 90),
                        List.of(75, 85, 95)));

        assertEquals(6, result.cells().size());
        assertEquals(List.of("80/75", "80/85", "80/95",
                        "90/75", "90/85", "90/95"),
                result.cells().stream()
                        .map(cell -> cell.primaryDeathAge()
                                + "/" + cell.spouseDeathAge())
                        .toList());
        assertTrue(result.cellFor(90, 85).isPresent());
        assertEquals(90,
                result.cellFor(90, 85).orElseThrow().primaryDeathAge());
        assertFalse(result.cellFor(85, 85).isPresent());
    }

    @Test
    void identicalStrategiesTieInEveryCellAndCountsReconcile() {
        SocialSecurityStrategyRequest strategy = baseStrategy(
                LocalDate.of(2030, 1, 2));
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(strategy, strategy,
                        List.of(75, 90),
                        List.of(80, 100)));

        result.cells().forEach(cell -> {
            assertMoney(BigDecimal.ZERO, cell.nominalDifference());
            assertMoney(BigDecimal.ZERO, cell.realDifference());
            assertMoney(BigDecimal.ZERO, cell.presentValueDifference());
            assertEquals(StrategyComparisonWinner.TIE,
                    cell.nominalWinner());
            assertEquals(StrategyComparisonWinner.TIE,
                    cell.realWinner());
            assertEquals(StrategyComparisonWinner.TIE,
                    cell.presentValueWinner());
            assertEquals(SocialSecurityBreakEvenStatus.IDENTICAL,
                    cell.nominalBreakEven().status());
        });
        Map<StrategyComparisonWinner, Long> counts =
                result.presentValueWinnerCounts();
        assertEquals(4L, counts.get(StrategyComparisonWinner.TIE));
        assertEquals(result.cells().size(), counts.values().stream()
                .mapToLong(Long::longValue).sum());
    }

    @Test
    void delayedClaimantDyingAt68NeverReceivesOwnBenefit() {
        SocialSecurityDeathAgeMatrixCell cell = calculator.calculate(
                        request(
                                baseStrategy(LocalDate.of(2033, 1, 2)),
                                baseStrategy(LocalDate.of(2030, 1, 2)),
                                List.of(68),
                                List.of(100)))
                .cells().getFirst();

        assertTrue(cell.comparison().strategyA().strategyResult()
                .monthlyResults().stream()
                .allMatch(month -> month.primaryOwnBenefit().signum() == 0));
        assertTrue(cell.comparison().strategyB().strategyResult()
                .monthlyResults().stream()
                .anyMatch(month -> month.primaryOwnBenefit().signum() > 0));
    }

    @Test
    void longSurvivingSpouseAndReverseLongevityUseActualDeathOrder() {
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(
                        baseStrategy(LocalDate.of(2033, 1, 2)),
                        baseStrategy(LocalDate.of(2030, 1, 2)),
                        List.of(68, 100),
                        List.of(68, 100)));

        SocialSecurityDeathAgeMatrixCell spouseSurvives =
                result.cellFor(68, 100).orElseThrow();
        assertTrue(spouseSurvives.comparison().strategyA().strategyResult()
                .monthlyResults().stream()
                .anyMatch(month -> month.spouseSurvivorBenefit().signum() > 0));

        SocialSecurityStrategyRequest reverseBenefits = baseStrategy(
                LocalDate.of(2033, 1, 2),
                1200,
                3000);
        SocialSecurityDeathAgeMatrixCell primarySurvives = calculator.calculate(
                        request(
                                reverseBenefits,
                                reverseBenefits,
                                List.of(100),
                                List.of(68)))
                .cells().getFirst();
        assertTrue(primarySurvives.comparison().strategyA().strategyResult()
                .monthlyResults().stream()
                .anyMatch(month -> month.primarySurvivorBenefit().signum() > 0));
    }

    @Test
    void equalDeathAgesUseDifferentDobDatesAndNaturalSameDatesRemainDeterministic() {
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(
                        baseStrategy(LocalDate.of(2033, 1, 2)),
                        baseStrategy(LocalDate.of(2030, 1, 2)),
                        List.of(85),
                        List.of(83, 85)));

        SocialSecurityDeathAgeMatrixCell equalAges =
                result.cellFor(85, 85).orElseThrow();
        assertEquals(LocalDate.of(2048, 1, 2),
                equalAges.primaryDeathDate());
        assertEquals(LocalDate.of(2050, 1, 2),
                equalAges.spouseDeathDate());

        SocialSecurityDeathAgeMatrixCell sameDate =
                result.cellFor(85, 83).orElseThrow();
        assertEquals(sameDate.primaryDeathDate(), sameDate.spouseDeathDate());
        assertMoney(BigDecimal.ZERO,
                month(sameDate.comparison(), 2048, 1)
                        .strategyAMonth().householdBenefit());
    }

    @Test
    void deathBeforeEligibilityAndAge110CompleteWithoutInventedPayments() {
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(
                        baseStrategy(LocalDate.of(2033, 1, 2)),
                        baseStrategy(LocalDate.of(2030, 1, 2)),
                        List.of(58, 110),
                        List.of(100)));

        SocialSecurityDeathAgeMatrixCell earlyDeath =
                result.cellFor(58, 100).orElseThrow();
        assertTrue(earlyDeath.comparison().strategyA().strategyResult()
                .monthlyResults().stream()
                .allMatch(month -> month.primarySelectedBenefit().signum() == 0));
        assertTrue(result.cellFor(110, 100).orElseThrow()
                .comparison().strategyA().presentValue().signum() >= 0);
    }

    @Test
    void unreachableSurvivorClaimIsInactiveWithoutMutatingBaseStrategy() {
        SocialSecurityStrategyRequest base = baseStrategy(
                LocalDate.of(2030, 1, 2));
        LocalDate originalSurvivorClaim = base.spouseSurvivorClaimDate();

        SocialSecurityDeathAgeMatrixCell cell = calculator.calculate(
                        request(base, base, List.of(90), List.of(58)))
                .cells().getFirst();

        assertEquals(originalSurvivorClaim, base.spouseSurvivorClaimDate());
        assertEquals(null, cell.comparison().request().strategyA()
                .spouseSurvivorClaimDate());
        assertTrue(cell.comparison().strategyA().strategyResult()
                .monthlyResults().stream()
                .allMatch(month -> month.spouseSurvivorBenefit().signum() == 0));
    }

    @Test
    void everyCellDifferencesAndBreakEvenComeFromFullComparison() {
        SocialSecurityDeathAgeMatrixResult result = calculator.calculate(
                request(
                        baseStrategy(LocalDate.of(2025, 1, 2)),
                        baseStrategy(LocalDate.of(2033, 1, 2)),
                        List.of(68, 95),
                        List.of(60)));

        result.cells().forEach(cell -> {
            SocialSecurityStrategyComparisonResult comparison = cell.comparison();
            assertMoney(comparison.strategyA().nominalLifetimeBenefits()
                            .subtract(comparison.strategyB()
                                    .nominalLifetimeBenefits()),
                    cell.nominalDifference());
            assertMoney(comparison.strategyA().realLifetimeBenefits()
                            .subtract(comparison.strategyB()
                                    .realLifetimeBenefits()),
                    cell.realDifference());
            assertMoney(comparison.strategyA().presentValue()
                            .subtract(comparison.strategyB().presentValue()),
                    cell.presentValueDifference());
            assertEquals(comparison.nominalBreakEven(),
                    cell.nominalBreakEven());
        });

        assertEquals(StrategyComparisonWinner.STRATEGY_A,
                result.cellFor(68, 60).orElseThrow().nominalWinner());
        assertEquals(StrategyComparisonWinner.STRATEGY_B,
                result.cellFor(95, 60).orElseThrow().nominalWinner());
    }

    private SocialSecurityDeathAgeMatrixRequest request(
            SocialSecurityStrategyRequest strategyA,
            SocialSecurityStrategyRequest strategyB,
            List<Integer> primaryAges,
            List<Integer> spouseAges) {
        return new SocialSecurityDeathAgeMatrixRequest(
                strategyA,
                strategyB,
                primaryAges,
                spouseAges,
                LocalDate.of(2025, 1, 1),
                new BigDecimal("0.01"));
    }

    private SocialSecurityStrategyRequest baseStrategy(
            LocalDate primaryClaimDate) {
        return baseStrategy(primaryClaimDate, 3000, 1200);
    }

    private SocialSecurityStrategyRequest baseStrategy(
            LocalDate primaryClaimDate,
            int primaryBenefit,
            int spouseBenefit) {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), primaryBenefit,
                        primaryClaimDate),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), spouseBenefit,
                        LocalDate.of(2032, 1, 2)),
                LocalDate.of(2023, 1, 2),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2053, 1, 2),
                LocalDate.of(2060, 1, 2),
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(benefit),
                2025,
                claimDate);
    }

    private SocialSecurityMonthlyComparison month(
            SocialSecurityStrategyComparisonResult comparison,
            int year,
            int month) {
        YearMonth expected = YearMonth.of(year, month);
        return comparison.monthlyComparisons().stream()
                .filter(value -> value.month().equals(expected))
                .findFirst()
                .orElseThrow();
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
