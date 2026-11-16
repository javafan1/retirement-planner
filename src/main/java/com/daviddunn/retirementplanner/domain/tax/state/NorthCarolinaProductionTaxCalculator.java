package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.northcarolina.NorthCarolinaTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded full-year resident estimate; see PHASE-6E-C-NORTH-CAROLINA-TAX-AUDIT.md. */
public final class NorthCarolinaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final NorthCarolinaTaxRules rules = new NorthCarolinaTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.NC) {
            throw new IllegalArgumentException("North Carolina calculator requires North Carolina residency.");
        }
        if (request.taxYear() < 2026 || (request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "North Carolina supports Single/MFJ for 2026 onward only.");
        }
        var income = request.income();
        var federal = request.federal();
        BigDecimal agi = federal.getAdjustedGrossIncome();
        BigDecimal taxableSs = federal.getTaxableSocialSecurity();
        if (agi.compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(taxableSs)) != 0
                || taxableSs.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Modeled federal AGI and taxable Social Security do not reconcile with annual income.");
        }
        BigDecimal investments = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        BigDecimal conversions = BigDecimal.ZERO;
        BigDecimal pensions = BigDecimal.ZERO;
        BigDecimal socialSecurity = BigDecimal.ZERO;
        BigDecimal conditional = BigDecimal.ZERO;
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                        "North Carolina source attribution belongs to another tax year.");
            }
            if (source.grossAmount().signum() == 0) continue;
            var taxable = source.modeledTaxableAmount();
            switch (source.kind()) {
                // Gain/basis evidence does not create a second federal income channel.
                case CAPITAL_GAIN -> investments = investments.add(taxable.orElse(BigDecimal.ZERO));
                case INTEREST, DIVIDEND -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0) {
                        return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                "Special/exempt investment income requires North Carolina source adjustment facts.");
                    }
                    investments = investments.add(taxable.orElseThrow());
                }
                case ORDINARY_WITHDRAWAL -> {
                    // Empty means the model did not calculate a taxable gain, not
                    // that gross proceeds should be added to income or refused.
                    // An explicitly represented taxable portion must still reconcile
                    // with the existing modeled investment-income category below.
                    investments = investments.add(taxable.orElse(BigDecimal.ZERO));
                }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> {
                    if (taxable.isEmpty()) {
                        return unsupported(request, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                                "Retirement taxable amount is unavailable; North Carolina cannot infer basis.");
                    }
                    if (source.account().isPresent()
                            && source.account().orElseThrow().type().getTaxTreatment()
                            == com.daviddunn.retirementplanner.domain.model.TaxTreatment.ROTH
                            && taxable.orElseThrow().signum() > 0) {
                        return unsupported(request, StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED,
                                "Explicitly taxable Roth distributions are outside the qualified-Roth planning scope.");
                    }
                    if (taxable.orElseThrow().signum() > 0
                            && taxable.orElseThrow().compareTo(source.grossAmount()) != 0) {
                        return unsupported(request, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                                "Partially taxable traditional receipts require basis treatment outside the fully taxable planning scope.");
                    }
                    if (source.kind() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                        conversions = conversions.add(taxable.orElseThrow());
                    } else {
                        withdrawals = withdrawals.add(taxable.orElseThrow());
                    }
                }
                case PENSION -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0) {
                        return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                "Only modeled fully taxable private pension income is supported.");
                    }
                    pensions = pensions.add(taxable.orElseThrow());
                }
                case OTHER -> {
                    return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE,
                            "Unclassified income cannot establish North Carolina adjustment treatment.");
                }
                // Evidence is reconciled, never added to the federal base again.
                case SOCIAL_SECURITY -> socialSecurity = socialSecurity.add(source.grossAmount());
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM, NONQUALIFIED_ANNUITY ->
                        conditional = conditional.add(taxable.orElseThrow());
                case NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
            }
        }
        if (investments.compareTo(income.getTaxableInterestIncome()) > 0
                || withdrawals.compareTo(income.getTaxDeferredWithdrawals()) > 0
                || conversions.compareTo(income.getRothConversion()) > 0
                || pensions.compareTo(income.getPensionIncome()) > 0
                || socialSecurity.compareTo(income.getSocialSecurityIncome()) > 0
                || conditional.compareTo(income.getConditionalOrdinaryIncome()) > 0) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Attributed income exceeds its modeled annual category; no extra income was invented.");
        }
        var context = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE
                ? context.activeOwners() : context.householdOwners();
        int requiredFilers = request.filingStatus() == FilingStatus.SINGLE ? 1 : 2;
        if (filers.size() != requiredFilers) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "North Carolina filing status requires complete annual filer context.");
        }
        BigDecimal standard = rules.standardDeduction(request.filingStatus());
        BigDecimal rate = rules.rate(request.taxYear());
        BigDecimal taxableIncome = agi.subtract(taxableSs).subtract(standard).max(BigDecimal.ZERO);
        BigDecimal tax = taxableIncome.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual North Carolina base: modeled federal AGI " + agi.toPlainString()
                + "; federally taxable Social Security subtraction " + taxableSs.toPlainString()
                + "; state standard deduction " + standard.toPlainString()
                + "; taxable income " + taxableIncome.toPlainString()
                + "; rate " + rate.toPlainString() + "; rule version " + NorthCarolinaTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.NC, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid North Carolina disclosure year range.");
        return List.of(
                "North Carolina full-year domestic Single/MFJ retirement-planning estimate. Uses modeled federal AGI, subtracts federally taxable Social Security, and applies the state standard deduction: $12,750 Single/$25,500 MFJ. Ordinary fully taxable private pensions, traditional distributions, RMDs and taxable Roth conversions remain included once.",
                "Brokerage withdrawal proceeds are cash flow, not automatically taxable income. Uses taxable investment income already modeled. Unmodeled realized gains may cause actual taxes to be higher; security lots, capital losses/carryovers and special Treasury/municipal or state-basis adjustments are not modeled.",
                "Rule version " + NorthCarolinaTaxRules.VERSION + ": enacted rates 3.99% in 2026, 3.49% in 2027-2029, 3.24% in 2030-2032 and 2.99% from 2033. Conditional 2035-2040 reductions are not assumed. Statutory state standard deductions remain nominally constant under reviewed law; future legislative changes require reviewed rules updates.",
                "Bailey-qualified government benefits, eligible military/railroad benefits and special rollover/conversion exclusions require separate consideration and may receive different tax treatment. Pension names do not establish eligibility; this estimate assumes ordinary fully taxable private pensions and does not determine historical government-plan qualification.",
                "Standard-deduction-only estimate for eligible nondependent filers. Itemization, dependent/child benefits, specialized credits, business/portable-benefit adjustments, part-year residence and multistate sourcing/credits are outside scope. Assumes fully taxable traditional receipts and qualified Roth withdrawals; existing annuity/NIIT admission safeguards still apply. Federal below-AGI senior/other deductions are not North Carolina deductions; existing federal-model omissions can affect combined outcomes. Exact intermediates and final HALF_UP cents differ from return whole-dollar rounding.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request,
            StateTaxUnavailableReason reason, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.NC,
                request.assumptions().calculationMethod(), List.of(reason), explanation);
    }
}
