package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.time.Instant;
import java.util.*;

/** Admission-time display values only; no mutable plan or controls survive capture. */
record IndividualReportContext(String household, String primary, Instant started, List<Section> assumptions) {
    IndividualReportContext { assumptions = List.copyOf(assumptions); }

    private static String date(java.time.LocalDate value, String absent) { return value == null ? absent : value.toString(); }

    static IndividualReportContext capture(RetirementPlan plan) {
        if (plan.getHousehold().hasSpouse()) throw new IllegalArgumentException("Individual report requires one person.");
        var person = plan.getHousehold().getPrimaryPerson();
        var a = plan.getPlanningAssumptions();
        var t = a.getTaxAssumptions();
        var lines = new ArrayList<String>();
        lines.add("Primary: " + person.getFullName() + "; DOB: " + person.getBirthDate());
        lines.add("Projection start: " + a.getProjectionStartDate() + "; configured calendar years: " + a.getProjectionLengthYears());
        lines.add("Investment return: " + UIFormatters.percent(a.getInvestmentReturnRate())
                + "; General Inflation: " + UIFormatters.percent(a.getGeneralInflationRate())
                + "; Healthcare Inflation: " + UIFormatters.percent(a.getHealthcareInflationRate())
                + "; Social Security COLA: " + UIFormatters.percent(a.getSocialSecurityColaRate()));
        lines.add("Filing status: " + t.getFilingStatus() + "; State / local tax rates: "
                + UIFormatters.percent(t.getStateIncomeTaxRate()) + " / " + UIFormatters.percent(t.getLocalIncomeTaxRate())
                + "; Estimated heir tax rate: " + UIFormatters.percent(t.getEstimatedHeirTaxRateOnTaxDeferredAssets()));
        lines.add("Federal bracket / deduction growth: " + UIFormatters.percent(t.getFederalTaxBracketGrowthRate())
                + " / " + UIFormatters.percent(t.getStandardDeductionGrowthRate())
                + "; future marginal adjustment: " + UIFormatters.percent(t.getFutureFederalMarginalRateAdjustment())
                + "; effective year: " + (t.getFutureFederalMarginalRateEffectiveYear() == null ? "not scheduled" : t.getFutureFederalMarginalRateEffectiveYear()));
        for (var income : person.getIncomeSources()) {
            if (income instanceof SocialSecurityIncome ss) lines.add("Primary Social Security: FRA monthly benefit "
                    + UIFormatters.money(ss.getFullRetirementMonthlyBenefit()) + "; current claiming age " + ss.getClaimingAge()
                    + "; derived start " + ss.getStartDate() + "; benefit valuation year " + ss.getBenefitValuationYear());
            if (income instanceof Pension p) lines.add("Primary pension: " + UIFormatters.money(p.getMonthlyBenefit())
                    + "/month; COLA " + UIFormatters.percent(p.getAnnualColaRate()) + "; " + date(p.getStartDate(), "projection start") + " through " + date(p.getEndDate(), "no configured end"));
        }
        for (var account : plan.getAccountPortfolio().getAccounts()) lines.add("Account: " + account.getOwnership()
                + "; " + account.getType() + "; " + UIFormatters.money(account.getCurrentBalance()));
        for (var e : plan.getHousehold().getExpenses()) lines.add("Expense: " + e.getDescription() + "; "
                + UIFormatters.money(e.getAnnualAmount()) + "/year; " + e.getExpenseType() + "; " + e.getGrowthCategory()
                + "; " + date(e.getStartDate(), "projection start") + " through " + date(e.getEndDate(), "no configured end"));
        var roth = plan.getRothConversionRequest();
        lines.add("Roth conversions: " + (roth == null || !roth.isEnabled() ? "Disabled" : roth.getStrategy()
                + switch (roth.getStrategy()) {
                    case FIXED_AMOUNT -> "; requested amount " + UIFormatters.money(roth.getAnnualAmount());
                    case CUSTOM_TAXABLE_INCOME_TARGET -> "; target taxable income " + UIFormatters.money(roth.getCustomTargetTaxableIncome());
                    default -> "; amount determined by available bracket room";
                } + "; start " + roth.getStartYear()
                + "; " + roth.getFrequency() + "; stop " + roth.getStopRule()));
        lines.add("RMDs use eligible owned accounts and prior December 31 projected balances, including configured opening RMD data.");
        lines.add("Medicare/IRMAA uses the existing modeled same-year AGI and applicable filing status for the actual eligible person.");
        return new IndividualReportContext(plan.getHousehold().getHouseholdName(), person.getFullName(), Instant.now(),
                List.of(new Section("Result-Time Plan Assumptions", lines)));
    }
}
