package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Existing completed synthetic presentation fixtures; no production search is launched. */
public final class SnapshotFixtures {
    private SnapshotFixtures() { }
    public static final String HASH = "a".repeat(64);
    public static SnapshotContext context() {
        var c = IntegratedAnalyzerPdfReportTest.context(true);
        var b = CurrentStrategyBaseline.fromPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
        var rows = IntegratedAnalysisComparisonPresentation.create(new LongevityWeightedIntegratedPresentation(couple().couple().restore(), 7, 9), null, -1, 7, true);
        return new SnapshotContext(c.household(), c.primary(), c.spouse(), Instant.parse("2026-01-01T00:00:00Z"),
                c.assumptions().stream().map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(),
                List.of(new SnapshotContext.Section("Baseline", List.of(c.baseline()))),
                List.of(new SnapshotContext.Section("Historical comparison", List.of("Unavailable at completion"))),
                Map.of("metric", "EXPECTED_PV_AFTER_TAX_ESTATE", "occurrence", "2"),
                new SnapshotContext.Baseline(b.elections().stream().map(e -> new SnapshotContext.Election(e.name(), e.value(), e.source())).toList(),
                        b.strategy().map(WeightedSnapshots.Strategy::from).orElse(null), b.problem()),
                List.of(new SnapshotContext.HistoricalComparison(AnalysisKind.COUPLE_EXHAUSTIVE, null, null,
                        "No compatible deterministic result existed at completion", rows.stream().map(AnalysisSnapshotPresentationMapper::capture).toList())));
    }
    public static SnapshotContext individualContext(com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) {
        var c = IndividualReportContext.capture(plan);
        return new SnapshotContext(c.household(), c.primary(), null, c.started(), c.assumptions().stream()
                .map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), Map.of());
    }
    public static WeightedSnapshots.Payload couple() {
        var first = LongevityWeightedIntegratedPresentationTest.entry(1, "100.00400", 1);
        var second = LongevityWeightedIntegratedPresentationTest.entry(2, "100.00400", 1);
        var failedStrategy = LongevityWeightedIntegratedPresentationTest.REQUEST.candidates().get(2);
        var failure = new LongevityWeightedIntegratedStrategyComparisonEntry(3, failedStrategy, Optional.empty(),
                Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(failedStrategy, "Required allocation exceeds eligible funds",
                        IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)), Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
        var date = LocalDate.of(2040, 1, 1);
        var detail = new LongevityWeightedIntegratedScenarioOutcome(2039, 2040, new BigDecimal("1.00"),
                new EstateAtSecondDeathSnapshot(date, date.minusDays(1), new BigDecimal("123.4500"), BigDecimal.ZERO, new BigDecimal("123.4500")),
                new BigDecimal("100.00400"));
        first = new LongevityWeightedIntegratedStrategyComparisonEntry(1, first.strategy(), first.aggregate(), first.failure(),
                Optional.of(List.of(detail)), first.rank(), Optional.of(new BigDecimal("0.0000")), Optional.empty());
        var model = LongevityWeightedIntegratedPresentationTest.model(List.of(first, second, failure),
                Optional.of(LongevityWeightedIntegratedPresentationTest.entry(0, "100.00400", null)));
        var r = model.result();
        var equivalence = new LongevityWeightedStrategyEquivalencePlanner.Plan(List.of(List.of(1), List.of(2), List.of(3)),
                List.of(1, 2, 3), 4, 3, Duration.ofNanos(1234), Optional.of(new LongevityEquivalencePlanningWork(4, 3, 2, 1, 1, 0, 100, 2, 2, 1, 0)));
        var result = new LongevityWeightedIntegratedStrategyComparisonResult(r.objective(), r.metadata(), r.orderedEntries(),
                r.rankedSuccessfulEntries(), r.baseline(), new LongevityWeightedIntegratedStrategyComparisonResult.Work(3, 4, 3, 3, 2,
                1, new LongevityContinuationWork(4, 2, 1, 1, 0, 1, 3, 2, 4, 3, 1, 42)), Duration.ofNanos(4567),
                LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED_WITH_FAILURES, Optional.of(equivalence), 2);
        return new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(result), null);
    }
    public static AnalysisSnapshot snapshot(UUID id) {
        var payload = couple(); var context = context();
        return new AnalysisSnapshot(1, 1, id, AnalysisKind.COUPLE_WEIGHTED, AnalysisKind.Shape.COUPLE, UUID.randomUUID(),
                Instant.parse("2026-01-01T01:00:00Z"), "test", Map.of("weighted", "v1"), Map.of("table", "v1"), HASH, HASH,
                new AnalysisSnapshotCodec().digest(payload, context), payload, context, null);
    }
    public static AnalysisSnapshot fullUniverseDtoOnly() {
        var template = couple().couple();
        var candidates = LongevityWeightedIntegratedPresentationTest.REQUEST.candidates();
        var entries = new ArrayList<WeightedSnapshots.Entry>();
        for (int i = 0; i < candidates.size(); i++) {
            var amount = new BigDecimal("1000000.0000").subtract(BigDecimal.valueOf(i));
            entries.add(new WeightedSnapshots.Entry(i + 1, WeightedSnapshots.Strategy.from(candidates.get(i)),
                    new LongevityWeightedStrategyAggregate(amount, amount, amount, amount, amount, BigDecimal.ONE, 2, 2),
                    null, null, i + 1, null, null));
        }
        var payload = new WeightedSnapshots.Payload(new WeightedSnapshots.Couple(template.objective(), template.metadata(),
                entries, java.util.stream.IntStream.rangeClosed(1, entries.size()).boxed().toList(), null, template.work(),
                Duration.ZERO, LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED, null, 0), null);
        var context = context();
        return new AnalysisSnapshot(1, 1, UUID.randomUUID(), AnalysisKind.COUPLE_WEIGHTED, AnalysisKind.Shape.COUPLE,
                UUID.randomUUID(), Instant.parse("2026-01-01T01:00:00Z"), "DTO-only-scale-fixture",
                Map.of("weighted", "v1"), Map.of("table", "fixture"), HASH, HASH,
                new AnalysisSnapshotCodec().digest(payload, context), payload, context, null);
    }
    public static AcceptedAnalysisCompletion completion(UUID id, Path file) {
        var s = snapshot(id);
        return new AcceptedAnalysisCompletion(id, file, s.kind(), s.runId(), s.completedAt(), s.inputFingerprint(),
                s.contextFingerprint(), s.algorithms(), s.references(), s.payload(), s.context());
    }
}
