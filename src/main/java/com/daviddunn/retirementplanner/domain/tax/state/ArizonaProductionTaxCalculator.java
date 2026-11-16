package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.arizona.ArizonaRetirementAdjustmentsCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded domestic full-year estimate. See PHASE-6E-B2-ARIZONA-ADMISSION.md for rule derivation. */
public final class ArizonaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private static final String RULE_VERSION = "AZ-2026-1";
    // ARS 43-1041(A,H) adopts federal-style inflation; Rev. Proc. 2025-32 .14
    // establishes the 2026 adjustment of the enacted 2025 basic deduction.
    private static final BigDecimal SINGLE_DEDUCTION = new BigDecimal("16100");
    private static final BigDecimal JOINT_DEDUCTION = new BigDecimal("32200");
    private static final BigDecimal RATE = new BigDecimal("0.025");
    private final ArizonaRetirementAdjustmentsCalculator adjustments =
            new ArizonaRetirementAdjustmentsCalculator();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.AZ) {
            throw new IllegalArgumentException("Arizona calculator requires Arizona residency.");
        }
        if (request.taxYear() < 2026 || (request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Arizona supports Single/MFJ for 2026 onward only.");
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
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear()) {
                return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                        "Arizona source attribution belongs to another tax year.");
            }
            if (source.grossAmount().signum() == 0) continue;
            var taxable = source.modeledTaxableAmount();
            switch (source.kind()) {
                // Gain/basis metadata does not create federal income. Retain the
                // modeled AGI and do not apply an unverified Arizona gain subtraction.
                case CAPITAL_GAIN -> investments = investments.add(taxable.orElse(BigDecimal.ZERO));
                case INTEREST, DIVIDEND -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0) {
                        return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                                "Special/exempt investment income requires Arizona source adjustment facts.");
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
                                "Retirement taxable amount is unavailable; Arizona cannot infer basis.");
                    }
                    if (source.account().isPresent()
                            && source.account().orElseThrow().type().getTaxTreatment()
                            == com.daviddunn.retirementplanner.domain.model.TaxTreatment.ROTH
                            && taxable.orElseThrow().signum() > 0) {
                        return unsupported(request, StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED,
                                "Explicitly taxable Roth distributions are outside the qualified-Roth planning scope.");
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
                            "Unclassified income cannot establish Arizona adjustment treatment.");
                }
                // Selected annual SS and admitted conditional/annuity taxable receipts
                // are already included in the reconciled modeled income. No second addition.
                case SOCIAL_SECURITY, CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM,
                        NONQUALIFIED_ANNUITY, NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
            }
        }
        if (investments.compareTo(income.getTaxableInterestIncome()) > 0
                || withdrawals.compareTo(income.getTaxDeferredWithdrawals()) > 0
                || conversions.compareTo(income.getRothConversion()) > 0
                || pensions.compareTo(income.getPensionIncome()) > 0) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Attributed income exceeds its modeled annual category; no extra income was invented.");
        }
        var context = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE
                ? context.activeOwners() : context.householdOwners();
        int requiredFilers = request.filingStatus() == FilingStatus.SINGLE ? 1 : 2;
        if (filers.size() != requiredFilers) {
            return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                    "Arizona filing status requires complete annual filer context.");
        }
        int seniors = 0;
        for (AccountOwnership owner : filers) {
            LocalDate birth = context.birthDates().get(owner);
            LocalDate ageDate = context.activeOwners().contains(owner)
                    ? LocalDate.of(request.taxYear(), 12, 31) : LocalDate.of(request.taxYear(), 1, 1);
            if (birth == null || birth.isAfter(ageDate)) {
                return unsupported(request, StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE,
                        "Arizona age exemptions require a valid birth date for each annual filer.");
            }
            // Age is attained on the day before the birthday. January 1 births
            // qualify December 31; deceased filers cannot attain a later age.
            if (!birth.plusYears(65).minusDays(1).isAfter(ageDate)) seniors++;
        }
        var base = adjustments.calculate(request.taxYear(), request.filingStatus(), agi, taxableSs, seniors);
        BigDecimal standard = request.filingStatus() == FilingStatus.SINGLE ? SINGLE_DEDUCTION : JOINT_DEDUCTION;
        BigDecimal taxableIncome = base.signedAdjustedIncome().max(BigDecimal.ZERO)
                .subtract(standard).max(BigDecimal.ZERO);
        BigDecimal tax = taxableIncome.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Arizona base: federally taxable Social Security subtraction " + taxableSs.toPlainString()
                + "; age exemption " + base.ageExemption().toPlainString()
                + "; enhanced senior subtraction " + base.enhancedSeniorSubtraction().toPlainString()
                + "; basic deduction " + standard.toPlainString() + "; taxable income " + taxableIncome.toPlainString() + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.AZ, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Arizona disclosure year range.");
        return List.of(
                "Arizona full-year domestic Single/MFJ planning estimate; private pensions are taxable. Specialized government and military pension exclusions, itemization, charity increases and credits/refunds are not modeled. Pension names do not establish classification.",
                "Uses modeled federal AGI and modeled taxable investment income; brokerage withdrawal proceeds are not automatically taxable income. Unmodeled realized gains are not included in the estimated tax base and may cause actual taxes to be higher. Unverified Arizona-specific qualifying capital-gain subtractions are not applied, potentially overestimating tax when applicable. Assumes valid senior SSNs, nondependent filers, domestic MAGI equal to AGI, fully taxable traditional receipts and qualified Roth withdrawals. Special Treasury/municipal source adjustments remain outside this estimate.",
                "Rule version " + RULE_VERSION + ": 2.5% rate; nominal 2026 basic deductions $16,100 Single/$32,200 MFJ and $2,100 age exemption held constant in later years as a planning approximation to indexed law. Enhanced senior subtraction expires after 2028. Taxable income is floored at zero; exact intermediate amounts and final HALF_UP cents differ from return line-by-line whole-dollar rounding. Federal senior deductions remain outside the existing federal model.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request,
            StateTaxUnavailableReason reason, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.AZ,
                request.assumptions().calculationMethod(), List.of(reason), explanation);
    }
}
