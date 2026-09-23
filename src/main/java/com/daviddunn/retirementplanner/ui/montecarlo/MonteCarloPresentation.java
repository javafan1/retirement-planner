package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Display formatting only. Invoked on the FX thread because UIFormatters is shared.
 */
public final class MonteCarloPresentation {
    public static final String CONDITIONAL_NOTICE =
            "Ending financial distributions include only simulations that funded the complete planning horizon.";

    public record Outcome(String name, Optional<MonteCarloPercentiles> percentiles) {
    }

    private MonteCarloPresentation() {
    }

    public static String fundingPercent(MonteCarloAnalysisResult result) {
        BigDecimal percent = result.fundingProbability().movePointRight(2);
        BigDecimal rounded = percent.setScale(1, RoundingMode.HALF_UP);
        // Do not display 100% with failures, or 0% with a small positive completed count.
        if ((rounded.compareTo(new BigDecimal("100")) == 0 && result.fundingFailureCount() > 0)
                || (rounded.signum() == 0 && result.completedCount() > 0)) {
            rounded = percent.setScale(2, RoundingMode.HALF_UP);
        }
        return rounded + "%";
    }

    public static String fundingDetail(MonteCarloAnalysisResult result) {
        if (result.fundingFailureCount() == 0) {
            return String.format("All %,d simulations funded the complete planning horizon.", result.settings().simulationCount());
        }
        return String.format("%,d of %,d simulations funded the complete planning horizon.",
                result.completedSimulationCount(), result.settings().simulationCount());
    }

    public static String failureDetail(MonteCarloAnalysisResult result) {
        return result.fundingFailureStatistics().map(statistics ->
                String.format("%,d simulations encountered a funding constraint.  First-failure years: ",
                        statistics.count()) + "Earliest " + year(statistics.firstShortfallYear().minimum())
                        + " · Median " + year(statistics.firstShortfallYear().p50())
                        + " · Latest " + year(statistics.firstShortfallYear().maximum())).orElse("");
    }

    private static String year(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static String people(BreakEvenPlanSummary people, int lastYear) {
        String elections = List.of(people.primary(), people.spouse()).stream()
                .filter(person -> person.retirementClaimingAge() != null)
                .map(person -> person.name() + " SS " + person.retirementClaimingAge())
                .collect(Collectors.joining(" · "));
        return (elections.isBlank() ? "" : elections + " · ") + "Planning Horizon " + lastYear;
    }

    public static String periods(List<ProjectionChartModel.Period> periods) {
        return periods.isEmpty() ? "None in reference" : periods.stream()
                .map(period -> period.firstYear() == period.lastYear() ? "" + period.firstYear()
                        : period.firstYear() + "–" + period.lastYear())
                .collect(Collectors.joining(", "));
    }

    public static List<Outcome> outcomes(MonteCarloAnalysisResult result) {
        return List.of(new Outcome("Investable Assets", result.endingInvestableAssets()),
                new Outcome("Total Net Worth", result.endingNetWorth()),
                new Outcome("After-Tax Estate", result.endingAfterTaxEstate()),
                new Outcome("Lifetime Taxes", result.lifetimeTaxes()));
    }

    public static List<BigDecimal> quantiles(MonteCarloPercentiles values) {
        return List.of(values.minimum(), values.p10(), values.p25(), values.p50(),
                values.p75(), values.p90(), values.maximum());
    }

    public static String tooltip(MonteCarloFanModel model, MonteCarloFanModel.Year year) {
        StringBuilder text = new StringBuilder("Calendar Year: " + year.calendarYear());
        if (year.percentiles().isPresent()) {
            var values = year.percentiles().orElseThrow();
            text.append("\nP90: ").append(UIFormatters.money(values.p90()))
                    .append("\nP75: ").append(UIFormatters.money(values.p75()))
                    .append("\nMedian: ").append(UIFormatters.money(values.p50()))
                    .append("\nP25: ").append(UIFormatters.money(values.p25()))
                    .append("\nP10: ").append(UIFormatters.money(values.p10()));
        } else {
            text.append("\nAnnual percentiles: unavailable");
        }
        text.append("\nDeterministic Projection: ")
                .append(year.deterministic().map(UIFormatters::money).orElse("Unavailable"))
                .append("\nSimulations reaching this year: ")
                .append(year.percentiles().map(MonteCarloPercentiles::sampleCount).orElse(0))
                .append(" of ").append(model.requested());
        model.context().claims().stream().filter(claim -> claim.year() == year.calendarYear()).forEach(claim ->
                text.append("\n").append(claim.person()).append(" claims at ").append(claim.age()));
        model.context().years().stream().filter(point -> point.year() == year.calendarYear()).findFirst().ifPresent(point -> {
            text.append("\nPrimary age: ").append(point.primaryAge());
            for (var metric : List.of(ProjectionChartMetric.ROTH_CONVERSIONS, ProjectionChartMetric.RMD)) {
                if (point.values().get(metric).signum() > 0) {
                    text.append("\nDeterministic ").append(metric).append(": ")
                            .append(UIFormatters.money(point.values().get(metric)));
                }
            }
        });
        return text.toString();
    }
}
