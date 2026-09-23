package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionComparison;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import java.math.BigDecimal;
import java.util.Objects;

/** PDF-only result-time presentation inputs; no JavaFX nodes or calculations. */
public record ProjectionPdfReport(String planName, ProjectionChartModel charts,
        BigDecimal averageEffectiveTaxRate, BigDecimal afterTaxEstateHeirValue,
        ProjectionComparison baselineComparison) {
    public ProjectionPdfReport {
        Objects.requireNonNull(planName);
        Objects.requireNonNull(charts);
        Objects.requireNonNull(averageEffectiveTaxRate);
        Objects.requireNonNull(afterTaxEstateHeirValue);
    }
}
