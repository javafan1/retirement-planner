package com.daviddunn.retirementplanner.app.baseline;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionComparison;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

/** Detached view data only. No live plan, engine, repository or JavaFX controls. */
public record BaselineDetailsSession(String baselineDescription, LocalDateTime baselineCaptured,
        UUID currentRun, String originalPlanIdentity, Instant currentCompleted, String currentYears, String status,
        List<BaselineParameterComparison.Row> rows, ProjectionChartModel baselineChart) {
    public static final String CHART_TITLE = "Original Baseline Projection — Saved Results";
    public static final String CHART_UNAVAILABLE = "Original baseline chart unavailable. This baseline does not have verified original annual results. Rebuilt values are not displayed as historical results.";
    public BaselineDetailsSession { rows = List.copyOf(rows); }
    /** Exactly the seven existing Summary comparison deltas; no alternative financial objective. */
    public static boolean differs(ProjectionComparison c) {
        return c != null && Stream.of(c.getInvestmentGrowthChange(), c.getTotalIncomeChange(), c.getTotalTaxesChange(),
                c.getPeakAnnualTaxChange(), c.getEndingInvestableAssetsChange(), c.getNetWorthChange(), c.getAfterTaxEstateChange())
                .anyMatch(value -> value.signum() != 0);
    }
    public record Accepted(ProjectionInputs inputs, UUID runId, String planIdentity, Instant completedAt, long sourceRevision) { }
}
