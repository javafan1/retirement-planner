package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnnualResult;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import java.math.BigDecimal;
import java.util.List;

/** Session input validation and display formatting only; uses authoritative result aggregates. */
public final class MonteCarloMortalityPresentation {
    public static final String ADJUSTMENT_HELP =
            "Mortality risk multiplier: 1.00 means standard table mortality; 0.75 means 75% "
                    + "and 1.25 means 125% of the table's underlying mortality hazard. Annual death probabilities "
                    + "are adjusted through survival probabilities, not multiplied directly. This does not shorten "
                    + "or lengthen remaining life by that percentage. Above 1.00 increases modeled mortality; "
                    + "below 1.00 decreases it. Range: 0.50 to 3.00. Planning assumption only.";
    public static final String FUNDING_HELP = "Share of simulated market and lifetime scenarios that completed "
            + "all modeled obligations through the household's second death.";
    public static final String ANNUAL_NOTICE = "Annual percentile bands include only simulated households that are "
            + "still living and remain funded through that year. Use Left/Right, Home/End to inspect years.";
    public static final String NOMINAL_NOTICE = "Terminal values are nominal future dollars measured immediately "
            + "before second death in each simulated household lifetime; dates vary. Lifetime Taxes is the nominal lifetime total.";
    public static final String NO_TERMINALS = "No simulations funded all modeled obligations through second death, "
            + "so no terminal outcome distribution is available.";
    public static final String SMALL_SAMPLE_NOTICE =
            "Small surviving sample — percentile estimates may be less stable.";

    /** Display heuristic: roughly ten observations per decile and a substantially reduced population. */
    public static boolean smallSample(MonteCarloMortalityAnnualResult annual) {
        int completed = annual.completedLivingYearSampleCount();
        return completed > 0 && completed <= 100 && (long) completed * 20 <= annual.requestedWorldCount();
    }

    /** Shared by the visible summary, tooltip and chart accessibility; no financial computation. */
    public static String selectedYear(MonteCarloFanModel model, MonteCarloFanModel.Year year) {
        var annual = year.population().orElseThrow();
        String values = year.percentiles().map(p -> "Median " + UIFormatters.money(p.p50())
                + " · P10 " + UIFormatters.money(p.p10()) + " · P90 " + UIFormatters.money(p.p90()))
                .orElse("Median unavailable · P10 unavailable · P90 unavailable");
        return year.calendarYear() + " — " + values + String.format(
                "\n%,d living of %,d · %,d funded through %d · %,d both alive · %,d %s only · %,d %s only",
                annual.livingHouseholdCount(), annual.requestedWorldCount(), annual.completedLivingYearSampleCount(),
                year.calendarYear(), annual.bothAliveCount(), annual.primaryOnlyAliveCount(), model.primaryName(),
                annual.spouseOnlyAliveCount(), model.spouseName())
                + (smallSample(annual) ? " · " + SMALL_SAMPLE_NOTICE : "");
    }

    private MonteCarloMortalityPresentation() {
    }

    public static LongevitySessionSettings settings(RetirementPlan plan, String primary, String spouse) {
        return new LongevitySessionSettings(plan.getPlanningAssumptions().getProjectionStartDate(),
                adjustment(primary, "Primary"), adjustment(spouse, "Spouse"));
    }

    private static SocialSecurityMortalityAdjustment adjustment(String text, String owner) {
        try {
            var value = new BigDecimal(text.trim());
            if (value.compareTo(new BigDecimal("0.50")) >= 0 && value.compareTo(new BigDecimal("3.00")) <= 0) {
                return SocialSecurityMortalityAdjustment.of(value);
            }
        } catch (NumberFormatException ignored) {
            // Report the same supported UI range as the longevity analyzer.
        }
        throw new IllegalArgumentException(owner + " mortality adjustment must be a number from 0.50 to 3.00.");
    }

    public static int survivorClaimingAge(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Select a survivor Social Security claiming age for longevity-adjusted Monte Carlo.");
        }
        int age;
        try {
            age = Integer.parseInt(text.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Survivor Social Security claiming age must be a whole year of at least 60.");
        }
        com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy.validateClaimingAge(age);
        return age;
    }

    public static String fundingDetail(MonteCarloMortalityAnalysisResult result) {
        return String.format("%,d of %,d simulations funded all modeled obligations through second death.",
                result.completedCount(), result.requestedSimulationCount());
    }

    public static String terminalDates(MonteCarloMortalityAnalysisResult result) {
        return result.earliestSuccessfulTerminalBalanceDate().map(first -> "Successful terminal dates: "
                + first + "–" + result.latestSuccessfulTerminalBalanceDate().orElseThrow())
                .orElse("No funded lifetime simulations produced terminal outcomes.");
    }

    public record TerminalRow(String percentile, List<String> values) {
        public TerminalRow {
            values = List.copyOf(values);
        }
    }

    public static List<TerminalRow> terminalRows(MonteCarloMortalityAnalysisResult result) {
        if (result.completedCount() == 0) {
            return List.of();
        }
        var columns = List.of(result.endingInvestableAssets(), result.endingNetWorth(),
                        result.afterTaxEstate(), result.lifetimeTaxes()).stream()
                .map(value -> MonteCarloPresentation.quantiles(value.orElseThrow())).toList();
        var names = List.of("Min", "P10", "P25", "Median", "P75", "P90", "Max");
        return java.util.stream.IntStream.range(0, names.size()).mapToObj(index ->
                new TerminalRow(names.get(index), columns.stream()
                        .map(column -> UIFormatters.money(column.get(index))).toList())).toList();
    }

    public record Detail(String title, String text) {
    }

    public static List<Detail> details(MonteCarloRun run) {
        var result = run.mortalityResult();
        var settings = result.request().settings();
        var longevity = result.request().longevityAssumptions();
        return List.of(
                new Detail("Simulation", String.format("%,d simulations · seed %d · expected return %s · volatility %s. ",
                        settings.simulationCount(), settings.seed(), UIFormatters.percent(settings.expectedReturn()),
                        UIFormatters.percent(settings.returnVolatility()))
                        + "Independent lognormal gross returns. Adjustments are session-only, not saved in the plan."),
                new Detail("Longevity", run.people().primary().name() + ": " + longevity.primaryCategory()
                        + " × " + longevity.primaryAdjustment().factor() + "; " + run.people().spouse().name()
                        + ": " + longevity.spouseCategory() + " × " + longevity.spouseAdjustment().factor()
                        + ". " + longevity.tableMetadata().displayName() + " / " + longevity.tableMetadata().sourceVersion()
                        + " (" + longevity.tableMetadata().tableId() + "). Conditioning date: " + longevity.mortalityBaseDate()
                        + "; complete birthday intervals. Annual financial deaths occur January 1. "
                        + "Survivor Social Security claiming age: " + result.request().survivorClaimingAge().orElseThrow()
                        + " (shared analysis assumption; saved plan elections unchanged)."),
                new Detail("Funding", "Requested: " + result.requestedSimulationCount()
                        + " · completed lifetime simulations: " + result.completedCount()
                        + " · funding-failed simulations: " + result.fundingFailureCount()
                        + " · Lifetime Funding Probability: " + MonteCarloPresentation.fundingPercent(
                                result.fundingProbability(), result.fundingFailureCount(), result.completedCount())
                        + ". " + FUNDING_HELP + " Constraints can include owner/account restrictions, not only living expenses."),
                new Detail("Annual chart", ANNUAL_NOTICE + " Values compare the same calendar year. "
                        + "Late-year samples can become small and compositionally different from earlier years. "
                        + "The small-sample notice means 1–100 completed households and at most 5% of requested simulations; "
                        + "it is a display heuristic, not a confidence bound. The annual sample count changes; no zeros are inserted for "
                        + "deceased or failed households. Both-alive and survivor counts are empirical simulation counts. "
                        + "The deterministic projection and its SS claiming markers, Roth conversion shading and RMD shading "
                        + "are hidden because they use configured plan lifetimes, not sampled lifetimes."),
                new Detail("Terminal outcomes", "Conditional on funding through second death; failed worlds are not inserted as zeros. "
                        + terminalDates(result) + ". " + NOMINAL_NOTICE
                        + " Values are neither present-value nor constant-dollar adjusted and are not measured in one common year. "
                        + "After-Tax Estate excludes non-investable assets. Lifetime Taxes includes federal and Michigan income taxes."),
                new Detail("Model limitations", "Market returns and mortality are independent in this version. Primary/spouse mortality "
                        + "uses the existing independent household mortality model. Deceased-owner accounts remain household assets; "
                        + "there is no inherited-account retitling or beneficiary distribution model. "
                        + "General spending inflation uses the selected mode; Social Security COLA remains deterministic."));
    }
}
