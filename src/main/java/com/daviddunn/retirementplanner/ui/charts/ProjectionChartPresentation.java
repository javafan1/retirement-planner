package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

final class ProjectionChartPresentation {
    private ProjectionChartPresentation() { }
    static String category(ProjectionAssetType type) {
        return switch (type) {
            case TAXABLE -> "Taxable / Cash";
            case TAX_DEFERRED -> "Tax-Deferred";
            case ROTH -> "Roth";
        };
    }
    static String periods(List<ProjectionChartModel.Period> periods) {
        return periods.stream().map(p -> p.firstYear() == p.lastYear() ? "" + p.firstYear()
                : p.firstYear() + "–" + p.lastYear()).collect(Collectors.joining(", "));
    }
    static String periodLabel(ProjectionChartModel.Period period, boolean roth) {
        return (roth ? (period.firstYear() == period.lastYear() ? "Roth Conversion" : "Roth Conversions") : "RMDs")
                + "\n" + periods(List.of(period));
    }
    static String tooltip(ProjectionChartModel.Point point, ProjectionChartMetric metric) {
        String text = point.year() + " · Primary age " + point.primaryAge() + "\n"
                + metric + ": " + UIFormatters.money(point.values().get(metric));
        if (metric == ProjectionChartMetric.INVESTABLE_ASSETS) {
            if (point.compositionComplete()) {
                for (var type : ProjectionAssetType.values()) text += "\n" + category(type) + ": "
                        + UIFormatters.money(point.composition().get(type)) + "  "
                        + point.percentage(type).setScale(1, RoundingMode.HALF_UP) + "%";
            } else text += "\nDetailed asset composition is unavailable for this projection.";
        }
        if (point.values().get(ProjectionChartMetric.ROTH_CONVERSIONS).signum() > 0)
            text += "\nActual Roth Conversion: " + UIFormatters.money(point.values().get(ProjectionChartMetric.ROTH_CONVERSIONS));
        if (point.values().get(ProjectionChartMetric.RMD).signum() > 0)
            text += "\nRMD distributed during projection: " + UIFormatters.money(point.values().get(ProjectionChartMetric.RMD));
        return text;
    }
}
