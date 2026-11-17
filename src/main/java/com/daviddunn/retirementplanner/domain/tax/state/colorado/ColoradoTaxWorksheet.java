package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.util.List;

/**
 * Isolated 2026 worksheet, deliberately not StateIncomeTaxCalculator or a production route.
 * Conversion/future-law gates are not bypassed by this testable composition of verified rules.
 */
public final class ColoradoTaxWorksheet {
    public record Result(ColoradoFederalTaxBaseBridge.Base base,
            ColoradoRetirementAdjustments.Adjustments retirement, BigDecimal taxableIncome,
            BigDecimal tax, String ruleVersion, List<String> limitations) {
        public Result { limitations = List.copyOf(limitations); }
    }

    public Result calculate(int year, FilingStatus status, TaxHouseholdContext household,
            TaxIncome income, FederalTaxCalculation federal) {
        ColoradoTaxRules.requireVerifiedYear(year);
        var receipts = new ColoradoIncomeReconciliation().reconcile(year, status, household, income, federal);
        var base = new ColoradoFederalTaxBaseBridge().calculate(year, status, household, federal);
        var retirement = new ColoradoRetirementAdjustments().calculate(year, status, household,
                federal.getAdjustedGrossIncome(), federal.getTaxableSocialSecurity(),
                receipts.grossSocialSecurity(), receipts.eligibleOtherRetirement());
        var taxable = base.beforeStateRetirementSubtractions().subtract(retirement.totalSubtraction()).max(BigDecimal.ZERO);
        return new Result(base, retirement, taxable, new ColoradoTaxRules().tax(year, taxable),
                ColoradoTaxRules.VERSION, limitations());
    }

    public static List<String> limitations() {
        return List.of(
                "Colorado PREPARATORY 2026 worksheet only; not available in production. Full-year domestic nondependent Single/MFJ ordinary retirement subset. Individual $20,000 age-55/64 and $24,000 age-65+ pools do not transfer; proportional taxable Social Security uses each recipient's pool first, subject to full-SS age/AGI exceptions.",
                "Brokerage proceeds are cash flow, not automatically taxable income. Only already-modeled investment income enters the base. Unmodeled realized gains may cause actual taxes to be higher; special sources, state basis, losses and principal-residence gains need separate consideration. No security-lot accounting or invented gains.",
                "2026 worksheet rate 4.4%; no 2027+ tax schedule or conditional TABOR reduction is assumed while certified election/effective law remains unresolved. Rule version " + ColoradoTaxRules.VERSION + ".",
                "Current official rule confirmation for IRA conversions remains pending. Conversions, employer-plan periodicity, inherited histories, premature/age-59.5 crossing IRA receipts, under-55 special survivor benefits and annuities lacking verified full maturity are unavailable. Pension names establish no qualification; ordinary pensions assume a qualifying private periodic plan. Specialized military, railroad and government adjustments are outside scope.",
                "CO-only federal-base bridge includes one published basic deduction, age additions and eligible temporary enhanced senior deduction/phaseout (expires after 2028), plus the standard-deduction addback above $300,000 AGI. Original federal taxes are unchanged. Requires valid SSNs and domestic MAGI equal to AGI; itemization, blindness, foreign/business adjustments, other deductions and credits are excluded. Future unpublished deductions require separate review. Signed intermediates are retained and final planning tax uses HALF_UP cents.");
    }
}
