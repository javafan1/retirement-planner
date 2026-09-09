package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LongevityWeightedIntegratedPresentationTest {
    static final LongevityWeightedIntegratedStrategyComparisonRequest REQUEST =
            LongevityWeightedAnalysisRequestFactoryTest.request(LongevityWeightedAnalysisRequestFactoryTest.plan());
    static LongevityWeightedIntegratedStrategyComparisonEntry entry(int order, String amount, Integer rank) {
        var value = new BigDecimal(amount);
        return new LongevityWeightedIntegratedStrategyComparisonEntry(order, REQUEST.candidates().get(order == 0 ? 0 : order - 1),
                Optional.of(new LongevityWeightedStrategyAggregate(value, value, value, value, BigDecimal.ONE, 2, 2)),
                Optional.empty(), Optional.empty(), rank == null ? OptionalInt.empty() : OptionalInt.of(rank), Optional.empty(), Optional.empty());
    }
    static LongevityWeightedIntegratedPresentation model(List<LongevityWeightedIntegratedStrategyComparisonEntry> entries,
            Optional<LongevityWeightedIntegratedStrategyComparisonEntry> baseline) {
        var metadata = new LongevityWeightedIntegratedStrategyComparisonResult.Metadata("test", "test", REQUEST.longevityScenarios().assumptions(),
                REQUEST.valuationDate(), BigDecimal.ZERO, REQUEST.realDiscountRate(), 2, BigDecimal.ONE, List.of());
        var result = new LongevityWeightedIntegratedStrategyComparisonResult(LongevityWeightedComparisonObjective.EXPECTED_PV_AFTER_TAX_ESTATE,
                metadata, entries, entries.stream().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful).toList(), baseline,
                new LongevityWeightedIntegratedStrategyComparisonResult.Work(0, 0, 0, 0, 0), Duration.ZERO,
                LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED);
        return new LongevityWeightedIntegratedPresentation(result, 7, 9);
    }
    @Test void preservesCompetitionRanksOccurrencesAndExactCurrentRank() {
        var entries = List.of(entry(1, "100", 1), entry(2, "100.00", 1), entry(3, "90", 3));
        var model = model(entries, Optional.of(entry(0, "100", null)));
        assertEquals(List.of(1, 1, 3), model.result().orderedEntries().stream().map(e -> e.rank().orElseThrow()).toList());
        assertEquals(2, model.highestTieCount());
        assertEquals(1, model.currentPosition().orElseThrow());
        assertTrue(model.currentHasCandidateRank());
        assertEquals("Highest / Current", model.marker(entries.getFirst()));
        assertEquals(2, model.result().orderedEntries().get(1).inputOrder());
        assertEquals(1, model.provenEquivalentCount(1), "A tied value is not a proof");
    }
    @Test void roundedCurrencyDoesNotCreateTie() {
        assertEquals(1, model(List.of(entry(1, "100.004", 1), entry(2, "100.003", 2)), Optional.empty()).highestTieCount());
    }
    @Test void noncandidateBaselineUsesMetricPosition() {
        var base = entry(0, "95", null);
        var other = REQUEST.candidates().get(10);
        base = new LongevityWeightedIntegratedStrategyComparisonEntry(0, other, base.aggregate(), base.failure(), base.scenarioDetails(),
                base.rank(), base.pvDifferenceFromBaseline(), base.nominalDifferenceFromBaseline());
        var model = model(List.of(entry(1, "100", 1), entry(2, "100", 1), entry(3, "90", 3)), Optional.of(base));
        assertFalse(model.currentHasCandidateRank());
        assertEquals(3, model.currentPosition().orElseThrow());
        assertEquals(new BigDecimal("5.26"), model.improvementPercent().orElseThrow());
    }
    @Test void zeroAndNegativeBaselinePercentagesAreUnavailable() {
        assertTrue(LongevityWeightedIntegratedPresentation.percent(BigDecimal.TEN, BigDecimal.ZERO).isEmpty());
        assertTrue(LongevityWeightedIntegratedPresentation.percent(BigDecimal.TEN, BigDecimal.ONE.negate()).isEmpty());
    }
    @Test void returnedProofMembershipIsSeparateFromTieAndNeverCollapsesOccurrences() {
        var original = model(List.of(entry(1, "100", 1), entry(2, "100", 1)), Optional.empty()).result();
        var proof = new LongevityWeightedStrategyEquivalencePlanner.Plan(List.of(List.of(1, 2)), List.of(1, 1), 0, 0, Duration.ZERO);
        var result = new LongevityWeightedIntegratedStrategyComparisonResult(original.objective(), original.metadata(),
                original.orderedEntries(), original.rankedSuccessfulEntries(), original.baseline(), original.work(),
                original.elapsedTime(), original.status(), Optional.of(proof), 1);
        var presentation = new LongevityWeightedIntegratedPresentation(result, 7, 9);
        assertEquals(2, presentation.provenEquivalentCount(2));
        assertEquals(2, presentation.highestTieCount());
        assertEquals(List.of(1, 2), presentation.result().orderedEntries().stream().map(e -> e.inputOrder()).toList());
    }

    @Test void mixedFailureRetainsSuccessfulRankingAndBackendDollarDifference() {
        var successful = entry(1, "100", 1);
        successful = new LongevityWeightedIntegratedStrategyComparisonEntry(1, successful.strategy(), successful.aggregate(),
                successful.failure(), successful.scenarioDetails(), successful.rank(), Optional.of(new BigDecimal("25")), Optional.empty());
        var strategy = REQUEST.candidates().get(1);
        var failed = new LongevityWeightedIntegratedStrategyComparisonEntry(2, strategy, Optional.empty(),
                Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(strategy, "Unavailable",
                        IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)), Optional.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.empty());
        var result = model(List.of(successful, failed), Optional.empty());
        assertEquals(1, result.result().failedStrategyCount());
        assertEquals(new BigDecimal("25"), result.highest().orElseThrow().pvDifferenceFromBaseline().orElseThrow());
        assertTrue(result.result().orderedEntries().get(1).rank().isEmpty());
    }
    @Test void allFailedHasNoHighestAndNoFabricatedRank() {
        var failure = new IntegratedSocialSecurityStrategyEvaluationFailure(REQUEST.candidates().getFirst(), "Unavailable",
                IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION);
        var entry = new LongevityWeightedIntegratedStrategyComparisonEntry(1, REQUEST.candidates().getFirst(), Optional.empty(),
                Optional.of(failure), Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
        var model = model(List.of(entry), Optional.empty());
        assertTrue(model.highest().isEmpty());
        assertEquals(1, model.result().failedStrategyCount());
        assertTrue(model.currentPosition().isEmpty());
    }
}
