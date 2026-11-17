package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaTaxRules;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded ordinary resident estimate; see Phase 6E-I independent Virginia worksheets. */
public final class VirginiaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final VirginiaTaxRules rules = new VirginiaTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.VA) {
            throw new IllegalArgumentException("Virginia calculator requires Virginia residency.");
        }
        if (request.taxYear() < 2026 || (request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Virginia supports Single/MFJ for 2026 onward only.");
        }
        var income = request.income();
        var federal = request.federal();
        BigDecimal agi = federal.getAdjustedGrossIncome();
        BigDecimal taxableSs = federal.getTaxableSocialSecurity();
        if (agi.compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(taxableSs)) != 0
                || taxableSs.signum() < 0
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
        BigDecimal investmentPlaceholder = null;
        BigDecimal explicitInterest = BigDecimal.ZERO;
        var seenInvestments = new java.util.HashSet<com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution>();
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                        "Virginia source attribution belongs to another tax year.");
            }
            if (source.grossAmount().signum() == 0) continue;
            var taxable = source.modeledTaxableAmount();
            if (java.util.Set.of(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.CAPITAL_GAIN,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.DIVIDEND,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL).contains(source.kind())) {
                if (!seenInvestments.add(source)) return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE, "Duplicate investment evidence.");
                if (taxable.orElse(BigDecimal.ZERO).signum() > 0 && source.account().isPresent()
                        && seenInvestments.stream().anyMatch(previous -> previous.account().equals(source.account())
                        && previous.modeledTaxableAmount().orElse(BigDecimal.ZERO).signum() > 0
                        && (previous.kind() == Kind.CAPITAL_GAIN && source.kind() == Kind.ORDINARY_WITHDRAWAL
                            || previous.kind() == Kind.ORDINARY_WITHDRAWAL && source.kind() == Kind.CAPITAL_GAIN))) {
                    return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                            "Overlapping gain/withdrawal evidence cannot establish disjoint taxable investment receipts.");
                }
                if (source.account().isPresent() && (source.account().get().type().getTaxTreatment()==com.daviddunn.retirementplanner.domain.model.TaxTreatment.TAX_DEFERRED
                        || source.account().get().type().getTaxTreatment()==com.daviddunn.retirementplanner.domain.model.TaxTreatment.ROTH && taxable.orElse(BigDecimal.ZERO).signum()>0))
                    return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE, "Retirement account conflicts with investment classification.");
            }
            if (source.kind()==com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST
                    && source.owner().isEmpty() && source.account().isEmpty() && source.destination().isEmpty()
                    && source.purpose()==com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.INCOME
                    && source.description().equals("Unattributed INTEREST")) {
                if (investmentPlaceholder!=null || taxable.isEmpty() || taxable.get().compareTo(source.grossAmount())!=0)
                    return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE, "Ambiguous investment aggregate.");
                investmentPlaceholder=taxable.get();
                continue;
            }
            if (source.kind()==com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST)
                explicitInterest=explicitInterest.add(taxable.orElse(BigDecimal.ZERO));
            switch (source.kind()) {
                // Gain/basis evidence does not create a second federal income channel.
                case CAPITAL_GAIN -> investments = investments.add(taxable.orElse(BigDecimal.ZERO));
                case INTEREST, DIVIDEND -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0) {
                        return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                "Special/exempt investment income requires Virginia source adjustment facts.");
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
                    if (source.account().isPresent()) {
                        var treatment = source.account().orElseThrow().type().getTaxTreatment();
                        if (source.kind() == Kind.ROTH_CONVERSION
                                && treatment != com.daviddunn.retirementplanner.domain.model.TaxTreatment.TAX_DEFERRED) {
                            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                    "A represented conversion must originate from a traditional account, not qualified Roth cash.");
                        }
                        if (treatment != com.daviddunn.retirementplanner.domain.model.TaxTreatment.TAX_DEFERRED
                                && treatment != com.daviddunn.retirementplanner.domain.model.TaxTreatment.ROTH) {
                            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                    "Represented account is incompatible with retirement receipt classification.");
                        }
                        if (source.kind() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION
                                && (source.purpose() != com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CONVERSION
                                || source.destination().isEmpty()
                                || source.destination().orElseThrow().type() != com.daviddunn.retirementplanner.domain.model.AccountType.ROTH_IRA)) {
                            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                    "Represented conversion must be a complete same-owner transfer to a Roth IRA.");
                        }
                    }
                    if (taxable.isEmpty()) {
                        return unsupported(request, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS,
                                "Retirement taxable amount is unavailable; Virginia cannot infer basis.");
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
                            "Unclassified income cannot establish Virginia adjustment treatment.");
                }
                // Evidence is reconciled, never added to the federal base again.
                case SOCIAL_SECURITY -> socialSecurity = socialSecurity.add(source.grossAmount());
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM, NONQUALIFIED_ANNUITY ->
                        conditional = conditional.add(taxable.orElseThrow());
                case NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
            }
        }
        if (investmentPlaceholder != null && investmentPlaceholder.add(explicitInterest).compareTo(income.getTaxableInterestIncome()) != 0
                || investments.compareTo(income.getTaxableInterestIncome()) > 0
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
        if (filers.size() != requiredFilers || !context.birthDates().keySet().containsAll(filers)) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Virginia filing status requires complete annual filer context.");
        }
        var afagi = agi.subtract(taxableSs);
        var age = new com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaAgeDeduction()
                .calculate(request.taxYear(), request.filingStatus(), context, filers, afagi);
        BigDecimal standard = rules.standardDeduction(request.taxYear(), request.filingStatus());
        BigDecimal adjusted = afagi.subtract(age.deduction());
        BigDecimal taxableIncome = adjusted.subtract(standard).subtract(age.exemptions()).max(BigDecimal.ZERO);
        BigDecimal tax = (adjusted.compareTo(rules.noTaxThreshold(request.filingStatus()))<0 ? BigDecimal.ZERO : rules.tax(taxableIncome))
                .setScale(2, RoundingMode.HALF_UP);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Virginia base: modeled federal AGI " + agi.toPlainString()
                + "; federally taxable Social Security subtraction " + taxableSs.toPlainString()
                + "; state standard deduction " + standard.toPlainString()
                + "; taxable income " + taxableIncome.toPlainString()
                + "; age deduction " + age.deduction().toPlainString() + "; exemptions " + age.exemptions().toPlainString() + "; rule version " + VirginiaTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.VA, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Virginia disclosure year range.");
        return List.of(
                "Virginia full-year domestic Single/MFJ retirement-planning estimate. Uses modeled federal AGI, subtracts federally taxable Social Security, and applies Virginia standard deduction and $930 per nondependent filer/$800 per qualifying age-65 filer exemptions. Ordinary IRA/employer withdrawals, RMDs, private pensions, taxable annuities and actual Roth conversions remain included once; qualified Roth cash stays outside AGI.",
                "Virginia age deduction: qualifying DOB on/before January 1, 1939 receives $12,000 without income phaseout. Later eligible filers use the official income-tested worksheet: $50,000 Single/$75,000 joint AFAGI threshold, reducing the combined eligible spouse pool once. Social Security is removed for AFAGI. A conversion can reduce this deduction as well as add taxable income; the separate $800 exemption remains. Applies the strict $11,950 Single/$23,900 joint no-tax threshold before standard deduction/exemptions.",
                "Brokerage proceeds are cash flow, not automatically taxable income. Uses modeled taxable investment income once; unknown basis alone is admitted. Unmodeled realized gains may cause actual taxes to be higher. Security lots, losses/carryovers and special investment-source/state-basis adjustments are not modeled.",
                "Rule version " + VirginiaTaxRules.VERSION + ": nominal 2%/3%/5%/5.75% bands. Enacted Single/MFJ standard deductions: 2026 $8,750/$17,500; 2027 $9,200/$18,400; 2028-2029 $9,300/$18,600; statutory 2030 onward reversion $3,000/$6,000. No silent extension or inflation indexing. Future legislation requires reviewed updates.",
                "Virginia spouse tax adjustment of up to $259 is omitted. Eligible two-income married households may pay less Virginia tax than this estimate. No arbitrary ownership split is used to claim it; this limitation is retained with results.",
                "Standard-deduction-only estimate for ordinary private benefits; no itemization, military/railroad/disability qualification, dependent/blindness benefits, ancillary credits, business/foreign adjustments, part-year residence or multistate sourcing. Pension names do not determine special classification. Federal below-AGI basic/age/enhanced senior deductions are not Virginia deductions. Existing federal/annuity/NIIT safeguards remain; exact intermediates/final HALF_UP cents differ from whole-dollar returns.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request,
            StateTaxUnavailableReason reason, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.VA,
                request.assumptions().calculationMethod(), List.of(reason), explanation);
    }
}
