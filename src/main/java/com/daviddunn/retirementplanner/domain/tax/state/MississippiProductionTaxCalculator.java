package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Bounded actual-joint/Single estimate. Source evidence never creates another income channel. */
public final class MississippiProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final MississippiTaxRules rules = new MississippiTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.MS)
            throw new IllegalArgumentException("Mississippi calculator requires Mississippi residency.");
        if (request.taxYear() < 2026 || request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)
            return unsupported(request, "Mississippi supports Single/actual joint returns from 2026.");
        try { return calculateSupported(request); }
        catch (IllegalArgumentException unavailable) { return unsupported(request, unavailable.getMessage()); }
    }

    private ProductionStateTaxResult calculateSupported(StateTaxCalculationRequest request) {
        var household = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (filers.size() != (request.filingStatus() == FilingStatus.SINGLE ? 1 : 2)
                || !household.birthDates().keySet().containsAll(filers))
            return unsupported(request, "Mississippi requires complete annual filer birth dates and survivor context.");
        var income = request.income();
        var federal = request.federal();
        var ss = federal.getTaxableSocialSecurity();
        if (federal.getAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(ss)) != 0
                || ss.signum() < 0 || ss.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0)
            return unsupported(request, "Federal AGI and taxable Social Security do not reconcile.");
        var eligible = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        BigDecimal withdrawals = BigDecimal.ZERO, conversions = BigDecimal.ZERO, pensions = BigDecimal.ZERO;
        BigDecimal social = BigDecimal.ZERO, conditional = BigDecimal.ZERO;
        var seen = new HashSet<IncomeSourceAttribution>();
        var taxableOwners = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear())
                return unsupported(request, "Mississippi source attribution belongs to another year.");
            if (source.grossAmount().signum() == 0) continue;
            if (!seen.add(source)) return unsupported(request, "Duplicate annual source evidence.");
            if (source.owner().isPresent() && source.owner().get() != AccountOwnership.JOINT
                    && !household.activeOwners().contains(source.owner().get()))
                return unsupported(request, "Positive income belongs to an inactive/deceased recipient.");
            var taxable = source.modeledTaxableAmount();
            BigDecimal retirement = BigDecimal.ZERO;
            switch (source.kind()) {
                case ORDINARY_WITHDRAWAL, CAPITAL_GAIN, INTEREST, DIVIDEND -> { /* Reconciled once by the GA helper below. */ }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> {
                    if (taxable.isEmpty() || source.account().isEmpty())
                        return unsupported(request, "Retirement taxable amount/account attribution is required.");
                    var type = source.account().orElseThrow().type();
                    if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION
                            && type.getTaxTreatment() != TaxTreatment.TAX_DEFERRED)
                        return unsupported(request, "A represented conversion must originate from a traditional account, not qualified Roth cash.");
                    if (type == AccountType.INHERITED_TRADITIONAL_IRA || type == AccountType.INHERITED_ROTH_IRA)
                        return unsupported(request, "Inherited retirement qualification/history is outside the ordinary Mississippi subset.");
                    if (type.getTaxTreatment() == TaxTreatment.ROTH && taxable.orElseThrow().signum() == 0) continue;
                    if (taxable.orElseThrow().compareTo(source.grossAmount()) != 0
                            || !Set.of(AccountType.TRADITIONAL_IRA, AccountType.ROLLOVER_IRA,
                            AccountType.TRADITIONAL_401K, AccountType.TRADITIONAL_403B).contains(type))
                        return unsupported(request, "Only fully taxable ordinary traditional receipts and qualified Roth cash are admitted.");
                    if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION) {
                        if (!Set.of(AccountType.TRADITIONAL_IRA, AccountType.ROLLOVER_IRA).contains(type)
                                || source.purpose() != IncomeSourceAttribution.Purpose.CONVERSION
                                || source.destination().isEmpty() || source.destination().orElseThrow().type() != AccountType.ROTH_IRA)
                            return unsupported(request, "Only confirmed complete traditional IRA-to-Roth IRA conversions are admitted; employer conversions need review.");
                        conversions = conversions.add(taxable.orElseThrow());
                    } else withdrawals = withdrawals.add(taxable.orElseThrow());
                    retirement = taxable.orElseThrow();
                    if (source.kind() != IncomeSourceAttribution.Kind.ROTH_CONVERSION
                            && !new com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiRetirementEligibility()
                            .exemptCash(source, household)) {
                        var owner = source.owner().orElseThrow();
                        taxableOwners.merge(owner, retirement, BigDecimal::add);
                        retirement = BigDecimal.ZERO;
                    }
                }
                case PENSION -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0
                            || source.pension().isPresent() && source.pension().orElseThrow().originalOwner() == AccountOwnership.JOINT)
                        return unsupported(request, "Only fully taxable ordinary individual private pensions are admitted.");
                    if (source.pension().isPresent() && source.pension().get().participantFacts()
                            .retirementConditionsStatusOn(source.pension().get().startDate()
                                    .filter(date -> date.isAfter(household.distributionDate()))
                                    .orElse(household.distributionDate())) == RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET)
                        return unsupported(request, "Represented unmet pension retirement conditions require separate qualification.");
                    pensions = pensions.add(taxable.orElseThrow());
                    retirement = taxable.orElseThrow();
                }
                case NONQUALIFIED_ANNUITY -> {
                    if (source.conditionalBenefit().isEmpty() || source.conditionalBenefit().orElseThrow().annuityBasis() == null
                            || taxable.isEmpty()) return unsupported(request, "Annuity requires its already-admitted taxable/basis receipt facts.");
                    if (taxable.orElseThrow().signum() > 0)
                        return unsupported(request, "Commercial nonqualified annuity retirement qualification is unverified for Mississippi.");
                }
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM -> {
                    if (source.conditionalBenefit().isEmpty() || taxable.isEmpty())
                        return unsupported(request, "Conditional taxable receipts require reliable receipt facts.");
                    conditional = conditional.add(taxable.orElseThrow());
                    var owner = source.owner().orElse(null);
                    if (owner == null || !household.activeOwners().contains(owner))
                        return unsupported(request, "Conditional taxable income requires its active recipient.");
                    taxableOwners.merge(owner, taxable.orElseThrow(), BigDecimal::add);
                }
                case SOCIAL_SECURITY -> social = social.add(source.grossAmount());
                case OTHER -> { return unsupported(request, "Unclassified income cannot establish Mississippi treatment."); }
                case NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
            }
            if (retirement.signum() > 0) {
                var owner = source.owner().orElse(null);
                if (owner == null || !filers.contains(owner) || !household.activeOwners().contains(owner))
                    return unsupported(request, "Retirement income needs its actual active recipient; no household split is inferred.");
                eligible.merge(owner, retirement, BigDecimal::add);
            }
        }
        if (withdrawals.compareTo(income.getTaxDeferredWithdrawals()) != 0 || conversions.compareTo(income.getRothConversion()) != 0
                || pensions.compareTo(income.getPensionIncome()) != 0 || conditional.compareTo(income.getConditionalOrdinaryIncome()) != 0
                || social.compareTo(income.getSocialSecurityIncome()) > 0)
            return unsupported(request, "Mississippi retirement totals require complete attribution; other evidence must not exceed modeled income.");

        var investments = new com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiInvestmentReconciliation()
                .reconcile(income, household.activeOwners(), request.filingStatus() == FilingStatus.SINGLE);
        investments.forEach((owner, amount) -> taxableOwners.merge(owner, amount, BigDecimal::add));
        var allocated = investments.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var unallocated = income.getTaxableInterestIncome().subtract(allocated);
        var exclusion = eligible.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var base = federal.getAdjustedGrossIncome().subtract(ss).subtract(exclusion);
        if (base.compareTo(taxableOwners.values().stream().reduce(unallocated, BigDecimal::add)) != 0)
            return unsupported(request, "Mississippi state net income does not reconcile with actual recipients.");
        var deduction = rules.standardDeduction(request.filingStatus());
        var exemptions = rules.exemptions(request.taxYear(), request.filingStatus(), household, filers);
        var worksheet = new com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiMarriedWorksheet();
        var tax = worksheet.tax(request.taxYear(), request.filingStatus(), taxableOwners, unallocated, exemptions);
        var ambiguous = rules.ambiguousSeniorBirthdays(request.taxYear(), household, filers);
        if (ambiguous > 0 && tax.compareTo(worksheet.tax(request.taxYear(), request.filingStatus(), taxableOwners,
                unallocated, exemptions.add(new BigDecimal("1500").multiply(BigDecimal.valueOf(ambiguous))))) != 0)
            return unsupported(request, "Mississippi senior birthday convention at year-end or January 1 death is unresolved and materially changes tax.");
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Mississippi base: modeled AGI " + federal.getAdjustedGrossIncome().toPlainString()
                + "; taxable Social Security subtraction " + ss.toPlainString() + "; individual eligible retirement receipts " + eligible
                + "; capped retirement subtraction " + exclusion.toPlainString() + "; one state deduction " + deduction.toPlainString()
                + "; taxable income " + base.toPlainString()
                + "; exemptions " + exemptions.toPlainString() + "; net by recipient " + taxableOwners
                + "; unallocated investment " + unallocated.toPlainString()
                + "; method " + (unallocated.signum() > 0 ? "one-column joint fallback (Single: sole filer)" : "actual recipient columns")
                + "; rate " + rules.rate(request.taxYear()) + "; rule version " + MississippiTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.MS, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Mississippi disclosure year range.");
        return List.of(
                "Mississippi full-year domestic Single/MFJ ordinary-retirement estimate. Reconciles modeled federal AGI to Mississippi own net-income rules by removing federally included Social Security, qualifying retirement payments and separately exempt completed traditional IRA-to-Roth IRA conversions. State deductions $2,300/$4,600; exemptions $6,000/$12,000 plus $1,500 per eligible age-65 filer. No federal below-AGI deduction is imported.",
                "Traditional IRA-to-Roth IRA conversions follow Code 27-7-15(4)(ff), including properly completed conversions before age 59 1/2. Only actually executed federally included amounts are removed once. Retained cash/tax-funding withdrawals are separate receipts. Employer-to-Roth, in-plan, inherited and partial-basis variants are unsupported without independent qualification.",
                "Ordinary qualified private-plan assumptions apply to pensions and eligible retirement payments; adverse dated facts override them. IRA cash must qualify throughout its receipt interval; clearly early unqualified cash remains taxable. Crossing age 59 1/2 or retirement eligibility with unknown payment timing is unsupported. A federal penalty exception alone does not establish state qualification. Qualified survivor pension exemption uses original-plan facts and current recipient, without an invented beneficiary age minimum. Unverified commercial nonqualified annuity retirement qualification is unsupported.",
                "Brokerage proceeds are cash flow, not automatically taxable income. Uses already-modeled taxable investment income once; unknown basis alone is admitted. Unmodeled gains may understate tax; specialized securities, state-source gains, Treasury/municipal and basis/loss adjustments can change actual tax and are not modeled.",
                "Married computation uses actual owned state net income and flexible ordinary deductions/exemptions in combined columns; unused $10,000 zero bands never transfer and one person income is not divided in half. Unallocated MFJ ordinary investment income, including JOINT evidence without individual shares, uses the one-column joint fallback and may overstate actual tax versus an accurately attributed combined return. Single uses the uncontradicted sole-filer convention.",
                "Rule version " + MississippiTaxRules.VERSION + ": 4% in 2026, 3.75% in 2027, 3.50% in 2028, 3.25% in 2029, 3% from 2030 above each applicable $10,000 zero band. Hold 3% after 2030 until a reviewed legally established reduction; conditional reserve/revenue triggers are not forecast. Deductions/exemptions stay nominal. A material unresolved January 1 senior-birthday convention returns unavailable.",
                "Standard-deduction-only nondependent model; no itemization, blind/dependent benefits, ancillary credits, specialized government/disability/service or business/foreign adjustments, part-year/multistate treatment. Federal early-distribution additional taxes and nonqualified Roth/basis exceptions are not newly modeled. Exact intermediates/final HALF_UP cents differ from whole-dollar returns; existing federal/annuity/NIIT safeguards remain.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.MS, request.assumptions().calculationMethod(),
                List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), explanation);
    }
}
