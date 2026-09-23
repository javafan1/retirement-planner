package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenAnalyzerTest {
    private final BreakEvenAnalyzer analyzer = new BreakEvenAnalyzer();

    @Test void normalRecovery() {
        var result = differences(-100, -50, 10, 40);
        assertEquals(BreakEvenStatus.BREAK_EVEN_REACHED, result.status());
        assertEquals(2029, result.firstCrossoverYear());
        assertEquals(2029, result.sustainedBreakEvenYear());
    }
    @Test void noRecoveryExposesDeficit() {
        var result = differences(-100, -50, -20);
        assertEquals(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD, result.status());
        assertEquals(new BigDecimal("-20"), result.finalDifference());
        assertNull(result.firstCrossoverYear());
        assertNull(result.sustainedBreakEvenYear());
    }
    @Test void alreadyAheadHasNoTraditionalBreakEven() {
        var result = differences(10, 20, 30);
        assertEquals(BreakEvenStatus.CURRENT_ALREADY_AHEAD, result.status());
        assertNull(result.firstCrossoverYear());
        assertNull(result.sustainedBreakEvenYear());
    }
    @Test void identicalHasNoMeaningfulCrossover() {
        assertEquals(BreakEvenStatus.IDENTICAL, differences(0, 0, 0).status());
    }
    @Test void temporaryCrossoversAreRetained() {
        var result = differences(-100, 10, -20, 30, 40);
        assertEquals(2028, result.firstCrossoverYear());
        assertEquals(2030, result.sustainedBreakEvenYear());
        assertEquals(List.of(new BreakEvenMetricResult.Crossing(2028, true),
                new BreakEvenMetricResult.Crossing(2029, false),
                new BreakEvenMetricResult.Crossing(2030, true)), result.crossings());
    }
    @Test void crossoverWithoutSustainedRecovery() {
        var result = differences(-100, 10, -20);
        assertEquals(BreakEvenStatus.CROSSOVER_NOT_SUSTAINED, result.status());
        assertEquals(2028, result.firstCrossoverYear());
        assertNull(result.sustainedBreakEvenYear());
    }
    @Test void initialEqualityDoesNotHideLaterDeficit() {
        var result = differences(0, -100, 20);
        assertEquals(BreakEvenStatus.BREAK_EVEN_REACHED, result.status());
        assertEquals(2029, result.sustainedBreakEvenYear());
        assertEquals(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD, differences(10, -10).status());
        assertEquals(2029, differences(10, -10, 0).sustainedBreakEvenYear());
    }
    @Test void horizonsMayDifferInEitherDirection() {
        var shortPlan = snapshot(range(2027, 2036));
        var longPlan = snapshot(range(2027, 2056));
        for (boolean reverse : List.of(false, true)) {
            var result = analyzer.analyze(reverse ? longPlan : shortPlan, reverse ? shortPlan : longPlan);
            assertEquals(2027, result.comparisonStartYear());
            assertEquals(2036, result.comparisonEndYear());
            assertEquals(10, result.comparableYearCount());
            assertTrue(result.planningHorizonsDiffer());
            assertEquals(reverse ? 2056 : 2036, result.baselineEndYear());
            assertEquals(reverse ? 2036 : 2056, result.currentEndYear());
            result.metrics().values().forEach(metric -> {
                assertEquals(10, metric.years().size());
                assertEquals(2036, metric.years().getLast().year());
            });
        }
    }
    @Test void intersectionUsesCalendarYearsIncludingGapsAndDifferentStarts() {
        var result = analyzer.analyze(snapshot(List.of(year(2029, 100), year(2027, 10), year(2032, 1000))),
                snapshot(List.of(year(2032, 1005), year(2028, 200), year(2029, 90))));
        assertEquals(2027, result.baselineStartYear());
        assertEquals(2028, result.currentStartYear());
        var metric = result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS);
        assertEquals(List.of(2029, 2032), metric.years().stream().map(BreakEvenYearResult::year).toList());
        assertEquals(List.of(new BigDecimal("-10"), new BigDecimal("5")),
                metric.years().stream().map(BreakEvenYearResult::difference).toList());
    }
    @Test void socialSecurityAccumulatesOnlyActualSharedYears() {
        var baseline = snapshot(List.of(ssYear(2027, "100"), ssYear(2028, "100"), ssYear(2029, "100")));
        var current = snapshot(List.of(ssYear(2027, "0"), ssYear(2028, "150"), ssYear(2029, "160")));
        var result = analyzer.analyze(baseline, current).metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY);
        assertEquals(List.of(new BigDecimal("-100"), new BigDecimal("-50"), new BigDecimal("10")),
                result.years().stream().map(BreakEvenYearResult::difference).toList());
        assertEquals(new BigDecimal("300"), result.years().getLast().baselineValue());
        assertEquals(new BigDecimal("310"), result.years().getLast().currentValue());
        assertEquals(2029, result.sustainedBreakEvenYear());
        var overlap = analyzer.analyze(baseline, snapshot(List.of(ssYear(2028, "150"), ssYear(2030, "999"))));
        assertEquals(new BigDecimal("50"), overlap.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).finalDifference());
    }
    @Test void balanceSheetMetricsAreNeverAccumulatedAndNetWorthIncludesNonInvestableAssets() {
        var baseline = snapshot(List.of(year(2027, 100), year(2028, 100)));
        var current = new BreakEvenProjectionSnapshot(List.of(year(2027, 120), year(2028, 130)),
                List.of(new NonInvestableAssetProjection(2027, List.of(), new BigDecimal("50")),
                        new NonInvestableAssetProjection(2028, List.of(), new BigDecimal("60"))), summary());
        var result = analyzer.analyze(baseline, current);
        assertEquals(new BigDecimal("30"), result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).finalDifference());
        assertEquals(new BigDecimal("30"), result.metrics().get(BreakEvenMetric.AFTER_TAX_ESTATE).finalDifference());
        assertEquals(new BigDecimal("90"), result.metrics().get(BreakEvenMetric.TOTAL_NET_WORTH).finalDifference());
    }
    @Test void noDisplayRoundingOrImplicitTolerance() {
        var result = analyzer.analyze(snapshot(List.of(ssYear(2027, "100.42"), ssYear(2028, "0.001"))),
                snapshot(List.of(ssYear(2027, "100"), ssYear(2028, "0.42"))))
                .metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY);
        assertEquals(BreakEvenStatus.NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD, result.status());
        assertEquals(new BigDecimal("-0.001"), result.finalDifference());
    }
    @Test void emptyAndDisjointProjectionsAreGraceful() {
        for (var baseline : List.of(snapshot(List.of()), snapshot(List.of(year(2020, 1))))) {
            var result = analyzer.analyze(baseline, snapshot(List.of(year(2027, 1))));
            assertEquals(0, result.comparableYearCount());
            assertNull(result.comparisonStartYear());
            assertNull(result.comparisonEndYear());
            result.metrics().values().forEach(metric -> {
                assertEquals(BreakEvenStatus.NO_COMPARABLE_YEARS, metric.status());
                assertTrue(metric.years().isEmpty());
                assertNull(metric.finalDifference());
            });
        }
    }
    @Test void agesUseProjectedPrimaryAndYearEndSpouseConvention() {
        var result = differences(-1, 1).years().getLast();
        assertEquals(70, result.primaryAge());
        assertEquals(63, result.spouseAge());
    }
    @Test void immutableInputsOutputsAndDuplicateYearDefense() {
        var years = new ArrayList<>(List.of(year(2027, 1)));
        var snapshot = snapshot(years);
        years.clear();
        assertEquals(1, snapshot.years().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.years().clear());
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(snapshot,
                snapshot(List.of(year(2027, 1), year(2027, 2)))));
    }

    private BreakEvenMetricResult differences(long... differences) {
        List<ProjectionYear> baseline = new ArrayList<>(), current = new ArrayList<>();
        for (int i = 0; i < differences.length; i++) {
            baseline.add(year(2027 + i, 1000));
            current.add(year(2027 + i, 1000 + differences[i]));
        }
        return analyzer.analyze(snapshot(baseline), snapshot(current)).metrics().get(BreakEvenMetric.INVESTABLE_ASSETS);
    }
    private static List<ProjectionYear> range(int start, int end) {
        return IntStream.rangeClosed(start, end).mapToObj(year -> year(year, 1000)).toList();
    }
    private static ProjectionYear year(int year, long amount) {
        return ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).withPrimaryPersonAge(70)
                .withEndingInvestableAssets(amount).withAfterTaxEstateValue(amount).build();
    }
    private static ProjectionYear ssYear(int year, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).withSocialSecurityResult(
                new HouseholdSocialSecurityResult(value, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        SocialSecurityBenefitSelection.OWN, SocialSecurityBenefitSelection.NONE, value)).build();
    }
    private static BreakEvenProjectionSnapshot snapshot(List<ProjectionYear> years) {
        return new BreakEvenProjectionSnapshot(years, List.of(), summary());
    }
    private static BreakEvenPlanSummary summary() {
        return new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("David", LocalDate.of(1963, 6, 4), 70),
                new BreakEvenPlanSummary.PersonSummary("Lisa", LocalDate.of(1965, 12, 31), 62));
    }
}
