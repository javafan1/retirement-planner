package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport;
import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport.*;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** FX-thread formatting of frozen completed runs only. Never reads controls or live plans. */
public final class MonteCarloPdfReportAdapter {
    private static final List<String> METRICS = List.of("Investable Assets", "Total Net Worth", "After-Tax Estate", "Lifetime Modeled Income Taxes");
    private static final String EXPECTED = "Expected return is the arithmetic mean annual return used to generate simulated yearly returns. With volatility, the median compounded outcome will generally differ from a deterministic projection using the same percentage.";
    private static final String PERCENTILES = "P10 is the value at or below which 10% of the applicable simulations fall. Median/P50 divides the applicable simulations in half. P90 is the value at or below which 90% fall. The existing Type-7 percentiles interpolate observations; no percentiles are recalculated for this report.";
    private static final String ANNUAL = "Annual percentiles are calculated from households still living and successfully funded through each year. The sample population therefore changes over time.";
    private static final String SMALL = "Very late years may contain few surviving households and unstable percentile estimates. A small-sample flag means 1–100 funded/comparable samples and at most 5% of requested simulations. No deceased or failed household is inserted as a zero financial sample.";

    private MonteCarloPdfReportAdapter() { }

    public static MonteCarloPdfReport from(MonteCarloRun run) {
        Objects.requireNonNull(run);
        boolean couple = run.people().hasSpouse();
        boolean mortality = run.mode() == MonteCarloMode.LONGEVITY_ADJUSTED;
        var sections = new ArrayList<Section>();
        var context = new ArrayList<>(settings(run.settings()));
        context.addAll(Arrays.asList(run.planDetails().split("\n")));
        List<Optional<MonteCarloPercentiles>> metrics;
        Optional<FundingFailureStatistics> failures;
        long completed;
        if (mortality) {
            var r = run.mortalityResult(); completed = r.completedCount(); failures = r.fundingFailureStatistics();
            if (couple) {
                var longevity = r.request().longevityAssumptions();
                context.add("Mortality categories: Primary " + longevity.primaryCategory() + "; Spouse " + longevity.spouseCategory()
                        + " | Conditioning date: " + longevity.mortalityBaseDate());
                context.add("Longevity adjustments: Primary " + longevity.primaryAdjustment().factor() + "; Spouse " + longevity.spouseAdjustment().factor()
                        + " | Survivor Social Security claiming age: " + r.request().survivorClaimingAge().orElseThrow());
            } else {
                var primary = r.request().primary();
                context.add("Primary mortality category: " + primary.mortalityCategory() + " | Conditioning date: " + primary.mortalityBaseDate());
                context.add("Primary Longevity Factor: " + primary.mortalityAdjustment().factor());
            }
            sections.add(section("Funding reliability", "Lifetime funding probability: " + MonteCarloPresentation.fundingPercent(r.fundingProbability(), r.fundingFailureCount(), completed),
                    MonteCarloMortalityPresentation.fundingDetail(r)));
            metrics = List.of(r.endingInvestableAssets(), r.endingNetWorth(), r.afterTaxEstate(), r.lifetimeTaxes());
        } else {
            var r = run.result(); completed = r.completedCount(); failures = r.fundingFailureStatistics();
            sections.add(section("Funding reliability", "Funding probability: " + MonteCarloPresentation.fundingPercent(r), MonteCarloPresentation.fundingDetail(r),
                    "Requested simulations: " + run.settings().simulationCount() + "; completed: " + completed + "; funding failures: " + r.fundingFailureCount()));
            metrics = List.of(r.endingInvestableAssets(), r.endingNetWorth(), r.endingAfterTaxEstate(), r.lifetimeTaxes());
        }
        sections.add(new Section("Terminal financial outcomes", List.of(
                "Conditional on " + count(completed) + " successfully completed simulations of " + count(run.settings().simulationCount()) + " requested. Failed outcomes are not replaced with zeros.",
                mortality ? MonteCarloMortalityPresentation.lifetimeText(MonteCarloStrategyComparisonPresentation.NOMINAL.replace("terminal differences", "terminal values"), couple)
                        : "Terminal values use the common planning horizon ending " + run.lastYear() + ". Values are nominal future dollars; taxes are lifetime totals."),
                List.of(distributions(metrics, false, List.of()))));
        if (mortality) {
            var r = run.mortalityResult();
            if (couple) {
                var rows = r.annualResults().values().stream().map(a -> List.of("" + a.year(), count(a.requestedWorldCount()), count(a.livingHouseholdCount()),
                        count(a.completedLivingYearSampleCount()), count(a.bothAliveCount()), count(a.primaryOnlyAliveCount()), count(a.spouseOnlyAliveCount()),
                        count(a.livingFundingFailedByYearCount()), count(a.bothDeceasedCount()), MonteCarloMortalityPresentation.smallSample(a) ? "Small" : "")).toList();
                sections.add(new Section("Annual living population", List.of(ANNUAL, "Funded/sample is both the completed-living count and annual percentile sample count. Failed living excludes normal post-second-death absence.", SMALL),
                        List.of(new Table(List.of("Year", "Requested", "Living", "Funded / sample", "Both alive", "Primary only", "Spouse only", "Failed living", "Deceased", "Sample flag"), rows,
                                List.of(42, 59, 48, 62, 55, 55, 55, 55, 55, 54)))));
            } else {
                var rows = r.annualResults().values().stream().map(a -> List.of("" + a.year(), count(a.requestedWorldCount()),
                        count(a.livingHouseholdCount()), count(a.completedLivingYearSampleCount()), count(a.livingFundingFailedByYearCount()),
                        count(a.deceasedCount()), MonteCarloMortalityPresentation.smallSample(a) ? "Small" : "")).toList();
                sections.add(new Section("Annual living population", List.of(ANNUAL, SMALL), List.of(new Table(
                        List.of("Year", "Requested", "Living", "Funded / sample", "Failed living", "Deceased", "Sample flag"), rows,
                        List.of(50, 80, 80, 95, 85, 75, 75)))));
            }
            sections.add(new Section("Terminal dates and analysis details", List.of(MonteCarloMortalityPresentation.terminalDates(r),
                    MonteCarloMortalityPresentation.lifetimeText("Terminal-year counts use the actual successful balance-date year. Opening-date second death uses opening assets; no annual financial row is fabricated.", couple),
                    "Mortality model: " + r.mortalityModel(),
                    "Mortality table: " + r.request().mortalityTable().metadata()), List.of(new Table(List.of("Successful terminal year", "Completed simulations"),
                    r.successfulTerminalYearCounts().entrySet().stream().map(e -> List.of("" + e.getKey(), count(e.getValue()))).toList(), List.of(270, 270)))));
        }
        sections.add(annualValues(run.fan().years().stream().map(y -> new Point(y.calendarYear(), y.percentiles(), y.deterministic())).toList(), false, run.settings().simulationCount()));
        failures(sections, "Funding failure statistics", failures);
        sections.add(section("Analysis assumptions and interpretation", "Return model: " + (mortality ? run.mortalityResult().returnModel() : run.result().returnModel()),
                EXPECTED, PERCENTILES, inflationNotes(run.settings()),
                "After-Tax Estate excludes non-investable assets. Lifetime Modeled Income Taxes includes federal and Michigan income taxes.",
                mortality ? "The deterministic projection, SS markers and Roth/RMD shading are omitted, as in the longevity UI: configured lifetimes are not sampled lifetimes. Terminal values are not discounted to a common valuation date."
                        : "The deterministic line, SS markers and Roth/RMD shading use the frozen configured plan reference, not a simulated mean. "
                        + (run.referenceIncomplete() ? "The reference encountered a funding constraint; only its completed prefix is shown." : "The reference completed its configured horizon.")));
        var fan = run.fan();
        var periods = new ArrayList<Period>();
        fan.context().rothPeriods().forEach(p -> periods.add(new Period(p.firstYear(), p.lastYear(), "Roth")));
        fan.context().rmdPeriods().forEach(p -> periods.add(new Period(p.firstYear(), p.lastYear(), "RMD")));
        var chart = new Chart("Investable Assets Over Time", mortality ? ANNUAL : "Annual percentiles include simulations successfully funded through each year; the sample count can decline.", false,
                fan.years().stream().map(y -> new Point(y.calendarYear(), y.percentiles(), y.deterministic())).toList(),
                fan.context().claims().stream().map(c -> new Marker(c.year(), c.person() + " SS age " + c.age() + " (" + c.year() + ")")).toList(), periods);
        return new MonteCarloPdfReport("Monte Carlo Retirement Analysis", mortality ? "Longevity-Adjusted" : "Fixed Lifespan",
                mortality ? "monte-carlo-longevity.pdf" : "monte-carlo-fixed.pdf", Instant.now(), context, chart, sections);
    }

    public static MonteCarloPdfReport from(MonteCarloStrategyComparisonRun run) {
        Objects.requireNonNull(run);
        var r = run.result(); var s = r.summary(); var p = s.pairedStates();
        var sections = new ArrayList<Section>();
        var context = new ArrayList<>(settings(r.request().assumptions().settings()));
        context.add("Strategy A — " + r.request().strategyA().label() + " | Strategy B — " + r.request().strategyB().label());
        Arrays.stream(run.details().split("\n")).filter(line -> line.startsWith("Categories:") || line.startsWith("Longevity factors:")
                || line.startsWith("Healthcare inflation:") || line.startsWith("Common fixed horizon:"))
                .forEach(context::add);
        context.add("Both strategies were evaluated against the same simulated market, inflation, and longevity outcomes. Reported differences therefore compare the strategies within matched simulated worlds.");
        sections.add(new Section("Funding reliability", List.of("All funding probabilities use all " + count(p.requestedCount()) + " requested paired worlds."), List.of(
                new Table(List.of(r.request().strategyA().label(), r.request().strategyB().label(), "Difference: Current − Baseline"),
                        List.of(List.of(MonteCarloPresentation.fundingPercent(p.aFundingProbability(), p.aFailedCount(), p.aCompletedCount()),
                                MonteCarloPresentation.fundingPercent(p.bFundingProbability(), p.bFailedCount(), p.bCompletedCount()),
                                MonteCarloStrategyComparisonPresentation.difference(p.fundingProbabilityDifference()))), List.of(145, 145, 250)),
                new Table(List.of("Paired outcome", "Count", "Probability"), List.of(
                List.of("Both completed", count(p.bothCompleted()), probability(p.bothCompletedProbability())),
                List.of("Current completed / Baseline failed", count(p.aCompletedBFailed()), probability(p.aCompletedBFailedProbability())),
                List.of("Current failed / Baseline completed", count(p.aFailedBCompleted()), probability(p.aFailedBCompletedProbability())),
                List.of("Both failed", count(p.bothFailed()), probability(p.bothFailedProbability()))), List.of(330, 90, 120)))));
        var m = List.of(s.terminalInvestableAssetsDifference(), s.terminalNetWorthDifference(), s.terminalAfterTaxEstateDifference(), s.lifetimeTaxesDifference());
        var extras = new ArrayList<List<String>>();
        extras.add(metricRow("Mean A − B", m, a -> a.meanDifference().map(MonteCarloStrategyComparisonPresentation::money).orElse("Unavailable")));
        extras.add(metricRow("P(Current > Baseline)", m, a -> MonteCarloStrategyComparisonPresentation.probability(a.greaterProbabilityAmongComparable())));
        extras.add(metricRow("P(Current = Baseline)", m, a -> MonteCarloStrategyComparisonPresentation.probability(a.equalProbabilityAmongComparable())));
        extras.add(metricRow("P(Current < Baseline)", m, a -> MonteCarloStrategyComparisonPresentation.probability(a.lessProbabilityAmongComparable())));
        var notes = new ArrayList<>(List.of("Current − Baseline (A minus B). Financial differences are calculated only from simulations in which both strategies completed successfully.",
                MonteCarloStrategyComparisonPresentation.denominator(s),
                "Relation probabilities use the same both-completed denominator; ties are a separate category.",
                "Lifetime Modeled Income Taxes: positive Current − Baseline means Current Plan paid MORE modeled income tax; negative means Current Plan paid LESS. More tax is not inherently favorable."));
        if (run.mode() == MonteCarloMode.LONGEVITY_ADJUSTED) notes.add(MonteCarloMortalityPresentation.lifetimeText(MonteCarloStrategyComparisonPresentation.NOMINAL, r.request().assumptions().hasSpouse()));
        sections.add(new Section("Paired financial differences", notes, List.of(distributions(m.stream().map(MonteCarloPairedMetricSummary::differencePercentiles).toList(), true, extras))));
        sections.add(new Section("Annual paired population", List.of(MonteCarloStrategyComparisonPresentation.ANNUAL,
                "Current-only / Baseline-only funded and both failed count living households only. Deceased/not living is normal mortality absence, not funding failure.", SMALL), List.of(new Table(
                List.of("Year", "Requested", "Living", "Comparable", "Current-only funded", "Baseline-only funded", "Both failed", "Deceased / not living", "Sample flag"),
                s.annualResults().values().stream().map(a -> List.of("" + a.year(), count(a.requestedWorldCount()), count(a.livingHouseholdCount()), count(a.comparableCount()),
                        count(a.aCompletedBFailedCount()), count(a.aFailedBCompletedCount()), count(a.bothFailedCount()), count(a.bothDeceasedCount()),
                        MonteCarloStrategyComparisonPresentation.smallSample(a) ? "Small" : "")).toList(), List.of(40, 59, 48, 68, 72, 72, 55, 72, 54)))));
        var points = s.annualResults().values().stream().map(a -> new Point(a.year(), a.investableAssetsDifference().differencePercentiles(), Optional.<BigDecimal>empty())).toList();
        sections.add(annualValues(points, true, p.requestedCount()));
        failures(sections, "Strategy A funding failures", s.strategyAFailureStatistics());
        failures(sections, "Strategy B funding failures", s.strategyBFailureStatistics());
        sections.add(section("Frozen analysis details", run.details()));
        sections.add(section("Methodology and interpretation", EXPECTED, PERCENTILES,
                "These are percentiles of the paired differences: Current Plan − Saved Baseline in each comparable world. They are NOT percentile(Current) − percentile(Baseline).",
                "Mean differences and relation probabilities come from the completed analysis. No simulation or financial projection is performed for PDF export.",
                inflationNotes(r.request().assumptions().settings())));
        return new MonteCarloPdfReport("Monte Carlo Strategy Comparison", run.mode().toString(), "monte-carlo-strategy-comparison.pdf", Instant.now(), context,
                new Chart("Investable Assets Difference Over Time", MonteCarloStrategyComparisonPresentation.ANNUAL + " " + MonteCarloStrategyComparisonPresentation.AXIS,
                        true, points, List.of(), List.of()), sections);
    }

    private static List<String> settings(MonteCarloSettings s) {
        return List.of("Simulations: " + count(s.simulationCount()) + " | Seed: " + s.seed(),
                "Expected annual return: " + UIFormatters.percent(s.expectedReturn()) + " | Return volatility: " + UIFormatters.percent(s.returnVolatility()),
                MonteCarloPresentation.inflationSummary(s));
    }
    private static String inflationNotes(MonteCarloSettings settings) {
        if (settings.inflation().isEmpty()) return "General spending inflation uses the frozen plan assumption. Healthcare inflation remains deterministic. Social Security COLA, pension COLAs, tax indexation, Medicare/IRMAA assumptions and asset appreciation remain separate and deterministic.";
        return MonteCarloPresentation.inflationSummary(settings) + "\n"
                + MonteCarloPresentation.inflationDetails(settings).lines()
                .filter(line -> line.startsWith("V1 samples") || line.startsWith("V1 limitations"))
                .collect(java.util.stream.Collectors.joining("\n"))
                + "\nMarket returns, inflation and mortality are independent in this version.";
    }
    private static Section section(String title, String... text) { return new Section(title, List.of(text), List.of()); }
    private static String count(long value) { return String.format(Locale.US, "%,d", value); }
    private static String probability(BigDecimal value) { return MonteCarloStrategyComparisonPresentation.probability(Optional.of(value)); }
    private static List<String> metricRow(String label, List<MonteCarloPairedMetricSummary> metrics, Function<MonteCarloPairedMetricSummary, String> value) {
        var row = new ArrayList<String>(); row.add(label); metrics.forEach(m -> row.add(value.apply(m))); return row;
    }
    private static Table distributions(List<Optional<MonteCarloPercentiles>> metrics, boolean signed, List<List<String>> extras) {
        var rows = new ArrayList<List<String>>();
        var labels = List.of("Min", "P10", "P25", "Median / P50", "P75", "P90", "Max");
        for (int i = 0; i < labels.size(); i++) {
            int index = i; var row = new ArrayList<String>(); row.add(labels.get(i));
            for (var metric : metrics) row.add(metric.map(p -> money(MonteCarloPresentation.quantiles(p).get(index), signed)).orElse("Unavailable"));
            rows.add(row);
        }
        rows.addAll(extras);
        var samples = new ArrayList<String>(); samples.add("Sample count"); metrics.forEach(p -> samples.add(count(p.map(MonteCarloPercentiles::sampleCount).orElse(0)))); rows.add(samples);
        var headings = new ArrayList<String>(); headings.add(signed ? "Current − Baseline" : "Percentile"); headings.addAll(METRICS);
        return new Table(headings, rows, List.of(112, 107, 107, 107, 107));
    }
    private static String money(BigDecimal value, boolean signed) { return signed ? MonteCarloStrategyComparisonPresentation.money(value) : UIFormatters.money(value); }
    private static Section annualValues(List<Point> points, boolean signed, int requested) {
        return new Section("Annual investable assets" + (signed ? " differences" : "") + " — percentiles", List.of("Requested: " + count(requested) + ". Sample count is conditional on completion through each year" + (signed ? " by both strategies." : ".")), List.of(new Table(
                List.of("Year", "Sample count", "P10", "Median / P50", "P90"), points.stream().map(p -> List.of("" + p.year(), count(p.percentiles().map(MonteCarloPercentiles::sampleCount).orElse(0)),
                        p.percentiles().map(v -> money(v.p10(), signed)).orElse("Unavailable"), p.percentiles().map(v -> money(v.p50(), signed)).orElse("Unavailable"),
                        p.percentiles().map(v -> money(v.p90(), signed)).orElse("Unavailable"))).toList(), List.of(48, 90, 134, 134, 134))));
    }
    private static void failures(List<Section> sections, String title, Optional<FundingFailureStatistics> failures) {
        if (failures.isEmpty()) { sections.add(section(title, "No funding failures observed.")); return; }
        var f = failures.orElseThrow();
        sections.add(new Section(title, List.of("Failures: " + count(f.count()) + ". First-failure year: earliest " + f.firstShortfallYear().minimum().toPlainString()
                + "; median " + f.firstShortfallYear().p50().stripTrailingZeros().toPlainString() + "; latest " + f.firstShortfallYear().maximum().toPlainString() + ".",
                "Funding-constraint shortfalls combine authoritative allocator stages; they are not necessarily unmet living expenses. Shortfall amount: min " + UIFormatters.money(f.shortfallAmount().minimum())
                        + "; median " + UIFormatters.money(f.shortfallAmount().p50()) + "; max " + UIFormatters.money(f.shortfallAmount().maximum()) + "."),
                List.of(new Table(List.of("First-failure year", "Count", "Probability / all requested", "Fraction / failures"), f.countByYear().entrySet().stream().map(e -> List.of("" + e.getKey(), count(e.getValue()),
                        probability(f.probabilityByYear().get(e.getKey())), probability(f.fractionOfFailuresByYear().get(e.getKey())))).toList(), List.of(140, 80, 160, 160)))));
    }
}
