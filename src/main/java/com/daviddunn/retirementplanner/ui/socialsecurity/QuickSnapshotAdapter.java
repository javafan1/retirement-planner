package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import java.util.*;

/** Pure historical adapter. No plan, controls or financial factories. */
final class QuickSnapshotAdapter {
    static QuickComparisonSnapshot capture(IntegratedSocialSecurityComparisonPresentation model, QuickReportSession report) {
        var prepared = report.prepared();
        return QuickComparisonSnapshot.capture(model.comparison(), model.rows().stream().map(IntegratedSocialSecurityComparisonPresentation.Row::socialSecurityRank).toList(),
                prepared.candidates().stream().map(c -> new QuickComparisonSnapshot.Candidate(c.rank(), WeightedSnapshots.Strategy.from(c.strategy()), c.value())).toList(),
                prepared.prerequisiteFingerprint(), prepared.prerequisiteRunId(), prepared.requestedCount(), prepared.financial().baseline());
    }
    static IntegratedSocialSecurityComparisonPresentation presentation(QuickComparisonSnapshot payload) {
        var result = payload.restore();
        return new IntegratedSocialSecurityComparisonPresentation(result, java.util.stream.IntStream.range(0, result.entries().size())
                .mapToObj(i -> new IntegratedSocialSecurityComparisonPresentation.Row(payload.socialSecurityRanks().get(i), result.entries().get(i))).toList());
    }
    static QuickReportSession report(AnalysisSnapshot snapshot) {
        var payload = (QuickComparisonSnapshot) snapshot.payload(); var context = snapshot.context();
        var financial = new IntegratedReportContext(context.household(), context.primary(), context.spouse(), context.startedAt(),
                context.assumptions().stream().map(s -> new Section(s.title(), s.lines())).toList(),
                payload.baselineDescription());
        var prepared = new QuickReportSession.Prepared(snapshot.planId(), snapshot.runId(), financial, payload.prerequisiteFingerprint(),
                payload.prerequisiteRun(), payload.requested(), payload.candidates().stream()
                .map(c -> new QuickReportSession.Candidate(c.rank(), c.strategy().restore(), c.expected())).toList(),
                context.request().stream().map(s -> new Section(s.title(), s.lines())).toList());
        return QuickReportSession.accepted(prepared, presentation(payload), snapshot.completedAt());
    }
    static SnapshotContext context(QuickReportSession report) {
        var p = report.prepared(); var f = p.financial();
        return new SnapshotContext(f.household(), f.primary(), f.spouse(), f.runStarted(),
                f.assumptions().stream().map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(),
                p.prerequisiteAssumptions().stream().map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(),
                List.of(), Map.of());
    }
}
