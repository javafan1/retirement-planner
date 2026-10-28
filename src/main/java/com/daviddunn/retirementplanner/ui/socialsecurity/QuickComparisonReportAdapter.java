package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.time.Instant;
import java.util.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.AnalysisPdfData.*;

/** Pure table-only report over original accepted outputs. Never sorts or calculates metrics. */
final class QuickComparisonReportAdapter {
    static IntegratedAnalyzerReport capture(QuickReportSession session, boolean current, Instant exported) {
        return capture(session, current, exported, "Memory-only Quick result. This comparison is not persisted or restorable after closing the view or application.");
    }
    static IntegratedAnalyzerReport capture(QuickReportSession session, boolean current, Instant exported, String durability) {
        return capture(session, new AnalysisResultFreshness(new AnalysisCompatibilityPolicy.Assessment(
                current ? AnalysisCompatibilityPolicy.State.CURRENT : AnalysisCompatibilityPolicy.State.OUTDATED,
                "These results were generated using previous retirement-plan or analysis assumptions."),
                AnalysisResultFreshness.Association.UNRECORDED), exported, durability);
    }
    static IntegratedAnalyzerReport capture(QuickReportSession session,
            AnalysisResultFreshness freshness, Instant exported, String durability) {
        var prepared = session.prepared(); var financial = prepared.financial();
        var context = List.of(freshness.status(),
                durability,
                "Original analysis completed: " + session.completed(), "Original participants: " + financial.primary() + " / " + financial.spouse(),
                "Original SS prerequisite fingerprint: " + prepared.prerequisiteFingerprint(),
                "Original SS prerequisite run: " + prepared.prerequisiteRunId(), "Original Quick run: " + prepared.runId());
        var summary = new ArrayList<String>();
        long successful = session.rows().stream().filter(row -> row.outcome().isPresent()).count();
        summary.add("Requested candidates: " + prepared.requestedCount() + "; supplied: " + session.supplied()
                + "; unique: " + session.rows().size() + "; successful: " + successful + "; failed: " + (session.rows().size() - successful));
        summary.add("Original current-plan baseline (not recalculated)"); summary.addAll(elections(session.baseline().strategy()));
        var supplied = new ArrayList<List<String>>();
        for (int i = 0; i < prepared.candidates().size(); i++) {
            var candidate = prepared.candidates().get(i);
            supplied.add(List.of(Integer.toString(i + 1), Integer.toString(candidate.rank()),
                    candidate.strategy().primaryRetirementAge() + "/" + candidate.strategy().spouseRetirementAge(), money(candidate.value().expectedPresentValue())));
        }
        var elections = new ArrayList<List<String>>(); var values = new ArrayList<List<String>>(); var metrics = new ArrayList<List<String>>();
        var results = new ArrayList<Section>();
        results.add(new Section("Original Baseline Warnings and Method", warnings(session.baseline())));
        for (var row : session.rows()) {
            elections.add(electionRow(row.callerOrder(), row.ssRank(), row.strategy()));
            values.add(List.of(Integer.toString(row.callerOrder()), Integer.toString(row.ssRank()),
                    row.expected().map(value -> money(value.expectedPresentValue())).orElse("Unavailable"), row.outcome().isPresent() ? "Successful" : "Failed",
                    row.failure().map(f -> f.category() + ": " + f.message()).orElse("No candidate failure")));
            for (var metric : METRICS) metrics.add(List.of(Integer.toString(row.callerOrder()), metric.label(), money(metric.value().apply(session.baseline().metrics())),
                    row.outcome().map(value -> money(metric.value().apply(value.metrics()))).orElse("Unavailable — failed candidate"),
                    row.differences().map(value -> money(metric.value().apply(value))).orElse("Unavailable — failed candidate")));
            row.outcome().ifPresent(value -> results.add(new Section("Original Candidate " + row.callerOrder() + " Warnings and Method", warnings(value))));
        }
        results.add(new Section("Limitations", List.of("Candidates retain original Social Security rank/caller order and first-seen exact deduplication. No integrated winner, new rank or heat map is computed.",
                "SS expected PV is mortality-weighted and discounted; integrated metrics are deterministic future-dollar full-plan outcomes, not mortality-weighted estate values.",
                "Differences are original candidate-minus-baseline outputs. Failed candidates have no fabricated financial results. No analysis was rerun for export.")));
        var assumptions = new ArrayList<>(financial.assumptions()); assumptions.addAll(prepared.prerequisiteAssumptions());
        return new IntegratedAnalyzerReport(freshness.mismatch() ? "Historical Analysis — Outdated | Deterministic Quick Comparison" : "Deterministic Quick Comparison", financial.household(), financial.runStarted(), exported, context, summary,
                new TablesOnly("SS-ranked candidate comparison under original retirement-plan assumptions; matched to the original current-plan baseline, without an integrated optimization objective."),
                assumptions, List.of(new Table("Original Supplied Candidate Basis", List.of(new Column("Supplied order", 80, true), new Column("SS rank", 70, true), new Column("Retirement ages Primary/Spouse", 150, false), new Column("Original SS expected PV", 150, true)), supplied),
                        new Table("Compared Candidates — Complete Elections", electionColumns(), elections),
                        new Table("Compared Candidates — Status and Original SS Values", List.of(new Column("Caller order", 65, true), new Column("SS Rank", 55, true), new Column("SS Expected PV", 120, true), new Column("Status", 80, false), new Column("Original failure", 300, false)), values),
                        new Table("Original Financial Comparisons — All Fourteen Metrics", List.of(new Column("Caller order", 50, true), new Column("Financial metric", 170, false), new Column("Original baseline", 150, true), new Column("Candidate", 150, true), new Column("Candidate minus baseline", 150, true)), metrics)), results);
    }
    private static List<String> warnings(QuickReportSession.Outcome outcome) {
        var lines = new ArrayList<String>(); lines.add("Advanced Social Security projection path used: " + outcome.advancedSocialSecurity());
        lines.addAll(outcome.warnings().isEmpty() ? List.of("No retained warnings.") : outcome.warnings()); return List.copyOf(lines);
    }
}
