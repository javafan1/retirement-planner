package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Formatting cached aggregates only. Called on FX; no reduction or financial calculations. */
public final class MonteCarloStrategyComparisonPresentation {
    public static final String PAIRED = "Both strategies are evaluated against the same simulated market, inflation, and longevity outcomes. Differences reflect the strategies rather than different random scenarios.";
    public static final String NOMINAL = "In Longevity-Adjusted mode, terminal differences are measured immediately before each simulated household's own second-death date. These nominal values may come from different calendar years across simulations; they are not discounted to a common date.";
    public static final String ANNUAL = "Current Plan minus Saved Baseline among simulations with comparable funded balances in each year.";
    public static final String AXIS = "Above $0: Current Plan has more investable assets. Below $0: Saved Baseline has more investable assets.";

    private MonteCarloStrategyComparisonPresentation() { }

    public record Metric(String name, MonteCarloPairedMetricSummary summary, boolean taxes) {
        public String median() { return summary.differencePercentiles().map(p -> money(p.p50())).orElse("Unavailable"); }
        public String mean() { return summary.meanDifference().map(MonteCarloStrategyComparisonPresentation::money).orElse("Unavailable"); }
        public String greater() { return probability(summary.greaterProbabilityAmongComparable()); }
        public String direction() {
            return taxes ? "Positive: Current Plan paid MORE modeled income tax; negative: Current Plan paid LESS."
                    : "Positive: Current Plan ended with MORE; negative: Current Plan ended with LESS.";
        }
        public String relations() {
            return (taxes ? "Probability Current Plan paid more / equal / less modeled income tax: "
                    : "Probability Current Plan has more / equal / less: ") + greater() + " / "
                    + probability(summary.equalProbabilityAmongComparable()) + " / "
                    + probability(summary.lessProbabilityAmongComparable())
                    + String.format(" · %,d comparable simulations", summary.sampleCount());
        }
        public String quantiles() {
            return summary.differencePercentiles().map(p -> "Min " + money(p.minimum()) + " · P10 " + money(p.p10())
                    + " · P25 " + money(p.p25()) + " · Median " + money(p.p50()) + " · P75 " + money(p.p75())
                    + " · P90 " + money(p.p90()) + " · Max " + money(p.maximum())).orElse("Percentiles unavailable: no both-completed simulations.");
        }
    }

    public static List<Metric> metrics(MonteCarloStrategyComparisonSummary s) {
        return List.of(new Metric("Investable Assets", s.terminalInvestableAssetsDifference(), false),
                new Metric("Total Net Worth", s.terminalNetWorthDifference(), false),
                new Metric("After-Tax Estate", s.terminalAfterTaxEstateDifference(), false),
                new Metric("Lifetime Modeled Income Taxes", s.lifetimeTaxesDifference(), true));
    }

    public static String money(BigDecimal value) {
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "−" : "") + UIFormatters.money(value.abs());
    }

    public static String probability(Optional<BigDecimal> value) {
        return value.map(v -> v.movePointRight(2).setScale(2, RoundingMode.HALF_UP) + "%").orElse("Unavailable");
    }

    public static String difference(BigDecimal value) {
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "−" : "")
                + value.abs().movePointRight(2).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + " percentage points";
    }

    public static String funding(MonteCarloPairedStateSummary s) {
        return "Current Plan  " + MonteCarloPresentation.fundingPercent(s.aFundingProbability(), s.aFailedCount(), s.aCompletedCount())
                + "     Saved Baseline  " + MonteCarloPresentation.fundingPercent(s.bFundingProbability(), s.bFailedCount(), s.bCompletedCount())
                + "     Current − Baseline  " + difference(s.fundingProbabilityDifference());
    }

    public static String states(MonteCarloPairedStateSummary s) {
        return state("Both completed", s.bothCompleted(), s.bothCompletedProbability()) + "     "
                + state("Current only completed", s.aCompletedBFailed(), s.aCompletedBFailedProbability()) + "     "
                + state("Baseline only completed", s.aFailedBCompleted(), s.aFailedBCompletedProbability()) + "     "
                + state("Both failed", s.bothFailed(), s.bothFailedProbability())
                + String.format("     Funding probabilities use all %,d requested paired worlds.", s.requestedCount());
    }

    private static String state(String label, int count, BigDecimal probability) {
        return String.format("%s: %,d (%s)", label, count, probability(Optional.of(probability)));
    }

    public static String denominator(MonteCarloStrategyComparisonSummary s) {
        return String.format("Financial differences below use the %,d of %,d simulations in which both strategies completed successfully.",
                s.pairedStates().bothCompleted(), s.requestedCount());
    }

    public static boolean smallSample(MonteCarloPairedAnnualResult year) {
        return year.comparableCount() > 0 && year.comparableCount() <= 100
                && (long) year.comparableCount() * 20 <= year.requestedWorldCount();
    }

    public static String warning(MonteCarloPairedAnnualResult year) {
        return smallSample(year) ? "Only " + year.comparableCount()
                + " simulations have comparable funded balances in this year. Percentiles may be unstable." : "";
    }

    public static String selectedYear(MonteCarloPairedAnnualResult y) {
        return y.year() + " — " + y.investableAssetsDifference().differencePercentiles()
                .map(p -> "Median " + money(p.p50()) + " · P10 " + money(p.p10()) + " · P90 " + money(p.p90()))
                .orElse("No comparable funded balances; percentiles unavailable")
                + String.format("\n%,d comparable of %,d requested · Current-only funded %,d · Baseline-only funded %,d · Both failed %,d · %,d deceased/not living",
                y.comparableCount(), y.requestedWorldCount(), y.aCompletedBFailedCount(), y.aFailedBCompletedCount(),
                y.bothFailedCount(), y.bothDeceasedCount());
    }

    public static String percentileHelp(String name) {
        if (name.equals("Median")) {
            return "Median/P50: Half of comparable simulations had a difference below this amount and half above (ties may occur).";
        }
        int p = Integer.parseInt(name.substring(1));
        return name + ": " + p + "% of comparable simulations had a Current-minus-Baseline difference at or below this amount; "
                + (100 - p) + "% were above it. Percentiles interpolate observations, not probabilities of an exact amount.";
    }
}
