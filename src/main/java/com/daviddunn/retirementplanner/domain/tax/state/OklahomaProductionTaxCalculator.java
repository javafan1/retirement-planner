package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Bounded actual-joint/Single estimate. Source evidence never creates another income channel. */
public final class OklahomaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final OklahomaTaxRules rules = new OklahomaTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.OK)
            throw new IllegalArgumentException("Oklahoma calculator requires Oklahoma residency.");
        if (request.taxYear() < 2026 || request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)
            return unsupported(request, "Oklahoma supports Single/actual joint returns from 2026.");
        try { return calculateSupported(request); }
        catch (IllegalArgumentException unavailable) { return unsupported(request, unavailable.getMessage()); }
    }

    private ProductionStateTaxResult calculateSupported(StateTaxCalculationRequest request) {
        var household = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (filers.size() != (request.filingStatus() == FilingStatus.SINGLE ? 1 : 2)
                || !household.birthDates().keySet().containsAll(filers))
            return unsupported(request, "Oklahoma requires complete annual filer birth dates and survivor context.");
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
                return unsupported(request, "Oklahoma source attribution belongs to another year.");
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
                        return unsupported(request, "Inherited retirement qualification/history is outside the ordinary Oklahoma subset.");
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
                    // Commercial nonqualified annuity is investment income, not a qualified retirement plan.
                    conditional = conditional.add(taxable.orElseThrow());
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
                case OTHER -> { return unsupported(request, "Unclassified income cannot establish Oklahoma treatment."); }
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
            return unsupported(request, "Oklahoma retirement totals require complete attribution; other evidence must not exceed modeled income.");

        new com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaInvestmentReconciliation()
                .reconcile(income, household.activeOwners(), request.filingStatus() == FilingStatus.SINGLE);
        var exclusion = eligible.values().stream().map(v -> v.min(OklahomaTaxRules.RETIREMENT_LIMIT))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var deduction = rules.standardDeduction(request.taxYear(), request.filingStatus());
        var exemptions = rules.exemptions(request.taxYear(), request.filingStatus(), household, filers,
                federal.getAdjustedGrossIncome(), conversions);
        var base = federal.getAdjustedGrossIncome().subtract(ss).subtract(exclusion)
                .subtract(deduction).subtract(exemptions).max(BigDecimal.ZERO);
        var tax = rules.tax(request.taxYear(), request.filingStatus(), base);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Oklahoma base: modeled AGI " + federal.getAdjustedGrossIncome().toPlainString()
                + "; taxable Social Security subtraction " + ss.toPlainString() + "; individual eligible retirement receipts " + eligible
                + "; capped retirement subtraction " + exclusion.toPlainString() + "; one state deduction " + deduction.toPlainString()
                + "; taxable income " + base.toPlainString()
                + "; ordinary/senior exemptions " + exemptions.toPlainString()
                + "; statutory marginal bands; rule version " + OklahomaTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.OK, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Oklahoma disclosure year range.");
        return List.of(
                "Oklahoma full-year domestic Single/MFJ planning estimate: modeled federal AGI less federally taxable Social Security, state standard deduction $6,350/$12,700 and $1,000 per nondependent filer. One $10,000 retirement pool per recipient covers ordinary qualified private pension, traditional/employer withdrawals, RMDs and completed traditional IRA-to-Roth IRA conversions. Unused caps never transfer.",
                "IRA conversion eligibility is the approved application of the statutory IRA-distribution definition, not a conversion-specific OTC ruling. Conversions share the ordinary pool; no general age 59 1/2 or 65 gate applies to that pool. The additional $1,000 age-65 exemption uses $15,000 Single/$25,000 MFJ federal-AGI ceilings after removing qualifying IRA conversion income solely for that test.",
                "Brokerage proceeds are cash flow, not automatically taxable income. Uses only modeled taxable investment income once; unknown basis alone is admitted. Unmodeled realized gains may cause actual tax to be higher. Security lots, losses and special Treasury/municipal, Oklahoma qualifying-gain and source/basis adjustments are not modeled.",
                "Rule version " + OklahomaTaxRules.VERSION + ": 2026 statutory 0%/2.5%/3.5%/4.5% marginal bands and nominal deductions/exemptions are held in future years until reviewed law/certification establishes changes. HB2764/HB4072 conditional reductions are not forecast or activated by plan inflation; no future elimination date is assumed.",
                "Ordinary qualified private-plan and qualified Roth assumptions apply; pension names do not establish government classification. Specialized Oklahoma government/CSRS, military, railroad, disability, inherited and service/basis exceptions require separate consideration; represented incompatible facts are unsupported. Employer-to-Roth/in-plan variants are not inferred from IRA conversion rules. Commercial nonqualified annuities retain only their already-modeled taxable portion and receive no retirement-pool exclusion.",
                "Standard-deduction-only model; no itemization, dependent/blindness benefits, low-income sales/property-tax relief or ancillary credits, business/foreign adjustments, part-year/multistate treatment. Federal below-AGI basic/age/enhanced senior deductions are not state deductions. Exact intermediates/final HALF_UP cents differ from return-table/whole-dollar filing. Existing federal, annuity and NIIT safeguards remain.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.OK, request.assumptions().calculationMethod(),
                List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), explanation);
    }
}
