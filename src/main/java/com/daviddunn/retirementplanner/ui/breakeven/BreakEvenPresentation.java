package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public final class BreakEvenPresentation {
    private BreakEvenPresentation() { }

    public static String metricName(BreakEvenMetric metric) {
        return switch (metric) {
            case CUMULATIVE_SOCIAL_SECURITY -> "Cumulative Social Security Benefits";
            case INVESTABLE_ASSETS -> "Investable Assets";
            case TOTAL_NET_WORTH -> "Total Net Worth";
            case AFTER_TAX_ESTATE -> "After-Tax Estate";
        };
    }
    public static String summaryName(BreakEvenMetric metric) {
        return switch (metric) {
            case CUMULATIVE_SOCIAL_SECURITY -> "Social Security Benefit Break-Even";
            case INVESTABLE_ASSETS -> "Investable Asset Break-Even";
            case TOTAL_NET_WORTH -> "Total Net Worth Break-Even";
            case AFTER_TAX_ESTATE -> "After-Tax Estate Break-Even";
        };
    }
    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }
    public static String ages(BreakEvenYearResult point, BreakEvenPlanSummary plan) {
        return plan.primary().name() + " age " + age(point.primaryAge()) + " · "
                + plan.spouse().name() + " age " + age(point.spouseAge());
    }
    public static String age(Integer value) { return value == null ? "Unavailable" : value.toString(); }

    public static String signedMoney(BigDecimal value) {
        return value.signum() > 0 ? "+" + money(value) : money(value);
    }

    public static String probability(BreakEvenSurvivalPoint point) {
        return point.householdAtLeastOneAliveProbability().movePointRight(2)
                .setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public static String eventLabel(BreakEvenEvent event) {
        boolean sameAge = event.elections().stream().map(BreakEvenEvent.Election::claimingAge).distinct().count() == 1;
        if (sameAge) {
            return event.year() + "\n" + event.name() + " claims at " + event.elections().getFirst().claimingAge()
                    + "\n" + (event.elections().size() == 2 ? "Both plans" : scope(event.elections().getFirst().plan()));
        }
        return event.year() + "\n" + event.name() + "\n" + event.elections().stream()
                .map(e -> scope(e.plan()) + ": age " + e.claimingAge()).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String scope(BreakEvenEvent.PlanScope scope) {
        return scope == BreakEvenEvent.PlanScope.BASELINE ? "Baseline" : "Current";
    }

    public static BreakEvenYearResult sustainedPoint(BreakEvenMetricResult metric) {
        return metric.years().stream()
                .filter(point -> Objects.equals(metric.sustainedBreakEvenYear(), point.year()))
                .findFirst().orElse(null);
    }

    public static String compactAges(BreakEvenYearResult point, BreakEvenPlanSummary plan) {
        return plan.primary().name() + " " + age(point.primaryAge()) + " · "
                + plan.spouse().name() + " " + age(point.spouseAge());
    }

    public static String cardValue(BreakEvenMetricResult metric) {
        return switch (metric.status()) {
            case BREAK_EVEN_REACHED -> String.valueOf(metric.sustainedBreakEvenYear());
            case NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD -> "Not Reached";
            case CURRENT_ALREADY_AHEAD -> "Already Ahead";
            case IDENTICAL -> "No Difference";
            case CROSSOVER_NOT_SUSTAINED -> "No Sustained Break-Even";
            case NO_COMPARABLE_YEARS -> "No Comparable Years";
        };
    }

    public static String cardDetail(BreakEvenAnalysisResult result, BreakEvenMetricResult metric) {
        return switch (metric.status()) {
            case BREAK_EVEN_REACHED -> compactAges(sustainedPoint(metric), result.currentAssumptions())
                    + "\nAt or above Baseline through " + result.comparisonEndYear()
                    + (!Objects.equals(metric.firstCrossoverYear(), metric.sustainedBreakEvenYear())
                    ? "\nFirst crossover: " + metric.firstCrossoverYear() : "");
            case NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD -> "Current remains "
                    + money(metric.finalDifference().abs()) + " behind at " + result.comparisonEndYear();
            case CURRENT_ALREADY_AHEAD -> "Current begins at or above Baseline and remains so through " + result.comparisonEndYear();
            case IDENTICAL -> "Plans remain equal through " + result.comparisonEndYear();
            case CROSSOVER_NOT_SUSTAINED -> "First crossover: " + metric.firstCrossoverYear()
                    + "\nFalls below Baseline again";
            case NO_COMPARABLE_YEARS -> "No shared projection years to compare";
        };
    }

    public static String banner(BreakEvenAnalysisResult result, BreakEvenMetricResult metric) {
        String outcome = switch (metric.status()) {
            case BREAK_EVEN_REACHED -> "Break-even " + metric.sustainedBreakEvenYear();
            case NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD -> "No break-even through " + result.comparisonEndYear();
            case CURRENT_ALREADY_AHEAD -> "Current already ahead at start of comparison";
            case IDENTICAL -> "No difference";
            case CROSSOVER_NOT_SUSTAINED -> "No sustained break-even · First crossover " + metric.firstCrossoverYear();
            case NO_COMPARABLE_YEARS -> "No comparable years";
        };
        return metric.finalDifference() == null ? outcome
                : outcome + " · Current " + signedMoney(metric.finalDifference()) + " at " + result.comparisonEndYear();
    }

    public static String periodHelp() {
        return "Social Security totals accumulate only over comparable years. "
                + "Sustained break-even means at or above Baseline through comparison end, not beyond it.";
    }

    public static String summary(BreakEvenAnalysisResult analysis, BreakEvenMetricResult metric) {
        String text = switch (metric.status()) {
            case IDENTICAL -> "No material difference (identical values).";
            case CURRENT_ALREADY_AHEAD -> "Current is already at or above Baseline from the beginning of the comparison period.";
            case NO_COMPARABLE_YEARS -> "No comparable projection years.";
            case NO_BREAK_EVEN_WITHIN_COMPARABLE_PERIOD -> "Not reached within comparable period.";
            case CROSSOVER_NOT_SUSTAINED -> "First crossover: " + metric.firstCrossoverYear()
                    + ". Crossover was not sustained within the comparable period.";
            case BREAK_EVEN_REACHED -> {
                var point = metric.years().stream().filter(year -> year.year() == metric.sustainedBreakEvenYear())
                        .findFirst().orElseThrow();
                yield "Year " + point.year() + " · " + ages(point, analysis.currentAssumptions())
                        + ". Current remains at or above Baseline through " + analysis.comparisonEndYear() + "."
                        + (!Objects.equals(metric.firstCrossoverYear(), metric.sustainedBreakEvenYear())
                        ? " First crossover: " + metric.firstCrossoverYear() + "." : "");
            }
        };
        if (metric.finalDifference() != null && metric.finalDifference().signum() < 0) {
            text += " At comparison end, Current remains " + money(metric.finalDifference().abs()) + " behind Baseline.";
        }
        return text;
    }

    public static String period(BreakEvenAnalysisResult result) {
        if (!result.planningHorizonsDiffer() && result.comparableYearCount() > 0) {
            return "Comparison Period: " + range(result.comparisonStartYear(), result.comparisonEndYear())
                    + " · " + result.comparableYearCount()
                    + (result.comparableYearCount() == 1 ? " year" : " years");
        }
        String text = result.comparableYearCount() == 0 ? "No comparable years."
                : "Comparison Period: " + result.comparisonStartYear() + "–" + result.comparisonEndYear() + " · "
                + result.comparableYearCount() + (result.comparableYearCount() == 1 ? " comparable year" : " comparable years");
        text += "\nBaseline: " + range(result.baselineStartYear(), result.baselineEndYear())
                + "\nCurrent: " + range(result.currentStartYear(), result.currentEndYear());
        if (result.planningHorizonsDiffer()) {
            text += "\nPlanning horizons differ. Only calendar years available in both projections are compared.";
            if (result.baselineEndYear() != null && result.currentEndYear() != null) {
                if (result.baselineEndYear() < result.currentEndYear()) {
                    text += " Current continues beyond Baseline. A later break-even cannot be determined because Baseline ends in "
                            + result.baselineEndYear() + ".";
                } else if (result.currentEndYear() < result.baselineEndYear()) {
                    text += " Baseline continues beyond Current. A later break-even cannot be determined because Current ends in "
                            + result.currentEndYear() + ".";
                }
            }
        }
        return text;
    }
    private static String range(Integer first, Integer last) { return first == null ? "Unavailable" : first + "–" + last; }
}
