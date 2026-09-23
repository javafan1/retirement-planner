package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import java.util.List;
import java.util.Objects;

/** Completed, immutable inputs. Deliberately has no selected metric or UI state. */
public record BreakEvenPdfReport(BreakEvenAnalysisResult analysis, BreakEvenContext context,
        BreakEvenInsight insight) {
    public BreakEvenPdfReport {
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(context);
        Objects.requireNonNull(insight);
    }

    public List<BreakEvenMetricResult> charts() {
        return java.util.Arrays.stream(BreakEvenMetric.values()).map(metric -> analysis.metrics().get(metric)).toList();
    }
}
