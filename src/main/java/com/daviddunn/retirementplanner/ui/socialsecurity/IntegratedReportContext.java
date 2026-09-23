package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import java.time.Instant;
import java.util.List;

/** Captured before a run; never reconstruct report assumptions from a later live plan. */
record IntegratedReportContext(String household, String primary, String spouse,
                               Instant runStarted, List<Section> assumptions, String baseline) {
    IntegratedReportContext { assumptions = List.copyOf(assumptions); }

    static IntegratedReportContext capture(RetirementPlan plan, boolean weighted,
            SocialSecurityAnalyzerInputSummary.AnalyzerValues values, CurrentStrategyBaseline baseline) {
        var rows = SocialSecurityAnalyzerInputSummary.from(plan, values, baseline).rows().stream()
                .filter(row -> weighted || (!row.input().toLowerCase(java.util.Locale.ROOT).contains("mortality")
                        && !row.input().contains("Weighted") && !row.input().contains("Real discount")
                        && !row.input().contains("Current Strategy Baseline")
                        && !row.source().equals("Analyzer")))
                .map(row -> row.input() + ": " + row.value() + " [" + row.source() + "]").toList();
        return new IntegratedReportContext(plan.getHousehold().getHouseholdName(),
                plan.getHousehold().getPrimaryPerson().getFullName(), plan.getHousehold().getSpouse().getFullName(),
                Instant.now(), List.of(new Section("Result-Time Plan and Analysis Assumptions", rows)),
                baseline.summary() + "\n" + baseline.problem());
    }
}
