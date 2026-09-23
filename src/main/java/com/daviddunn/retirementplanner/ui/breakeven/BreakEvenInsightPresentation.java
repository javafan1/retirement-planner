package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import java.util.Objects;

/** Pure presentation: no JavaFX, projection calls, or new break-even classification. */
public final class BreakEvenInsightPresentation {
    private BreakEvenInsightPresentation() { }

    public static String headline(BreakEvenAnalysisResult result, BreakEvenMetric selected) {
        var ss = result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY);
        var metric = result.metrics().get(selected == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY
                ? BreakEvenMetric.INVESTABLE_ASSETS : selected);
        String text;
        if (ss.sustainedBreakEvenYear() != null && metric.sustainedBreakEvenYear() != null) {
            int gap = metric.sustainedBreakEvenYear() - ss.sustainedBreakEvenYear();
            String name = BreakEvenPresentation.metricName(metric.metric());
            text = gap == 0 ? "Cumulative Social Security and " + name.toLowerCase(java.util.Locale.ROOT)
                    + " both reach sustained break-even in " + ss.sustainedBreakEvenYear() + "."
                    : name + (metric.metric() == BreakEvenMetric.INVESTABLE_ASSETS ? " reach" : " reaches")
                    + " sustained break-even in " + metric.sustainedBreakEvenYear() + ", "
                    + Math.abs(gap) + (Math.abs(gap) == 1 ? " year " : " years ")
                    + (gap > 0 ? "after" : "before") + " cumulative Social Security (" + ss.sustainedBreakEvenYear() + ").";
        } else {
            text = state(result, ss) + " " + state(result, metric);
        }
        for (var m : new BreakEvenMetricResult[]{ss, metric}) {
            if (m.sustainedBreakEvenYear() != null && m.firstCrossoverYear() != null
                    && !Objects.equals(m.firstCrossoverYear(), m.sustainedBreakEvenYear())) {
                text += " " + BreakEvenPresentation.metricName(m.metric()) + " first crosses in "
                        + m.firstCrossoverYear() + ", but stays at or above Baseline only from " + m.sustainedBreakEvenYear() + ".";
            }
        }
        return text;
    }

    private static String state(BreakEvenAnalysisResult result, BreakEvenMetricResult metric) {
        String name = BreakEvenPresentation.metricName(metric.metric());
        return name + ": " + switch (metric.status()) {
            case BREAK_EVEN_REACHED -> "sustained break-even in " + metric.sustainedBreakEvenYear() + ".";
            case CURRENT_ALREADY_AHEAD -> "Current starts at or above Baseline and remains so through " + result.comparisonEndYear() + ".";
            case IDENTICAL -> "plans are equal throughout the comparable period.";
            case NO_COMPARABLE_YEARS -> "no comparable years.";
            case CROSSOVER_NOT_SUSTAINED -> "first crossover in " + metric.firstCrossoverYear()
                    + ", but no sustained break-even through " + result.comparisonEndYear() + ".";
            case NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD -> "no sustained break-even; Current is "
                    + BreakEvenPresentation.money(metric.finalDifference().abs()) + " behind at " + result.comparisonEndYear() + ".";
        };
    }

    public static String snapshotHeading(BreakEvenAnalysisResult result, BreakEvenInsight insight) {
        return insight.referenceYear() == null ? "No comparable snapshot"
                : (result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).sustainedBreakEvenYear() != null
                ? "At SS sustained break-even (" : "At comparison end (") + insight.referenceYear() + ") — Current − Baseline";
    }

    public static String withdrawal(BreakEvenInsight insight) {
        return insight.observations().stream()
                .filter(value -> value.driver() == BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS)
                .map(value -> value.difference().signum() == 0
                        ? "Through " + insight.referenceYear() + ", gross portfolio withdrawals are equal in both plans."
                        : "Through " + insight.referenceYear() + ", Current gross portfolio withdrawals are "
                        + BreakEvenPresentation.money(value.difference().abs())
                        + (value.difference().signum() < 0 ? " lower" : " higher")
                        + " than Baseline (including RMDs and tax funding; excluding Roth transfers).")
                .findFirst().orElse("");
    }

    public static String driverName(BreakEvenInsight.Driver driver) {
        return switch (driver) {
            case GROSS_PORTFOLIO_WITHDRAWALS -> "Cumulative gross portfolio withdrawals";
            case INCOME_TAXES -> "Cumulative income taxes (federal + state)";
            case INVESTMENT_GROWTH -> "Cumulative investment growth";
        };
    }
}
