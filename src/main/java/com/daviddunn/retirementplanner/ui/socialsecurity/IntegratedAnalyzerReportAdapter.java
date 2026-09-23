package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;

/** Read-only adapters over completed results and presentation selections. No JavaFX nodes or engines. */
final class IntegratedAnalyzerReportAdapter {
    static IntegratedAnalyzerReport deterministic(IntegratedReportContext context,
            ExhaustiveIntegratedSearchPresentation model, ClaimingStrategyHeatMapModel map,
            ClaimingStrategyHeatMapMetric metric, ClaimingStrategyHeatMapCell selectedCell,
            IntegratedSocialSecurityCompleteStrategySearchEntry selected,
            List<ExhaustiveIntegratedSearchPresentation.Group> orderedRows, String socialSecurityReference) {
        var result = model.result();
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Search Summary and Current Plan", List.of(
                "Ranking objective: Deterministic After-Tax Estate; future dollars at the configured horizon.",
                "Current strategy: " + elections(result.currentPlanBaseline().evaluatedStrategy()),
                "Current survivor policy: persisted deterministic projection policy.",
                "Current metric position: " + rank(result.currentPlanRank()),
                "Successful: " + result.successfulStrategyCount() + "; failed: " + result.failedStrategyCount()
                        + "; complete strategies evaluated: " + result.totalStrategyCount() + "; runtime: " + model.runtime(),
                "Grouped table includes all " + orderedRows.size() + " represented outcome groups, in screen order. "
                        + "Grouping is presentation only; equivalent strategy counts are preserved.", socialSecurityReference)));
        sections.add(new Section("Current Plan Financial Metrics", metrics(result.currentPlanBaseline().metrics())));
        result.rankedSuccessfulEntries().stream().findFirst().ifPresent(best -> {
            sections.add(new Section("Highest Tested Strategy", deterministicDetails(best)));
            var gap = best.metrics().orElseThrow().afterTaxEstate().subtract(result.currentPlanBaseline().metrics().afterTaxEstate());
            sections.add(new Section("Gap to Highest Tested", List.of("Highest minus current: " + signed(gap),
                    "Percent of current: " + deterministicGapPercent(gap, result.currentPlanBaseline().metrics().afterTaxEstate()))));
        });
        if (selected != null) sections.add(new Section("Selected Strategy - Complete Financial Details", deterministicDetails(selected)));
        var failures = result.entries().stream().filter(entry -> !entry.successful())
                .map(entry -> "Occurrence " + entry.generationOrder() + ": " + elections(entry.strategy())
                        + " | Unavailable: " + entry.failure().orElseThrow().message()).toList();
        if (!failures.isEmpty()) sections.add(new Section("Unavailable Strategies", failures));
        sections.add(new Section("Interpretation", List.of(ClaimingStrategyHeatMapProfile.deterministic().explanation(),
                "Retirement ages tested: Primary 62-70; Spouse 62-70. Complete survivor elections are tested independently; "
                        + "Primary survivor applies if the spouse dies first and Spouse survivor if the primary dies first.",
                "Valuation date: not applicable to deterministic future-dollar results; no discounting or mortality weighting is applied.",
                "Heat-map colors use exact, unrounded percent of optimal regardless of the display metric. "
                        + "Each cell shows the best successful complete strategy for its age pair. A table selection may have different survivor elections.")));
        List<List<String>> electionRows = new ArrayList<>();
        List<List<String>> valueRows = new ArrayList<>();
        for (var group : orderedRows) {
            var entry = group.representative();
            var m = entry.metrics().orElseThrow();
            electionRows.add(electionRow(Integer.toString(entry.generationOrder()), rank(entry.afterTaxEstateRank()), entry.strategy(),
                    Integer.toString(group.equivalentStrategyCount())));
            valueRows.add(List.of(Integer.toString(entry.generationOrder()), rank(entry.afterTaxEstateRank()),
                    money(m.lifetimeHouseholdSocialSecurity()), money(m.totalTaxes()), money(m.lifetimePortfolioWithdrawals()),
                    money(m.endingInvestableAssets()), money(m.afterTaxEstate()), signed(entry.differencesFromCurrentPlan().orElseThrow().afterTaxEstate())));
        }
        var tables = List.of(electionsTable(electionRows, "Equivalent Strategies"),
                table("Ranked Strategies - Deterministic Outcomes", valueRows,
                        "Strategy ID", "Estate Rank", "Lifetime SS", "Total Taxes", "Portfolio Withdrawals",
                        "Ending Investable Assets", "After-Tax Heir Value", "Delta Estate vs Current"));
        var selectedSummary = selected == null ? unavailableSelection(selectedCell) : summary(selected.strategy(),
                rank(selected.afterTaxEstateRank()), selected.metrics().map(ProjectionMetrics::afterTaxEstate), map.optimalValue(),
                selected.differencesFromCurrentPlan().map(ProjectionMetrics::afterTaxEstate), "After-Tax Heir Value");
        return report("Deterministic Analysis", context, map, metric, selectedCell, selected == null ? null : selected.strategy(),
                selectedSummary, tables, sections, List.of("Future dollars at configured projection horizon", map.context(),
                        "Objective: highest tested After-Tax Estate; no mortality weighting or discounting"));
    }

    static IntegratedAnalyzerReport weighted(IntegratedReportContext context,
            LongevityWeightedIntegratedPresentation model, ClaimingStrategyHeatMapModel map,
            ClaimingStrategyHeatMapMetric metric, ClaimingStrategyHeatMapCell selectedCell,
            LongevityWeightedIntegratedStrategyComparisonEntry selected,
            List<LongevityWeightedIntegratedStrategyComparisonEntry> orderedRows,
            List<IntegratedAnalysisComparisonPresentation.Row> comparisons,
            IntegratedSocialSecurityCompleteStrategySearchResult deterministicReference) {
        var result = model.result();
        var metadata = result.metadata();
        var longevity = metadata.longevityAssumptions();
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Methodology and Result-Time Mortality Assumptions", List.of(
                LongevityWeightedIntegratedView.METHODOLOGY, LongevityWeightedIntegratedView.LIMITATION,
                "Primary mortality category: " + longevity.primaryCategory() + "; factor: " + longevity.primaryAdjustment().factor(),
                "Spouse mortality category: " + longevity.spouseCategory() + "; factor: " + longevity.spouseAdjustment().factor(),
                "Categories captured from Person information when the analysis request was created.",
                "Retirement ages tested: Primary 62-70; Spouse 62-70. Complete survivor elections are tested independently. "
                        + "Primary survivor elections apply if the spouse dies first; spouse survivor elections apply if the primary dies first.",
                "Mortality conditioning date: " + longevity.mortalityBaseDate(),
                "Mortality table: " + longevity.tableMetadata(), "Timing convention: " + longevity.partialYearConvention(),
                "Independent mortality: " + longevity.assumesIndependentMortality(),
                "Valuation date: " + metadata.valuationDate() + "; real discount rate: " + metadata.realDiscountRate()
                        + "; general inflation: " + metadata.generalInflationRate(),
                "January 1 second deaths use the preceding December 31 estate or matching opening snapshot. "
                        + "Scenarios end at their required second-death snapshot; configured plan length is reference only. "
                        + "Discount timing is actual days / 365.25. Original probabilities are not renormalized.",
                "Methodology version: " + metadata.methodologyVersion() + "; reference: " + metadata.methodologyReference(),
                String.join("\n", metadata.financialLimitations()))));
        model.highest().ifPresent(best -> sections.add(new Section("Highest Longevity-Weighted Tested Strategy", weightedDetails(model, best))));
        sections.add(new Section("Current Strategy Baseline", List.of(context.baseline(),
                "Current " + (model.currentHasCandidateRank() ? "weighted rank" : "metric position") + ": " + rank(model.currentPosition()),
                "Tested strategies tied at current PV: " + model.currentTieCount(), "Tied highest: " + model.highestTieCount(),
                "Improvement over current: " + model.improvementPercent().map(value -> value + "%").orElse("Unavailable"),
                "Difference from Highest Strategy (current minus highest PV): " + result.baseline().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                        .flatMap(base -> model.highest().map(best -> signed(LongevityWeightedIntegratedPresentation.pv(base)
                                .subtract(LongevityWeightedIntegratedPresentation.pv(best))))).orElse("Unavailable"))));
        result.baseline().ifPresent(base -> sections.add(new Section("Current Strategy Financial Result", weightedDetails(model, base))));
        if (selected != null) {
            var details = new ArrayList<>(weightedDetails(model, selected));
            details.add("Deterministic rank: " + (deterministicReference == null ? "Unavailable - requires a current compatible deterministic result"
                    : rank(deterministicReference.rankOf(selected.strategy()))));
            sections.add(new Section("Selected Strategy - Complete Aggregate Details", details));
        }
        sections.add(new Section("Compare Analysis Outcomes", List.of(IntegratedAnalysisComparisonPresentation.EXPLANATION,
                "Unavailable deterministic values require a current compatible deterministic exhaustive result. "
                        + "Highest rows represent the first tied strategy when applicable.")));
        for (var row : comparisons) sections.add(new Section(row.role(), List.of(elections(row.strategy()),
                "Deterministic estate rank: " + rank(row.deterministicRank()) + "; ending investable assets: " + optionalMoney(row.deterministicInvestableAssets())
                        + "; After-Tax Heir Value: " + optionalMoney(row.deterministicEstate()),
                "Weighted rank: " + rank(row.weightedRank()) + "; expected investable assets at second death: " + optionalMoney(row.weightedInvestableAssets())
                        + "; expected After-Tax Heir Value: " + optionalMoney(row.weightedHeirValue()) + "; expected PV After-Tax Estate: " + optionalMoney(row.weightedPv()))));
        sections.add(new Section("Computation and Coverage", List.of("Successful: " + result.completedStrategyCount()
                + "; failed candidates: " + result.failedStrategyCount() + "; elapsed: " + result.elapsedTime(),
                "Work: " + result.work(), "Equivalence groups: " + result.equivalencePlan().map(value -> Integer.toString(value.equivalenceGroupCount())).orElse("Unavailable")
                        + "; evaluations avoided: " + result.evaluationsAvoided(),
                "Compact aggregates are retained. Scenario detail loading is not available in this release.",
                ClaimingStrategyHeatMapProfile.weighted().explanation())));
        var failures = result.failures().stream().map(entry -> "Occurrence " + entry.inputOrder() + ": "
                + elections(entry.strategy()) + " | Unavailable: " + entry.failure().orElseThrow().message()).toList();
        if (!failures.isEmpty()) sections.add(new Section("Unavailable Strategies and Baseline", failures));
        List<List<String>> electionRows = new ArrayList<>();
        List<List<String>> valueRows = new ArrayList<>();
        for (var entry : orderedRows) {
            electionRows.add(electionRow(Integer.toString(entry.inputOrder()), rank(entry.rank()), entry.strategy(), model.marker(entry)));
            valueRows.add(List.of(Integer.toString(entry.inputOrder()), rank(entry.rank()),
                    optionalMoney(entry.aggregate().map(LongevityWeightedStrategyAggregate::expectedInvestableAssetsAtSecondDeath)),
                    optionalMoney(entry.aggregate().map(LongevityWeightedStrategyAggregate::expectedNominalEstateAtSecondDeath)),
                    optionalMoney(entry.aggregate().map(LongevityWeightedStrategyAggregate::expectedPvAfterTaxEstate)),
                    entry.pvDifferenceFromBaseline().map(IntegratedAnalyzerReportAdapter::signed).orElse("Unavailable")));
        }
        var tables = List.of(electionsTable(electionRows, "Markers"), table("Ranked Strategies - Longevity-Weighted Outcomes", valueRows,
                "Strategy ID", "Weighted Rank", "Expected Investable Assets at Second Death", "Expected After-Tax Heir Value",
                "Expected PV After-Tax Estate", "Difference vs Current"));
        var selectedSummary = selected == null ? unavailableSelection(selectedCell) : summary(selected.strategy(), rank(selected.rank()),
                selected.aggregate().map(LongevityWeightedStrategyAggregate::expectedPvAfterTaxEstate), map.optimalValue(),
                selected.pvDifferenceFromBaseline(), "Expected PV After-Tax Estate");
        return report("Longevity-Weighted Analysis", context, map, metric, selectedCell, selected == null ? null : selected.strategy(),
                selectedSummary, tables, sections, List.of("Valuation: " + metadata.valuationDate() + "; conditioning: " + longevity.mortalityBaseDate(),
                        "Mortality: Primary " + longevity.primaryCategory() + " x " + longevity.primaryAdjustment().factor()
                                + "; Spouse " + longevity.spouseCategory() + " x " + longevity.spouseAdjustment().factor(),
                        "Objective: Expected PV After-Tax Estate at second death; real discount " + metadata.realDiscountRate()));
    }

    private static IntegratedAnalyzerReport report(String type, IntegratedReportContext context,
            ClaimingStrategyHeatMapModel map, ClaimingStrategyHeatMapMetric metric, ClaimingStrategyHeatMapCell selectedCell,
            SocialSecurityHouseholdClaimingStrategy selected, List<String> selectedSummary, List<Table> tables,
            List<Section> sections, List<String> keyContext) {
        var cells = map.cells().stream().map(cell -> new Cell(cell.primaryClaimingAge(), cell.spouseClaimingAge(),
                ClaimingHeatMapFormatting.format(metric, cell.value(metric)),
                ClaimingHeatMapPalette.forPercent(cell.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)), cell.optimal(),
                selected != null ? cell.primaryClaimingAge() == selected.primaryRetirementAge() && cell.spouseClaimingAge() == selected.spouseRetirementAge()
                        : cell.equals(selectedCell))).toList();
        List<String> lines = new ArrayList<>();
        lines.add("Primary: " + context.primary() + " | Spouse: " + context.spouse());
        lines.addAll(keyContext);
        if (type.equals("Deterministic Analysis")) {
            var keyAssumptions = context.assumptions().stream().flatMap(section -> section.lines().stream())
                    .filter(line -> line.startsWith("Investment return assumptions:") || line.startsWith("General inflation:")
                            || line.startsWith("Social Security COLA:"))
                    .map(line -> line.split(" \\[", 2)[0]).toList();
            lines.add(String.join(" | ", keyAssumptions));
        }
        return new IntegratedAnalyzerReport(type, context.household(), context.runStarted(), Instant.now(), lines,
                selectedSummary, new HeatMap(metric.toString(), "Colors: % of optimal (unrounded). Stars: exact optimal results, including ties. "
                + "Blue outline: selected age pair; grid values represent that pair's best strategy.", map.primaryAges(), map.spouseAges(), cells),
                context.assumptions(), tables, sections);
    }

    private static List<String> unavailableSelection(ClaimingStrategyHeatMapCell cell) {
        return List.of("Primary " + cell.primaryClaimingAge() + " / Spouse " + cell.spouseClaimingAge(),
                "Not Available: no successful strategy for this age pair.", "Failed strategies for pair: " + cell.failedStrategyCount());
    }

    private static List<String> summary(SocialSecurityHouseholdClaimingStrategy strategy, String rank,
            Optional<BigDecimal> value, Optional<BigDecimal> optimal, Optional<BigDecimal> delta, String objective) {
        List<String> lines = new ArrayList<>();
        lines.add("Primary " + strategy.primaryRetirementAge() + " / Spouse " + strategy.spouseRetirementAge());
        lines.add("Rank: " + rank + (value.isPresent() && optimal.isPresent() && value.orElseThrow().compareTo(optimal.orElseThrow()) == 0
                ? " | Optimal (highest tested)" : " | Selected candidate"));
        lines.add(objective + ": " + optionalMoney(value));
        lines.add("% of Optimal: " + (value.isPresent() && optimal.isPresent() ? percent(value.orElseThrow(), optimal.orElseThrow()) : "Unavailable"));
        lines.add("Difference vs Optimal: " + (value.isPresent() && optimal.isPresent() ? signed(value.orElseThrow().subtract(optimal.orElseThrow())) : "Unavailable"));
        lines.add("Difference vs Current: " + delta.map(IntegratedAnalyzerReportAdapter::signed).orElse("Unavailable"));
        lines.addAll(Arrays.asList(elections(strategy).split("\n")));
        return lines;
    }

    private static List<String> deterministicDetails(IntegratedSocialSecurityCompleteStrategySearchEntry entry) {
        List<String> lines = new ArrayList<>(List.of("Occurrence: " + entry.generationOrder() + "; estate rank: " + rank(entry.afterTaxEstateRank()), elections(entry.strategy())));
        if (entry.successful()) {
            lines.addAll(metrics(entry.metrics().orElseThrow()));
            lines.add("Candidate minus current plan:");
            lines.addAll(metrics(entry.differencesFromCurrentPlan().orElseThrow()));
            lines.add("Projection detail retained: " + entry.retainedDetail().isPresent());
        } else lines.add("Unavailable: " + entry.failure().orElseThrow().message());
        return lines;
    }

    private static List<String> weightedDetails(LongevityWeightedIntegratedPresentation model, LongevityWeightedIntegratedStrategyComparisonEntry entry) {
        List<String> lines = new ArrayList<>(List.of("Original strategy occurrence: " + entry.inputOrder() + "; weighted rank: " + rank(entry.rank()), elections(entry.strategy())));
        if (!entry.successful()) { lines.add("Unavailable: " + entry.failure().orElseThrow().message()); return lines; }
        var a = entry.aggregate().orElseThrow();
        lines.addAll(List.of("Expected Investable Assets at Second Death: " + money(a.expectedInvestableAssetsAtSecondDeath()),
                "Expected After-Tax Heir Value (nominal future dollars): " + money(a.expectedNominalEstateAtSecondDeath()),
                "Expected PV After-Tax Estate (valuation-date dollars): " + money(a.expectedPvAfterTaxEstate()),
                "Minimum nominal scenario estate: " + money(a.minimumNominalScenarioEstate()),
                "Maximum nominal scenario estate: " + money(a.maximumNominalScenarioEstate()),
                "Evaluated probability coverage: " + a.totalEvaluatedProbability() + "; scenario count: " + a.originalScenarioCount(),
                "Actual ProjectionEngine runs: " + a.actualProjectionRunCount(),
                "PV Difference vs Current: " + entry.pvDifferenceFromBaseline().map(IntegratedAnalyzerReportAdapter::signed).orElse("Unavailable"),
                "Proven-equivalence group size: " + model.provenEquivalentCount(entry.inputOrder()) + "; rounded values do not establish ties or equivalence."));
        return lines;
    }

    private static List<String> metrics(ProjectionMetrics m) {
        return List.of("Investment growth: " + money(m.totalInvestmentGrowth()), "Total guaranteed income: " + money(m.totalIncome()),
                "Total taxes: " + money(m.totalTaxes()), "Peak annual tax: " + money(m.peakAnnualTax()),
                "Ending Investable Assets: " + money(m.endingInvestableAssets()), "Ending net worth: " + money(m.endingNetWorth()),
                "After-Tax Heir Value: " + money(m.afterTaxEstate()), "Portfolio withdrawals: " + money(m.lifetimePortfolioWithdrawals()),
                "Roth conversions: " + money(m.lifetimeRothConversions()), "RMDs: " + money(m.lifetimeRequiredMinimumDistributions()),
                "Medicare premiums: " + money(m.lifetimeMedicarePremiums()), "Household Social Security: " + money(m.lifetimeHouseholdSocialSecurity()),
                "Primary Social Security: " + money(m.lifetimePrimarySocialSecurity()), "Spouse Social Security: " + money(m.lifetimeSpouseSocialSecurity()));
    }

    private static List<String> electionRow(String id, String rank, SocialSecurityHouseholdClaimingStrategy s, String marker) {
        return List.of(id, rank, "Age " + s.primaryRetirementAge() + "\n" + s.primaryRetirementClaimDate(),
                "Age " + s.spouseRetirementAge() + "\n" + s.spouseRetirementClaimDate(),
                survivor(s.primarySurvivorElection()) + "\n" + s.primarySurvivorElection().claimDate(),
                survivor(s.spouseSurvivorElection()) + "\n" + s.spouseSurvivorElection().claimDate(), marker);
    }

    private static Table electionsTable(List<List<String>> rows, String last) {
        return new Table("Ranked Strategies - Complete Elections", List.of(new Column("Strategy ID", 70, true),
                new Column("Rank", 45, true), new Column("Primary Retirement", 125, false), new Column("Spouse Retirement", 125, false),
                new Column("Primary Survivor Benefit Claiming Age", 125, false), new Column("Spouse Survivor Benefit Claiming Age", 125, false), new Column(last, 90, false)), rows);
    }

    private static Table table(String title, List<List<String>> rows, String... names) {
        return new Table(title, java.util.stream.IntStream.range(0, names.length)
                .mapToObj(i -> new Column(names[i], i < 2 ? 70 : 120, true)).toList(), rows);
    }
    private static String elections(SocialSecurityHouseholdClaimingStrategy s) {
        return "Primary retirement: Age " + s.primaryRetirementAge() + " - " + s.primaryRetirementClaimDate()
                + "\nSpouse retirement: Age " + s.spouseRetirementAge() + " - " + s.spouseRetirementClaimDate()
                + "\nPrimary Survivor Benefit Claiming Age: " + survivor(s.primarySurvivorElection()) + " - " + s.primarySurvivorElection().claimDate()
                + "\nSpouse Survivor Benefit Claiming Age: " + survivor(s.spouseSurvivorElection()) + " - " + s.spouseSurvivorElection().claimDate();
    }
    private static String survivor(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate value) {
        return "Age " + value.ageYears() + (value.ageMonths() == 0 ? "" : "y " + value.ageMonths() + "m");
    }
    private static String rank(OptionalInt value) { return value.isPresent() ? Integer.toString(value.getAsInt()) : "Unavailable"; }
    private static String money(BigDecimal value) { return UIFormatters.money(value); }
    private static String optionalMoney(Optional<BigDecimal> value) { return value.map(IntegratedAnalyzerReportAdapter::money).orElse("Unavailable"); }
    private static String signed(BigDecimal value) { return (value.signum() > 0 ? "+" : "") + money(value); }
    private static String percent(BigDecimal value, BigDecimal base) {
        return base.signum() <= 0 ? "Unavailable" : value.multiply(new BigDecimal("100")).divide(base, MathContext.DECIMAL128)
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }
    private static String deterministicGapPercent(BigDecimal gap, BigDecimal current) {
        return current.signum() == 0 ? "Unavailable" : gap.multiply(new BigDecimal("100"))
                .divide(current, 2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
