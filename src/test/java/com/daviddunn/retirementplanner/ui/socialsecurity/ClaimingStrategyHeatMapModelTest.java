package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimingStrategyHeatMapModelTest {
    static LongevityWeightedIntegratedStrategyComparisonEntry entry(int order, int primary, int spouse,
            int primarySurvivor, int spouseSurvivor, String pv, int rank, String delta) {
        LocalDate p = LocalDate.of(1963, 6, 4);
        LocalDate s = LocalDate.of(1965, 2, 28);
        var strategy = new SocialSecurityHouseholdClaimingStrategy(primary, spouse, p.plusYears(primary), s.plusYears(spouse),
                new SocialSecuritySurvivorClaimingCandidate(p.plusYears(primarySurvivor).plusMonths(4), primarySurvivor, 4, "Primary survivor"),
                new SocialSecuritySurvivorClaimingCandidate(s.plusYears(spouseSurvivor).plusMonths(7), spouseSurvivor, 7, "Spouse survivor"));
        BigDecimal value = new BigDecimal(pv);
        return new LongevityWeightedIntegratedStrategyComparisonEntry(order, strategy,
                Optional.of(new LongevityWeightedStrategyAggregate(value, new BigDecimal("5000000").add(BigDecimal.valueOf(order)),
                        new BigDecimal("6000000"), value, value, BigDecimal.ONE, 1, 1)), Optional.empty(), Optional.empty(),
                rank == 0 ? OptionalInt.empty() : OptionalInt.of(rank), Optional.ofNullable(delta).map(BigDecimal::new), Optional.empty());
    }

    static LongevityWeightedIntegratedStrategyComparisonEntry failed(int order, int primary, int spouse) {
        var source = entry(order, primary, spouse, 60, 60, "1", 1, null);
        return new LongevityWeightedIntegratedStrategyComparisonEntry(order, source.strategy(), Optional.empty(),
                Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(source.strategy(), "Controlled failure",
                        IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)), Optional.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.empty());
    }

    static LongevityWeightedIntegratedPresentation presentation(List<LongevityWeightedIntegratedStrategyComparisonEntry> entries,
            Optional<LongevityWeightedIntegratedStrategyComparisonEntry> baseline) {
        var original = LongevityWeightedIntegratedPresentationTest.model(entries, baseline);
        var result = original.result();
        var ranked = entries.stream().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .sorted(Comparator.comparingInt((LongevityWeightedIntegratedStrategyComparisonEntry entry) -> entry.rank().orElseThrow())
                        .thenComparingInt(LongevityWeightedIntegratedStrategyComparisonEntry::inputOrder)).toList();
        return new LongevityWeightedIntegratedPresentation(new LongevityWeightedIntegratedStrategyComparisonResult(result.objective(),
                result.metadata(), result.orderedEntries(), ranked, baseline, result.work(), result.elapsedTime(), result.status()), 7, 9);
    }

    @Test void usesBackendHighestInsteadOfCurrentBaselineOrInputOrder() {
        var lower = entry(1, 62, 70, 60, 65, "993.45", 2, "-106.55");
        var highest = entry(2, 69, 62, 67, 60, "1000", 1, "-100");
        var current = entry(0, 67, 67, 65, 65, "1100", 0, null);
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(lower, highest), Optional.of(current)));
        var optimal = model.cell(69, 62);
        assertEquals(0, new BigDecimal("100").compareTo(optimal.strategy().orElseThrow().percentOfOptimal().orElseThrow()));
        assertTrue(optimal.optimal());
        assertEquals(new BigDecimal("1000"), model.optimalValue().orElseThrow());
        var other = model.cell(62, 70).strategy().orElseThrow();
        assertEquals(new BigDecimal("99.345"), other.percentOfOptimal().orElseThrow());
        assertEquals(new BigDecimal("-6.55"), other.differenceFromOptimal());
        assertEquals(new BigDecimal("-106.55"), other.differenceFromCurrent().orElseThrow());
        assertFalse(other.optimal());
    }

    @Test void bestCompleteSurvivorVariantAndStableTiesKeepOriginalIdentity() {
        var weak = entry(1, 70, 62, 60, 60, "98", 4, null);
        var best = entry(3, 70, 62, 66, 64, "100", 1, null);
        var tie = entry(4, 70, 62, 67, 65, "100.00", 1, null);
        var otherOptimal = entry(5, 63, 68, 65, 66, "100", 1, null);
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(tie, weak, otherOptimal, best), Optional.empty()));
        var selected = model.cell(70, 62).strategy().orElseThrow();
        assertEquals(3, selected.inputOrder());
        assertEquals(66, selected.primarySurvivor().ageYears());
        assertEquals(4, selected.primarySurvivor().ageMonths());
        assertEquals(best.strategy().primarySurvivorElection().claimDate(), selected.primarySurvivor().claimDate());
        assertEquals(64, selected.spouseSurvivor().ageYears());
        assertEquals(7, selected.spouseSurvivor().ageMonths());
        assertEquals(best.strategy().spouseSurvivorElection().claimDate(), selected.spouseSurvivor().claimDate());
        assertTrue(model.cell(63, 68).optimal());
        assertEquals(2, model.cells().stream().filter(ClaimingStrategyHeatMapCell::optimal).count());
    }

    @Test void mapsPrimaryToColumnsAndSpouseToRowsAcrossAll81Cells() {
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(
                entry(1, 70, 62, 60, 60, "100", 1, null), entry(2, 62, 70, 60, 60, "90", 2, null)), Optional.empty()));
        assertEquals(81, model.cells().size());
        assertEquals(List.of(62, 63, 64, 65, 66, 67, 68, 69, 70), model.primaryAges());
        assertEquals(model.primaryAges(), model.spouseAges());
        assertSame(model.cell(70, 62), model.cells().get(8));
        assertSame(model.cell(62, 70), model.cells().get(72));
        assertEquals(new BigDecimal("100"), model.cell(70, 62).value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE).orElseThrow());
        assertEquals(new BigDecimal("90"), model.cell(62, 70).value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> model.cell(61, 62));
    }

    @Test void missingAndFailedPairsHaveNoZeroValuesOrSurvivorFallback() {
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(
                entry(1, 63, 63, 64, 65, "100", 1, null), failed(2, 62, 62), failed(3, 63, 63)), Optional.empty()));
        for (var missing : List.of(model.cell(62, 62), model.cell(70, 70))) {
            assertTrue(missing.strategy().isEmpty());
            assertFalse(missing.optimal());
            for (var metric : ClaimingStrategyHeatMapMetric.values()) assertTrue(missing.value(metric).isEmpty());
        }
        assertEquals(1, model.cell(62, 62).failedStrategyCount());
        assertEquals(0, model.cell(70, 70).failedStrategyCount());
        assertEquals(1, model.cell(63, 63).failedStrategyCount());
        assertTrue(model.cell(63, 63).strategy().isPresent());
    }

    @Test void baselineDifferencesKeepExactSignedValuesAndZero() {
        var values = List.of(entry(1, 62, 62, 60, 60, "100.004", 1, "0.004"),
                entry(2, 63, 63, 60, 60, "100", 2, "0"), entry(3, 64, 64, 60, 60, "99.997", 3, "-0.003"));
        var model = LongevityWeightedHeatMapAdapter.from(presentation(values, Optional.of(entry(0, 63, 63, 60, 60, "100", 0, null))));
        assertEquals(new BigDecimal("0.004"), model.cell(62, 62).value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT).orElseThrow());
        assertEquals(BigDecimal.ZERO, model.cell(63, 63).value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT).orElseThrow());
        assertEquals(new BigDecimal("-0.003"), model.cell(64, 64).value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT).orElseThrow());
    }

    @Test void missingAndFailedBaselineNeverInventCurrentDifferences() {
        var candidates = List.of(entry(1, 62, 62, 60, 60, "100", 1, "10"));
        for (var baseline : List.of(Optional.<LongevityWeightedIntegratedStrategyComparisonEntry>empty(), Optional.of(failed(0, 62, 62)))) {
            var model = LongevityWeightedHeatMapAdapter.from(presentation(candidates, baseline));
            assertTrue(model.cell(62, 62).value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT).isEmpty());
            assertTrue(model.cell(62, 62).strategy().isPresent());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-100"})
    void nonPositiveOptimalDisablesPercentageWithoutLosingDollarValues(String pv) {
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(entry(1, 62, 62, 60, 60, pv, 1, null)), Optional.empty()));
        var cell = model.cell(62, 62);
        assertTrue(cell.optimal());
        assertTrue(cell.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL).isEmpty());
        assertEquals(new BigDecimal(pv), cell.value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE).orElseThrow());
        assertEquals(0, cell.value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL).orElseThrow().signum());
    }

    @Test void entirelyFailedAnalysisHasNoOptimal() {
        var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(failed(1, 62, 62)), Optional.empty()));
        assertTrue(model.optimalValue().isEmpty());
        assertEquals(81, model.cells().size());
        assertTrue(model.cells().stream().allMatch(cell -> cell.strategy().isEmpty()));
    }

    @Test void metricAccessPreservesImmutableResultsAndUsesOnlySuppliedValues() {
        var source = presentation(List.of(entry(1, 62, 62, 65, 66, "100.004", 1, null)), Optional.empty());
        var before = source.result();
        var model = LongevityWeightedHeatMapAdapter.from(source);
        var cell = model.cell(62, 62);
        for (var metric : ClaimingStrategyHeatMapMetric.values()) cell.value(metric);
        assertSame(before, source.result());
        assertEquals(new BigDecimal("100.004"), before.orderedEntries().getFirst().aggregate().orElseThrow().expectedPvAfterTaxEstate());
        assertEquals(new BigDecimal("5000001"), cell.value(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE).orElseThrow());
        assertTrue(cell.value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_SOCIAL_SECURITY).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> model.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.primaryAges().clear());
    }
}
