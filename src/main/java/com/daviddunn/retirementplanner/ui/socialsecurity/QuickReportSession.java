package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.time.Instant;
import java.util.*;

/** Detached compact report graph: no plan, Projection, caches, mortality tables or controls. */
record QuickReportSession(Prepared prepared, Outcome baseline, int supplied, List<Row> rows, Instant completed) {
    record Candidate(int rank, SocialSecurityHouseholdClaimingStrategy strategy, SocialSecurityMortalityWeightedStrategyValue value) { }
    record Prepared(UUID planId, UUID runId, IntegratedReportContext financial, String prerequisiteFingerprint,
                    UUID prerequisiteRunId, int requestedCount, List<Candidate> candidates, List<Section> prerequisiteAssumptions) {
        Prepared { candidates = List.copyOf(candidates); prerequisiteAssumptions = List.copyOf(prerequisiteAssumptions); }
    }
    record Outcome(SocialSecurityHouseholdClaimingStrategy strategy, ProjectionMetrics metrics,
                   boolean advancedSocialSecurity, List<String> warnings) {
        Outcome { warnings = List.copyOf(warnings); }
        static Outcome from(IntegratedSocialSecurityStrategyResult result) {
            return new Outcome(result.evaluatedStrategy(), result.metrics(), result.advancedSocialSecurityPathUsed(), result.warnings());
        }
    }
    record Row(int callerOrder, int ssRank, SocialSecurityHouseholdClaimingStrategy strategy,
               Optional<SocialSecurityMortalityWeightedStrategyValue> expected,
               Optional<Outcome> outcome, Optional<ProjectionMetrics> differences,
               Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure) { }
    QuickReportSession { rows = List.copyOf(rows); }
    static QuickReportSession accepted(Prepared prepared, IntegratedSocialSecurityComparisonPresentation model, Instant completed) {
        return new QuickReportSession(prepared, Outcome.from(model.comparison().currentPlanBaseline()), model.comparison().suppliedCandidateCount(),
                model.rows().stream().map(row -> {
                    var entry = row.entry();
                    return new Row(entry.callerOrder(), row.socialSecurityRank(), entry.strategy(), entry.socialSecurityOnlyExpectedValue(),
                            entry.integratedResult().map(Outcome::from), entry.differencesFromCurrentPlan(), entry.failure());
                }).toList(), completed);
    }
}
