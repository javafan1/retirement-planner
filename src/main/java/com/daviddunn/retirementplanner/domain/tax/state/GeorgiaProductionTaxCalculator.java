package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.georgia.GeorgiaTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Bounded actual-joint/Single estimate. Source evidence never creates another income channel. */
public final class GeorgiaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final GeorgiaTaxRules rules = new GeorgiaTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.GA)
            throw new IllegalArgumentException("Georgia calculator requires Georgia residency.");
        if (request.taxYear() < 2026 || request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)
            return unsupported(request, "Georgia supports Single/actual joint returns from 2026.");
        var household = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (filers.size() != (request.filingStatus() == FilingStatus.SINGLE ? 1 : 2)
                || !household.birthDates().keySet().containsAll(filers))
            return unsupported(request, "Georgia requires complete annual filer birth dates and survivor context.");
        var income = request.income();
        var federal = request.federal();
        var ss = federal.getTaxableSocialSecurity();
        if (federal.getAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(ss)) != 0
                || ss.signum() < 0 || ss.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0)
            return unsupported(request, "Federal AGI and taxable Social Security do not reconcile.");
        var eligible = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        BigDecimal withdrawals = BigDecimal.ZERO, conversions = BigDecimal.ZERO, pensions = BigDecimal.ZERO;
        BigDecimal social = BigDecimal.ZERO, conditional = BigDecimal.ZERO;
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear())
                return unsupported(request, "Georgia source attribution belongs to another year.");
            if (source.grossAmount().signum() == 0) continue;
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
                        return unsupported(request, "Inherited retirement qualification/history is outside the ordinary Georgia subset.");
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
                    pensions = pensions.add(taxable.orElseThrow());
                    retirement = taxable.orElseThrow();
                }
                case NONQUALIFIED_ANNUITY -> {
                    if (source.conditionalBenefit().isEmpty() || source.conditionalBenefit().orElseThrow().annuityBasis() == null
                            || taxable.isEmpty()) return unsupported(request, "Annuity requires its already-admitted taxable/basis receipt facts.");
                    retirement = taxable.orElseThrow();
                    conditional = conditional.add(retirement);
                }
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM -> {
                    if (source.conditionalBenefit().isEmpty() || taxable.isEmpty())
                        return unsupported(request, "Conditional taxable receipts require reliable receipt facts.");
                    conditional = conditional.add(taxable.orElseThrow());
                }
                case SOCIAL_SECURITY -> social = social.add(source.grossAmount());
                case OTHER -> { return unsupported(request, "Unclassified income cannot establish Georgia treatment."); }
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
            return unsupported(request, "Georgia retirement totals require complete attribution; other evidence must not exceed modeled income.");
        BigDecimal exclusion = BigDecimal.ZERO;
        try {
            var investmentOwners = new com.daviddunn.retirementplanner.domain.tax.state.georgia.GeorgiaInvestmentReconciliation()
                    .eligible(income, household.activeOwners(), request.filingStatus()==FilingStatus.SINGLE);
            investmentOwners.forEach((owner, amount) -> eligible.merge(owner, amount, BigDecimal::add));
            for (var filer : filers) {
                if (eligible.getOrDefault(filer, BigDecimal.ZERO).signum() == 0) continue;
                var limit = rules.retirementLimit(request.taxYear(), household.birthDates().get(filer), household.activeOwners().contains(filer));
                exclusion = exclusion.add(eligible.getOrDefault(filer, BigDecimal.ZERO).min(limit));
            }
        } catch (IllegalArgumentException unavailable) { return unsupported(request, unavailable.getMessage()); }
        var deduction = rules.standardDeduction(request.taxYear(), request.filingStatus());
        var base = federal.getAdjustedGrossIncome().subtract(ss).subtract(exclusion).subtract(deduction).max(BigDecimal.ZERO);
        var tax = base.multiply(rules.rate(request.taxYear())).setScale(2, RoundingMode.HALF_UP);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Georgia base: modeled AGI " + federal.getAdjustedGrossIncome().toPlainString()
                + "; taxable Social Security subtraction " + ss.toPlainString() + "; individual eligible retirement receipts " + eligible
                + "; capped retirement subtraction " + exclusion.toPlainString() + "; one state deduction " + deduction.toPlainString()
                + "; taxable income " + base.toPlainString()
                + "; rate " + rules.rate(request.taxYear()) + "; rule version " + GeorgiaTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.GA, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Georgia disclosure year range.");
        return List.of(
                "Georgia full-year domestic Single/MFJ retirement-planning estimate. Uses modeled federal AGI less taxable Social Security and one state standard deduction. Each eligible recipient shares one retirement/investment exclusion: $35,000 ages 62-64, $65,000 age 65+ in 2026/$70,000 from 2027. Unused caps do not transfer; IRA distributions, RMDs, confirmed IRA-to-Roth conversions, ordinary private pensions and admitted taxable annuity receipts share the recipient's pool.",
                "Brokerage proceeds are cash flow, not automatically taxable income. Uses only already-modeled taxable investment income once. Unknown basis alone is admitted. Unmodeled realized gains may cause actual taxes to be higher. Capital losses, security lots and special Treasury/municipal or state-basis adjustments are not modeled.",
                "Georgia investment ownership: known recipients control; only explicitly JOINT property income is split 50/50. Unallocated MFJ investment income remains taxable without a retirement exclusion and may overstate actual Georgia tax. On an uncontradicted Single return the ordinary aggregate is assigned to the sole resident filer. Missing retirement ownership and contradictory evidence are unsupported.",
                "Rule version " + GeorgiaTaxRules.VERSION + ": reviewed 4.99% rate and $15,000 Single/$30,000 MFJ deduction from 2026 held nominally. Conditional future rate cuts and deduction increases are not forecast or inflation-indexed; only legally established reviewed updates apply. The age-65 cap increase to $70,000 in 2027 is unconditional. An unresolved January 1 threshold birthday returns unavailable rather than guessed eligibility.",
                "Assumes fully taxable ordinary private retirement receipts and qualified Roth withdrawals. Military, disability, railroad, inherited/basis histories, rental/royalty/business qualification and historical government-service provisions require separate consideration. Pension names do not establish classification. Employer-to-Roth/in-plan conversion variants are unsupported pending independent source verification; ordinary employer-plan withdrawals remain admitted.",
                "Standard-deduction-only estimate for nondependent filers; no itemization, earned-income exclusion engine, dependent/blindness benefits, ancillary credits, part-year residence or multistate sourcing. Federal below-AGI basic/age/enhanced senior deductions are not Georgia deductions. Existing federal/annuity/NIIT limitations remain. Exact intermediates/final HALF_UP cents differ from whole-dollar tax returns.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.GA, request.assumptions().calculationMethod(),
                List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), explanation);
    }
}
