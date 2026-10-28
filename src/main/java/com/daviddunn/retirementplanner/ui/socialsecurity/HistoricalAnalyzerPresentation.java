package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.time.Instant;
import java.util.*;

/** Pure reconstruction of saved report inputs. No plan, controller or calculation factory. */
final class HistoricalAnalyzerPresentation {
    interface ReportFactory {
        IntegratedAnalyzerReport capture(int age, ClaimingStrategyHeatMapMetric metric,
                ClaimingStrategyHeatMapCell cell, ClaimingAgeGridView.Pair pair);
    }
    record Prepared(ReportFactory reports, ClaimingStrategyHeatMapModel integrated,
            SocialSecurityClaimingGridAdapter.Model socialSecurity) { }

    static Prepared prepare(AnalysisSnapshot saved) {
        var c = saved.context();
        var individual = new IndividualReportContext(c.household(), c.primary(), c.startedAt(), c.assumptions().stream()
                .map(s -> new IntegratedAnalyzerReport.Section(s.title(), s.lines())).toList());
        if (saved.payload() instanceof CoupleSocialSecuritySnapshot ss) {
            var context = CoupleSocialSecuritySnapshotMapper.context(ss);
            var model = SocialSecurityStrategyAnalyzerPresentation.from(ss.restore());
            var session = SocialSecurityReportSession.restored(saved, context, model);
            return new Prepared((age, metric, cell, pair) -> SocialSecurityOnlyReportAdapter.capture(session, pair,
                    List.of("Original saved analyzer result for its recorded projection."), Instant.now()),
                    null, SocialSecurityClaimingGridAdapter.from(model));
        }
        if (saved.payload() instanceof QuickComparisonSnapshot) {
            var report = QuickSnapshotAdapter.report(saved);
            return new Prepared((age, metric, cell, pair) -> {
                var captured = QuickComparisonReportAdapter.capture(report, false, Instant.now(),
                        "Original saved Quick result. Immutable projection association; not a new optimization.");
                var status = new ArrayList<String>();
                status.add("Historical association validated for its original projection. Current editable inputs are not assessed or substituted.");
                captured.context().stream().filter(line -> !line.startsWith("Historical Analysis — Outdated")).forEach(status::add);
                return new IntegratedAnalyzerReport("Historical Quick Comparison — Original Saved Result", captured.household(), captured.runStarted(),
                        captured.exportedAt(), status, captured.selectedSummary(), captured.visualization(), captured.assumptions(), captured.rankedTables(), captured.results());
            }, null, null);
        }
        if (saved.payload() instanceof IndividualDeterministicSnapshot d) {
            var result = d.restore();
            return new Prepared((age, metric, cell, pair) -> IndividualAnalyzerReportAdapter.deterministic(individual, result, age), null, null);
        }
        if (saved.payload().individual() != null) {
            var result = saved.payload().individual().restore();
            return new Prepared((age, metric, cell, pair) -> IndividualAnalyzerReportAdapter.mortality(individual, result, age), null, null);
        }
        if (saved.payload() instanceof ExhaustiveSnapshot e) {
            var restored = ExhaustiveResultSnapshots.restore(e);
            var model = restored.model(); var map = DeterministicHeatMapAdapter.from(model);
            return new Prepared((age, metric, cell, pair) -> IntegratedAnalyzerReportAdapter.deterministic(restored.report(), model,
                    map, metric, cell, cell == null ? null : cell.strategy().flatMap(s -> model.result().entries().stream()
                            .filter(entry -> entry.generationOrder() == s.inputOrder()).findFirst()).orElse(null),
                    model.groups(), ExhaustiveResultSnapshots.crossReferenceText(restored.reference())), map, null);
        }
        var model = new LongevityWeightedIntegratedPresentation(saved.payload().couple().restore(), 0, 0);
        var map = LongevityWeightedHeatMapAdapter.from(model);
        var b = c.baseline();
        var baseline = new CurrentStrategyBaseline(b.elections().stream()
                .map(e -> new CurrentStrategyBaseline.Election(e.name(), e.value(), e.source())).toList(),
                Optional.ofNullable(b.strategy()).map(WeightedSnapshots.Strategy::restore), b.problem());
        var context = new IntegratedReportContext(c.household(), c.primary(), c.spouse(), c.startedAt(), individual.assumptions(),
                baseline.summary() + "\n" + Objects.toString(baseline.problem(), ""));
        var comparisons = c.historicalComparisons().stream().filter(v -> !v.explanation().equals("Frozen strategy-detail ranks"))
                .flatMap(v -> v.rows().stream()).map(AnalysisSnapshotPresentationMapper::restore).toList();
        var ranks = AnalysisSnapshotPresentationMapper.ranks(c.historicalComparisons().stream()
                .filter(v -> v.explanation().equals("Frozen strategy-detail ranks")).flatMap(v -> v.rows().stream()).toList());
        return new Prepared((age, metric, cell, pair) -> IntegratedAnalyzerReportAdapter.weighted(context, model, map, metric, cell,
                cell == null ? null : cell.strategy().flatMap(s -> model.result().orderedEntries().stream()
                        .filter(entry -> entry.inputOrder() == s.inputOrder()).findFirst()).orElse(null),
                model.result().rankedSuccessfulEntries(), comparisons, null, ranks), map, null);
    }
}
