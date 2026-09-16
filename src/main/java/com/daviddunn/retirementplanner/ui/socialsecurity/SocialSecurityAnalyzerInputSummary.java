package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityTables;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Immutable display snapshot from current authoritative values; no projection or mortality calculation. */
record SocialSecurityAnalyzerInputSummary(List<Row> rows) {
    static final String WEIGHTED_HORIZON = "Through household second death. January 1 second death uses the prior December 31 snapshot (secondDeathYear - 1); second death on the projection opening date uses the opening snapshot.";
    static final String DATE_EXPLANATION = "Projection start date: financial projection begins. Planning Horizon (assumed second death / end of household projection): deterministic horizon and configured duration. Mortality conditioning date: assumes both people survive through this date and conditions remaining mortality; SS-only remaining benefits start here. Valuation date: common date for PV dollars, independent of survival and projection start. Plan death scenario: deterministic analysis only.";

    record Row(String input, String value, String source) { }
    /** Null editable values represent missing/invalid input, not an invented default. */
    record AnalyzerValues(BigDecimal primaryAdjustment,
                          BigDecimal spouseAdjustment,
                          LocalDate conditioningDate, LocalDate valuationDate, BigDecimal realDiscountRate) { }

    SocialSecurityAnalyzerInputSummary {
        rows = List.copyOf(rows);
    }

    static SocialSecurityAnalyzerInputSummary from(RetirementPlan plan, AnalyzerValues analyzer) {
        return from(plan, analyzer, CurrentStrategyBaseline.fromPlan(plan));
    }

    static SocialSecurityAnalyzerInputSummary from(RetirementPlan plan, AnalyzerValues analyzer,
            CurrentStrategyBaseline baseline) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(analyzer);
        var assumptions = plan.getPlanningAssumptions();
        var tax = assumptions.getTaxAssumptions();
        var death = assumptions.getDeathScenarioAssumptions();
        List<Row> rows = new ArrayList<>();
        LocalDate start = assumptions.getProjectionStartDate();
        // ProjectionEngine models N calendar years, including a possibly partial opening year.
        int endYear = Math.addExact(start.getYear(), assumptions.getProjectionLengthYears() - 1);
        rows.add(new Row("Projection start date", date(start), "Retirement Plan"));
        rows.add(new Row("Planning Horizon - end date", LocalDate.of(endYear, 12, 31).toString(), "Retirement Plan"));
        rows.add(new Row("Planning Horizon - calendar years", assumptions.getProjectionLengthYears()
                + " calendar years (" + start.getYear() + "-" + endYear + ", inclusive; opening year may be partial)", "Retirement Plan"));
        rows.add(new Row("Weighted scenario horizon", WEIGHTED_HORIZON, "Mortality scenarios; configured end/length are reference only"));
        rows.add(new Row("Mortality conditioning date", date(analyzer.conditioningDate()), "Analyzer"));
        rows.add(new Row("Valuation date", date(analyzer.valuationDate()), "Analyzer"));
        rows.add(new Row("Real discount rate", percent(analyzer.realDiscountRate()), "Analyzer"));
        rows.add(new Row("Primary mortality category",
                mortalityCategory(plan.getHousehold().getPrimaryPerson()), "Person information"));
        rows.add(new Row("Primary mortality adjustment", factor(analyzer.primaryAdjustment()), "Analyzer / Mortality Assumptions"));
        rows.add(new Row("Spouse mortality category",
                mortalityCategory(plan.getHousehold().getSpouse()), "Person information"));
        rows.add(new Row("Spouse mortality adjustment", factor(analyzer.spouseAdjustment()), "Analyzer / Mortality Assumptions"));
        rows.add(new Row("Mortality probability distribution", SocialSecurityMortalityTables.ssaPeriod2022().metadata().displayName()
                + "; independent household mortality; conditioned on the date above using next complete birthday intervals", "Mortality Assumptions"));
        rows.add(new Row("General inflation", percent(assumptions.getGeneralInflationRate()), "Economic Assumptions"));
        rows.add(new Row("Social Security COLA", percent(assumptions.getSocialSecurityColaRate()), "Economic Assumptions"));
        rows.add(new Row("Investment return assumptions", percent(assumptions.getInvestmentReturnRate()) + " annual portfolio return", "Economic Assumptions"));
        rows.add(new Row("Healthcare inflation", percent(assumptions.getHealthcareInflationRate()), "Economic Assumptions"));
        rows.add(new Row("Configured plan death scenario", human(death.getDeathScenario())
                + (death.getDeathScenario() == DeathScenario.BOTH_SURVIVE ? "" : "; January 1, " + death.getDeathYear())
                + "; post-death recurring expense factor " + factor(death.getPostDeathExpenseFactor())
                + ". Deterministic only; weighted scenarios supply their own deaths.", "Retirement Plan"));
        var expenses = plan.getHousehold().getExpenses();
        rows.add(new Row("Expenses", compact(expenses.stream().map(expense -> expense.getDescription()
                + ": " + money(expense.getAnnualAmount()) + " configured annual amount, " + human(expense.getExpenseType())
                + ", " + human(expense.getGrowthCategory()) + ", " + range(expense.getStartDate(), expense.getEndDate())).toList()), "Retirement Plan"));
        var roth = plan.getRothConversionRequest();
        rows.add(new Row("Roth conversion strategy", roth == null || !roth.isEnabled() ? "Disabled"
                : human(roth.getStrategy()) + "; from " + roth.getStartYear() + "; " + human(roth.getFrequency())
                + "; requested annual amount " + money(roth.getAnnualAmount()) + "; stop: " + human(roth.getStopRule())
                + (roth.getCustomTargetTaxableIncome() == null ? "" : "; custom taxable-income target " + money(roth.getCustomTargetTaxableIncome())), "Retirement Plan"));
        var people = Stream.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse()).filter(Objects::nonNull).toList();
        rows.add(new Row("Pension assumptions", compact(people.stream().flatMap(person -> person.getIncomeSources().stream())
                .filter(Pension.class::isInstance).map(Pension.class::cast)
                .map(pension -> human(pension.getOwnership()) + ": " + money(pension.getMonthlyBenefit()) + "/month; survivor "
                        + money(pension.getSurvivorMonthlyBenefit()) + "/month; COLA " + percent(pension.getAnnualColaRate())
                        + "; " + range(pension.getStartDate(), pension.getEndDate())).toList()), "Retirement Plan"));
        var portfolio = plan.getAccountPortfolio();
        rows.add(new Row("Account balances / ownership", portfolio.getAccounts().size() + " accounts; total "
                + money(portfolio.getTotalBalance()) + "; " + Arrays.stream(AccountOwnership.values())
                .map(owner -> human(owner) + " " + money(portfolio.getTotalBalance(owner))).collect(Collectors.joining("; ")), "Retirement Plan"));
        rows.add(new Row("Federal tax assumptions", human(tax.getFilingStatus()) + "; bracket growth "
                + percent(tax.getFederalTaxBracketGrowthRate()) + "; deduction growth " + percent(tax.getStandardDeductionGrowthRate())
                + "; future marginal adjustment " + percent(tax.getFutureFederalMarginalRateAdjustment())
                + " from " + (tax.getFutureFederalMarginalRateEffectiveYear() == null ? "not scheduled" : tax.getFutureFederalMarginalRateEffectiveYear())
                + "; estimated heir tax rate " + percent(tax.getEstimatedHeirTaxRateOnTaxDeferredAssets()), "Tax Assumptions"));
        rows.add(new Row("State / local tax jurisdiction", "No named jurisdiction stored; configured state rate "
                + percent(tax.getStateIncomeTaxRate()) + "; local rate " + percent(tax.getLocalIncomeTaxRate()), "Tax Assumptions"));
        rows.add(new Row("Medicare / IRMAA assumptions", "Modeled automatically for alive, age-eligible people using annual federal tax results, filing status and projected government rules; no separate enrollment setting.", "Retirement Plan / Government rules"));
        rows.add(new Row("RMD assumptions", "Owner DOB, eligible account types and prior December 31 balances; "
                + portfolio.getAccounts().stream().filter(account -> account.getOpeningRmdAccountData() != null).count()
                + " accounts have opening RMD data. Subsequent years use projected snapshots.", "Retirement Plan / Government rules"));
        identity(rows, "Primary", plan.getHousehold().getPrimaryPerson());
        identity(rows, "Spouse", plan.getHousehold().getSpouse());
        for (var election : baseline.elections()) {
            rows.add(new Row(election.name(), election.value(), election.source()));
        }
        rows.add(new Row("Current Strategy Baseline", baseline.strategy().isPresent()
                ? "Complete; weighted comparison only, not candidate selection" : baseline.problem(), "Plan + Analyzer"));
        return new SocialSecurityAnalyzerInputSummary(rows);
    }

    private static void identity(List<Row> rows, String owner, Person person) {
        rows.add(new Row(owner + " DOB", person == null ? "Not configured" : date(person.getBirthDate()), "Retirement Plan"));
        var sources = person == null ? List.<SocialSecurityIncome>of() : person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance).map(SocialSecurityIncome.class::cast).toList();
        rows.add(new Row(owner + " FRA benefit / current retirement election", compact(sources.stream()
                .map(ss -> money(ss.getFullRetirementMonthlyBenefit()) + "/month (valuation year " + ss.getBenefitValuationYear()
                        + "); configured claim age " + ss.getClaimingAge() + "; current claim date " + date(ss.getStartDate())).toList()), "Social Security record"));
    }

    private static String mortalityCategory(Person person) {
        return person == null || person.getMortalityCategory() == null
                ? "Required - set in Person information" : human(person.getMortalityCategory());
    }

    private static String compact(List<String> values) {
        return values.isEmpty() ? "None configured" : values.size() + " configured: "
                + String.join("; ", values.stream().limit(3).toList())
                + (values.size() > 3 ? "; " + (values.size() - 3) + " more" : "");
    }
    private static String range(LocalDate start, LocalDate end) {
        return (start == null ? "no start limit" : start.toString()) + " to " + (end == null ? "no end limit" : end);
    }
    private static String date(LocalDate value) { return value == null ? "Missing / invalid" : value.toString(); }
    private static String factor(BigDecimal value) { return value == null ? "Missing / invalid" : value.stripTrailingZeros().toPlainString() + "x"; }
    private static String percent(BigDecimal value) { return value == null ? "Missing / invalid" : value.movePointRight(2).stripTrailingZeros().toPlainString() + "%"; }
    private static String money(BigDecimal value) { return value == null ? "Not configured" : "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String human(Enum<?> value) { return value == null ? "Not selected" : value.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT); }
}
