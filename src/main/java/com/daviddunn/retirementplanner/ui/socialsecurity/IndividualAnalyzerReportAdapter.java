package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import java.math.*;
import java.time.Instant;
import java.util.*;

/** Adapts completed individual results, without constructing couple coordinates or running analysis. */
final class IndividualAnalyzerReportAdapter {
    static IndividualAge age(int age, Optional<BigDecimal> value, Optional<BigDecimal> optimum, int selected, int current) {
        var percent = value.flatMap(v -> optimum.filter(o -> o.signum() > 0)
                .map(o -> v.multiply(new BigDecimal("100")).divide(o, MathContext.DECIMAL128)));
        return new IndividualAge(age, value.map(UIFormatters::money).orElse("Unavailable"),
                percent.map(p -> p.setScale(1, RoundingMode.HALF_UP) + "%").orElse("Unavailable"),
                ClaimingHeatMapPalette.forPercent(percent), value.isPresent() && optimum.isPresent()
                && value.orElseThrow().compareTo(optimum.orElseThrow()) == 0, age == selected, age == current);
    }

    static IntegratedAnalyzerReport deterministic(IndividualReportContext context, SinglePersonIntegratedAnalysis run, int selected) {
        var r = run.result();
        var optimum = r.rankedSuccessfulEntries().stream().findFirst().flatMap(e -> e.metrics().map(ProjectionMetrics::afterTaxEstate));
        int current = r.currentPlanBaseline().evaluatedStrategy().primaryRetirementAge();
        var ages = r.entries().stream().sorted(Comparator.comparingInt(e -> e.strategy().primaryRetirementAge()))
                .map(e -> age(e.strategy().primaryRetirementAge(), e.metrics().map(ProjectionMetrics::afterTaxEstate), optimum, selected, current)).toList();
        var entry = r.entries().stream().filter(e -> e.strategy().primaryRetirementAge() == selected).findFirst().orElseThrow();
        var sections = new ArrayList<Section>();
        sections.add(new Section("Current Plan Financial Metrics", metrics(r.currentPlanBaseline().metrics())));
        entry.metrics().ifPresent(m -> sections.add(new Section("Selected Strategy Financial Metrics", metrics(m))));
        sections.add(new Section("Interpretation", List.of("Deterministic full-plan analysis at the configured horizon; no mortality weighting or discounting.",
                "Nine primary retirement claiming ages; claiming age determines the benefit start date.",
                run.primaryDeathYear().map(y -> "Primary death is January 1, " + y + ". Own benefits end at death.")
                        .orElse("Primary is modeled living through the configured horizon."))));
        var rows = r.entries().stream().sorted(Comparator.comparingInt(e -> e.afterTaxEstateRank().orElse(Integer.MAX_VALUE)))
                .map(e -> List.of(Integer.toString(e.strategy().primaryRetirementAge()), e.afterTaxEstateRank().isPresent() ? "" + e.afterTaxEstateRank().getAsInt() : "Unavailable",
                        e.metrics().map(m -> money(m.afterTaxEstate())).orElse("Unavailable"),
                        e.differencesFromCurrentPlan().map(m -> money(m.afterTaxEstate())).orElse("Unavailable"))).toList();
        return report(context, "Deterministic Analysis", "After-Tax Estate", ages, selected, List.of(
                "Current claiming age: " + current, "Planning horizon: " + run.startYear() + "-" + run.endYear()),
                List.of(table("Ranked Strategies", List.of("Claiming age", "Rank", "After-Tax Estate", "Difference vs Current"), rows)), sections);
    }

    static IntegratedAnalyzerReport mortality(IndividualReportContext context, SinglePersonMortalityAnalysis.Result r, int selected) {
        boolean integrated = r.mode() == SinglePersonMortalityAnalysis.Mode.INTEGRATED;
        String metric = integrated ? "Expected PV After-Tax Estate" : "Expected PV Social Security";
        var optimum = Optional.of(r.ranked().getFirst().expectedPresentValue());
        var ages = r.entries().stream().sorted(Comparator.comparingInt(e -> e.strategy().primaryRetirementAge()))
                .map(e -> age(e.strategy().primaryRetirementAge(), Optional.of(e.expectedPresentValue()), optimum, selected, r.currentAge())).toList();
        var request = r.mortality().individual().request();
        var sections = new ArrayList<Section>();
        sections.add(new Section("Mortality and Valuation Assumptions", List.of("Primary mortality category: " + request.mortalityCategory(),
                "Primary Longevity Factor: " + request.mortalityAdjustment().factor(), "Mortality Conditioning Date: " + request.mortalityBaseDate(),
                "PV Valuation Date: " + r.valuationDate() + "; real discount rate: " + UIFormatters.percent(r.discountRate()),
                "Social Security COLA: " + UIFormatters.percent(r.colaRate()) + "; General Inflation: " + UIFormatters.percent(r.inflationRate()),
                "Mortality table: " + r.mortality().individual().tableMetadata().displayName()
                        + "; timing: " + r.mortality().individual().partialYearConvention()
                        + "; terminal residual age: " + r.mortality().individual().terminalDeathAge(),
                "Individual death scenarios: " + r.mortality().scenarios().size() + "; projection executions: " + r.projectionCount())));
        sections.add(new Section("Methodology", integrated ? List.of(
                "Expected values use the individual lifetime distribution and completed full-plan scenario results, not a fixed life expectancy.",
                "January 1 death uses the preceding December 31 estate or matching opening snapshot. Values are measured at the person's death.",
                "Expected PV After-Tax Estate uses valuation-date dollars; nominal estate excludes non-investable property. No analysis is rerun for export.")
                : List.of("Mortality-weighted expected present value of own Social Security benefits, using existing benefit, COLA and discount conventions.",
                "Remaining benefits begin at the mortality conditioning month. This is not a fixed-horizon integrated retirement-plan ranking.")));
        var rows = r.ranked().stream().map(e -> {
            var values = new ArrayList<>(List.of("" + e.strategy().primaryRetirementAge(), "" + r.rank(e), money(e.expectedPresentValue()), money(e.expectedNominal())));
            if (integrated) values.add(money(e.expectedInvestableAssets()));
            return List.copyOf(values);
        }).toList();
        var headers = new ArrayList<>(List.of("Claiming age", "Rank", metric, integrated ? "Expected Nominal Estate" : "Expected Nominal Own Benefit"));
        if (integrated) headers.add("Expected Investable Assets");
        return report(context, integrated ? "Longevity-Weighted Analysis" : "Social Security Only", metric, ages, selected,
                List.of("Current claiming age: " + r.currentAge(), "Valuation date: " + r.valuationDate()),
                List.of(table("Ranked Strategies", headers, rows)), sections);
    }

    private static IntegratedAnalyzerReport report(IndividualReportContext context, String type, String metric,
            List<IndividualAge> ages, int selected, List<String> notes, List<Table> tables, List<Section> results) {
        var chosen = ages.stream().filter(a -> a.age() == selected).findFirst().orElseThrow();
        var summary = List.of("Primary claiming age " + selected, metric + ": " + chosen.value(),
                "% of optimal: " + chosen.percentOfOptimal(), chosen.optimal() ? "Optimal (highest tested)" : "Selected candidate",
                chosen.current() ? "Current plan strategy" : "Analysis alternative");
        var lines = new ArrayList<>(List.of("Primary: " + context.primary())); lines.addAll(notes);
        var assumptions = type.equals("Social Security Only")
                ? List.of(new Section("Result-Time Social Security Inputs", context.assumptions().stream()
                    .flatMap(section -> section.lines().stream())
                    .filter(line -> line.startsWith("Primary:") || line.startsWith("Primary Social Security:")).toList()))
                : context.assumptions();
        return new IntegratedAnalyzerReport(type, context.household(), context.started(), Instant.now(), lines, summary,
                new Individual(metric, ages), assumptions, tables, results);
    }
    private static Table table(String title, List<String> headers, List<List<String>> rows) {
        return new Table(title, headers.stream().map(h -> new Column(h, 120, true)).toList(), rows);
    }
    private static List<String> metrics(ProjectionMetrics m) {
        return List.of("Investment Growth: " + money(m.totalInvestmentGrowth()), "Total Income: " + money(m.totalIncome()),
                "Total Taxes: " + money(m.totalTaxes()), "Peak Annual Tax: " + money(m.peakAnnualTax()),
                "Investable Assets: " + money(m.endingInvestableAssets()), "Net Worth: " + money(m.endingNetWorth()),
                "After-Tax Estate: " + money(m.afterTaxEstate()), "Social Security: " + money(m.lifetimeHouseholdSocialSecurity()),
                "RMDs: " + money(m.lifetimeRequiredMinimumDistributions()), "Roth Conversions: " + money(m.lifetimeRothConversions()),
                "Medicare: " + money(m.lifetimeMedicarePremiums()), "Portfolio Withdrawals: " + money(m.lifetimePortfolioWithdrawals()));
    }
    private static String money(BigDecimal amount) { return UIFormatters.money(amount); }
}
