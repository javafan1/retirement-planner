package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicHeatMapAdapterTest {
    static ProjectionMetrics metrics(String estate) {
        BigDecimal zero = BigDecimal.ZERO;
        return new ProjectionMetrics(zero, zero, zero, zero, new BigDecimal("14000000"), zero,
                new BigDecimal(estate), zero, zero, zero, zero, zero, zero, zero);
    }

    static IntegratedSocialSecurityCompleteStrategySearchEntry entry(int order, int primary, int spouse,
            int survivor, String estate, int rank, String delta) {
        var strategy = ClaimingStrategyHeatMapModelTest.entry(order, primary, spouse, survivor, 65, estate, rank, null).strategy();
        return new IntegratedSocialSecurityCompleteStrategySearchEntry(order, strategy, OptionalInt.of(rank),
                Optional.of(metrics(estate)), Optional.of(metrics(delta)), Optional.empty(), Optional.empty());
    }

    static ExhaustiveIntegratedSearchPresentation presentation(List<IntegratedSocialSecurityCompleteStrategySearchEntry> entries, int limit) {
        var baseline = new IntegratedSocialSecurityStrategyResult(entry(100, 67, 67, 60, "2000", 1, "0").strategy(),
                new Projection(), metrics("2000"), true, List.of());
        var ranked = entries.stream().filter(IntegratedSocialSecurityCompleteStrategySearchEntry::successful)
                .sorted(Comparator.comparingInt(entry -> entry.afterTaxEstateRank().orElseThrow())).toList();
        return ExhaustiveIntegratedSearchPresentation.from(new IntegratedSocialSecurityCompleteStrategySearchResult(
                baseline, IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, entries, ranked, OptionalInt.empty()), Duration.ZERO, limit);
    }

    static ExhaustiveIntegratedSearchPresentation fixture() {
        return presentation(List.of(entry(1, 69, 62, 66, "1000", 1, "-1000"),
                entry(2, 62, 70, 67, "993.4567", 2, "-1006.5433")), 1);
    }

    @Test void usesCompleteResultsNotLimitedGroupsOrBaselineForOptimalAndKeepsExactDifferences() {
        var source = fixture();
        var model = DeterministicHeatMapAdapter.from(source);
        assertEquals(1, source.groups().size());
        assertEquals(new BigDecimal("100"), model.cell(69, 62).value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL).orElseThrow());
        var other = model.cell(62, 70);
        assertEquals(new BigDecimal("99.34567"), other.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL).orElseThrow());
        assertEquals(new BigDecimal("-6.5433"), other.value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL).orElseThrow());
        assertEquals(new BigDecimal("-1006.5433"), other.value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT).orElseThrow());
        assertEquals("99.3%", ClaimingStrategyHeatMapView.format(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                other.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)));
        assertEquals(2, other.strategy().orElseThrow().rank().orElseThrow());
        assertTrue(other.value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE).isEmpty());
        assertTrue(other.value(ClaimingStrategyHeatMapMetric.EXPECTED_PV_SOCIAL_SECURITY).isEmpty());
    }

    @Test void mapsAll81PairsAndChoosesFirstBackendRankedSurvivorVariantIncludingExactTies() {
        var entries = new ArrayList<IntegratedSocialSecurityCompleteStrategySearchEntry>();
        for (int spouse = 62; spouse <= 70; spouse++) for (int primary = 62; primary <= 70; primary++) {
            int order = entries.size() + 1;
            entries.add(entry(order, primary, spouse, 60, Integer.toString(order), 84 - order, "0"));
        }
        entries.add(entry(82, 62, 62, 67, "100", 1, "0"));
        entries.add(entry(83, 62, 62, 66, "100", 1, "0"));
        entries.add(entry(84, 70, 70, 65, "100", 1, "0"));
        var model = DeterministicHeatMapAdapter.from(presentation(entries, 1));
        assertEquals(81, model.cells().size());
        assertTrue(model.cells().stream().allMatch(cell -> cell.strategy().isPresent()));
        assertSame(model.cell(70, 62), model.cells().get(8));
        assertSame(model.cell(62, 70), model.cells().get(72));
        var best = model.cell(62, 62).strategy().orElseThrow();
        assertEquals(82, best.inputOrder());
        assertEquals(67, best.primarySurvivor().ageYears());
        assertEquals(4, best.primarySurvivor().ageMonths());
        assertEquals(entries.get(81).strategy().primarySurvivorElection().claimDate(), best.primarySurvivor().claimDate());
        assertEquals(entries.get(81).strategy().spouseSurvivorElection().claimDate(), best.spouseSurvivor().claimDate());
        assertTrue(best.optimal());
        assertTrue(model.cell(70, 70).optimal());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-10"})
    void nonPositiveOptimalLeavesPercentUnavailableAndRetainsDollarMetrics(String estate) {
        var model = DeterministicHeatMapAdapter.from(presentation(List.of(entry(1, 62, 62, 60, estate, 1, "0")), 1));
        var cell = model.cell(62, 62);
        assertTrue(cell.optimal());
        assertTrue(cell.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL).isEmpty());
        assertEquals(new BigDecimal(estate), cell.value(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE).orElseThrow());
        assertEquals(0, cell.value(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL).orElseThrow().signum());
    }

    @Test void missingAndFailedPairsAreUnavailableNotZero() {
        var original = entry(1, 62, 62, 60, "1", 1, "0");
        var failed = new IntegratedSocialSecurityCompleteStrategySearchEntry(1, original.strategy(), OptionalInt.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(
                original.strategy(), "Controlled failure", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)));
        var model = DeterministicHeatMapAdapter.from(presentation(List.of(failed), 1));
        assertTrue(model.optimalValue().isEmpty());
        assertEquals(1, model.cell(62, 62).failedStrategyCount());
        for (var cell : model.cells()) for (var metric : ClaimingStrategyHeatMapMetric.values()) assertTrue(cell.value(metric).isEmpty());
    }

    @Test void metricsAreImmutableAndDoNotChangeSourceResults() {
        var source = fixture();
        var before = source.result().entries();
        var model = DeterministicHeatMapAdapter.from(source);
        for (var cell : model.cells()) for (var metric : ClaimingStrategyHeatMapMetric.values()) cell.value(metric);
        assertSame(before, source.result().entries());
        assertThrows(UnsupportedOperationException.class, () -> model.cell(69, 62).strategy().orElseThrow().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.cells().clear());
    }
}
