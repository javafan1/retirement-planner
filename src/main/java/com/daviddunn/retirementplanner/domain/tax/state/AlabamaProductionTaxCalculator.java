package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.tax.state.alabama.AlabamaFederalLiabilityDeduction;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.alabama.AlabamaTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Bounded actual-joint/Single estimate. Source evidence never creates another income channel. */
public final class AlabamaProductionTaxCalculator implements StateIncomeTaxCalculator {
    static final int FIRST_SUPPORTED_YEAR = 2026;
    static final String UNKNOWN_PENSION_CLASSIFICATION =
            "Alabama pension DB-versus-DC classification is UNKNOWN and materially changes tax. Verify the original legal plan classification.";

    private final AlabamaTaxRules rules = new AlabamaTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.AL)
            throw new IllegalArgumentException("Alabama calculator requires Alabama residency.");
        if (request.taxYear() < FIRST_SUPPORTED_YEAR || request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)
            return unsupported(request, "Alabama supports Single/actual joint returns from 2026.");
        try { return calculateSupported(request); }
        catch (IllegalArgumentException unavailable) { return unsupported(request, unavailable.getMessage()); }
    }

    private ProductionStateTaxResult calculateSupported(StateTaxCalculationRequest request) {
        var household = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (!hasSupportedFilers(request.filingStatus(), household))
            return unsupported(request, "Alabama requires complete annual filer birth dates and survivor context.");
        var income = request.income();
        var federal = request.federal();
        var ss = federal.getTaxableSocialSecurity();
        if (federal.getAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(ss)) != 0
                || ss.signum() < 0 || ss.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0)
            return unsupported(request, "Federal AGI and taxable Social Security do not reconcile.");
        var eligible = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var unknownPensions = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        BigDecimal definedBenefit = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO, conversions = BigDecimal.ZERO, pensions = BigDecimal.ZERO;
        BigDecimal social = BigDecimal.ZERO, conditional = BigDecimal.ZERO;
        var seen = new HashSet<IncomeSourceAttribution>();
        var taxableOwners = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear())
                return unsupported(request, "Alabama source attribution belongs to another year.");
            if (source.grossAmount().signum() == 0) continue;
            if (!seen.add(source)) return unsupported(request, "Duplicate annual source evidence.");
            if (source.owner().isPresent() && source.owner().get() != AccountOwnership.JOINT
                    && !household.activeOwners().contains(source.owner().get())
                    && !(source.kind() == IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL
                        && source.account().isPresent()
                        && Set.of(TaxTreatment.CASH, TaxTreatment.TAXABLE).contains(source.account().orElseThrow().type().getTaxTreatment())
                        && source.modeledTaxableAmount().orElse(BigDecimal.ZERO).signum() == 0))
                return unsupported(request, "Positive income belongs to an inactive/deceased recipient.");
            var taxable = source.modeledTaxableAmount();
            BigDecimal retirement = BigDecimal.ZERO;
            switch (source.kind()) {
                case ORDINARY_WITHDRAWAL, CAPITAL_GAIN, INTEREST, DIVIDEND -> { /* Reconciled once below. */ }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> {
                    if (taxable.isEmpty() || source.account().isEmpty())
                        return unsupported(request, "Retirement taxable amount/account attribution is required.");
                    var type = source.account().orElseThrow().type();
                    if (source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION
                            && type.getTaxTreatment() != TaxTreatment.TAX_DEFERRED)
                        return unsupported(request, "A represented conversion must originate from a traditional account, not qualified Roth cash.");
                    if (type == AccountType.INHERITED_TRADITIONAL_IRA || type == AccountType.INHERITED_ROTH_IRA)
                        return unsupported(request, "Inherited retirement qualification/history is outside the ordinary Alabama subset.");
                    if (Set.of(AccountType.TRADITIONAL_401K, AccountType.TRADITIONAL_403B).contains(type)
                            && source.account().orElseThrow().participantFacts().retirementConditionsStatusOn(household.distributionDate())
                                    == RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET)
                        return unsupported(request, "Represented unmet employer-plan qualification overrides the ordinary-plan assumption.");
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
                    if (source.owner().isEmpty() || !household.activeOwners().contains(source.owner().orElseThrow()))
                        return unsupported(request, "Pension needs its actual current recipient, including exempt DB benefits.");
                    var classification = source.pension().map(IncomeSourceAttribution.PensionFacts::planClassification)
                            .orElse(PensionPlanClassification.UNKNOWN);
                    if (classification == PensionPlanClassification.DEFINED_BENEFIT_414J) {
                        definedBenefit = definedBenefit.add(taxable.orElseThrow());
                    } else if (classification == PensionPlanClassification.UNKNOWN) {
                        var owner = source.owner().orElse(null);
                        if (owner == null || !household.activeOwners().contains(owner))
                            return unsupported(request, "Pension needs its actual current recipient.");
                        unknownPensions.merge(owner, taxable.orElseThrow(), BigDecimal::add);
                    } else retirement = taxable.orElseThrow();
                }
                case NONQUALIFIED_ANNUITY -> {
                    if (source.conditionalBenefit().isEmpty() || source.conditionalBenefit().orElseThrow().annuityBasis() == null
                            || taxable.isEmpty()) return unsupported(request, "Annuity requires its already-admitted taxable/basis receipt facts.");
                    conditional = conditional.add(taxable.orElseThrow());
                    // Approved Schedule RS interpretation, only for the already-admitted annuitized taxable portion.
                    retirement = taxable.orElseThrow();
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
                case OTHER -> { return unsupported(request, "Unclassified income cannot establish Alabama treatment."); }
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
            return unsupported(request, "Alabama retirement totals require complete attribution; other evidence must not exceed modeled income.");

        new com.daviddunn.retirementplanner.domain.tax.state.alabama.AlabamaInvestmentReconciliation()
                .reconcile(income, household.activeOwners(), request.filingStatus() == FilingStatus.SINGLE);
        var allAsDc = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        allAsDc.putAll(eligible);
        unknownPensions.forEach((owner, amount) -> allAsDc.merge(owner, amount, BigDecimal::add));
        var senior = new HashSet<AccountOwnership>();
        for (var owner : filers) if (rules.senior(request.taxYear(), household, owner)) senior.add(owner);
        var federalDeduction = new AlabamaFederalLiabilityDeduction().calculate(request);
        var outcome = worksheet(request, allAsDc, definedBenefit, senior, federalDeduction.eligibleLiability());
        var possibleAges = filers.stream().filter(owner -> rules.ambiguousSenior(request.taxYear(), household, owner)).toList();
        for (int mask = 0; mask < (1 << possibleAges.size()); mask++) {
            var ages = new HashSet<>(senior);
            for (int i = 0; i < possibleAges.size(); i++) if ((mask & (1 << i)) != 0) ages.add(possibleAges.get(i));
            if (worksheet(request, allAsDc, definedBenefit, ages, federalDeduction.eligibleLiability()).tax().compareTo(outcome.tax()) != 0)
                return unsupported(request, "Alabama January 1/year-end or January 1 death senior-birthday convention materially changes tax and is unverified.");
            var unknownAsDb = unknownPensions.values().stream().reduce(definedBenefit, BigDecimal::add);
            if (worksheet(request, eligible, unknownAsDb, ages, federalDeduction.eligibleLiability()).tax().compareTo(outcome.tax()) != 0)
                return unsupported(request, UNKNOWN_PENSION_CLASSIFICATION);
        }
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Alabama base: modeled AGI " + federal.getAdjustedGrossIncome().toPlainString()
                + "; taxable Social Security subtraction " + ss.toPlainString() + "; verified DB pension subtraction " + definedBenefit
                + "; individual taxable retirement receipts " + allAsDc + "; capped senior subtraction " + outcome.exclusion()
                + "; Alabama AGI " + outcome.agi() + "; completed-step standard deduction " + outcome.deduction()
                + "; personal exemption " + rules.personalExemption(request.filingStatus()) + "; taxable income " + outcome.base()
                + "; modeled federal cash tax " + federalDeduction.modeledCashTax()
                + "; eligible federal liability deduction " + federalDeduction.eligibleLiability()
                + "; federal cash less eligible liability " + federalDeduction.modeledCashTax().subtract(federalDeduction.eligibleLiability())
                + "; federal additional-age/enhanced-senior deductions " + federalDeduction.additionalAgeDeduction()
                + "/" + federalDeduction.enhancedSeniorDeduction() + "; NIIT included once " + federalDeduction.niit()
                + "; unknown pension receipts (tax-equivalent only) " + unknownPensions
                + "; pension classifications " + income.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION)
                        .map(s -> s.owner() + ":" + s.pension().map(IncomeSourceAttribution.PensionFacts::planClassification).orElse(PensionPlanClassification.UNKNOWN)).toList()
                + "; statutory marginal bands; rule version " + AlabamaTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.AL, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, outcome.tax(), notes, Optional.empty());
    }

    private record Outcome(BigDecimal agi, BigDecimal exclusion, BigDecimal deduction, BigDecimal base, BigDecimal tax) { }

    static boolean hasSupportedFilers(FilingStatus status, TaxHouseholdContext household) {
        int expected = switch (status) {
            case SINGLE -> 1;
            case MARRIED_FILING_JOINTLY -> 2;
            default -> -1;
        };
        var filers = status == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        return filers.size() == expected && household.birthDates().keySet().containsAll(filers);
    }

    private Outcome worksheet(StateTaxCalculationRequest request, Map<AccountOwnership, BigDecimal> receipts,
            BigDecimal db, Set<AccountOwnership> seniors, BigDecimal federalDeduction) {
        var exclusion = receipts.entrySet().stream().filter(e -> seniors.contains(e.getKey()))
                .map(e -> e.getValue().min(AlabamaTaxRules.RETIREMENT_LIMIT)).reduce(BigDecimal.ZERO, BigDecimal::add);
        var agi = request.federal().getAdjustedGrossIncome().subtract(request.federal().getTaxableSocialSecurity())
                .subtract(db).subtract(exclusion);
        if (agi.signum() < 0) throw new IllegalArgumentException("Alabama exempt amounts exceed reconciled income.");
        var deduction = rules.standardDeduction(agi, request.filingStatus());
        var base = agi.subtract(deduction).subtract(rules.personalExemption(request.filingStatus()))
                .subtract(federalDeduction).max(BigDecimal.ZERO);
        return new Outcome(agi, exclusion, deduction, base, rules.tax(base, request.filingStatus()));
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Alabama disclosure year range.");
        return List.of(
                "Alabama full-year domestic Single/MFJ ordinary-retirement estimate. Reconciles modeled federal AGI to Alabama income, removes included Social Security and verified IRC 414(j) defined-benefit pensions, and applies Alabama's completed-$500-step standard deduction ($3,000 to $2,500 Single; $8,500 to $5,000 MFJ) and personal exemption $1,500/$3,000. Fixed enacted ordinary amounts remain nominal; statutory 2%/4%/5% marginal slices apply.",
                "Only explicit original pension legal classification establishes defined-benefit relief, including direct survivor benefits. UNKNOWN materially ambiguous pensions are unavailable; names, monthly payments, COLA and cash-balance presentation are not proof. Defined-contribution pensions receive no automatic full exemption. Government, military, railroad, nonqualified-plan, inherited and historical-service exceptions are not inferred.",
                "One $6,000 age-65 taxable retirement pool per current recipient covers admitted ordinary IRA/employer withdrawals, RMDs, completed own-IRA-to-Roth IRA conversions and independently admitted annuitized taxable portions. Fully exempt DB and qualified Roth amounts consume no pool; unused caps never transfer. Material unresolved January 1/death birthday boundaries are unavailable; a deceased filer is not aged into later eligibility.",
                "The Alabama federal-liability deduction is a separate ordinary current-year accrued-liability estimate, not withholding, payments or a refund. Uses existing federal brackets/basic deduction plus applicable federal additional-age and temporary enhanced-senior deductions/phaseout; the temporary deduction ends after 2028. Evidenced NIIT is included once. Modeled federal cash taxes remain unchanged and may differ from the eligible deduction; annual metadata records both. No unmodeled federal credits, AMT, preference, foreign MAGI, prior-year refund/audit or payment-history adjustments are invented.",
                "2026/2027 published federal additional-age amounts are used for the eligible liability; the latest reviewed additional-age amounts are held nominally afterward. Federal basic deductions/brackets retain existing projected-rule assumptions. This is not a complete filed federal return; missing federal credits or preferential investment tax treatment may overstate the federal deduction and understate Alabama tax. Rule version AL-2026-1; future law changes require review.",
                "Brokerage proceeds are cash flow, not automatically taxable income; unknown basis alone is admitted. Modeled investment income is reconciled once without invented gains. Unmodeled realized gains can increase actual tax; special Treasury/municipal, bullion, loss, source and Alabama-basis adjustments may lower it. Historical pre-1982/pre-1985 contributions or exempt DB rollovers can create Alabama basis; represented incompatible facts are outside this fully taxable modern-account subset.",
                "Commercial annuities retain only their already-admitted taxable portion; recovered investment is not taxed again and monthly payments do not prove DB status. Arbitrary conditional income is not automatically retirement income. Existing annuity, basis, Advanced-mode, NIIT and growth safeguards remain. Standard-deduction-only/nondependent scope omits itemization and specialized credits, dependent/blindness, business/foreign and multistate/part-year rules. Exact intermediates/final HALF_UP cents differ from return tables and whole-dollar filing.");
    }

    /** Immutable result-time presentation text; no qualification is inferred by this summary. */
    public static String pensionAssumptions(Household household) {
        return "Alabama original pension legal classifications: " + household.peopleByOwner().entrySet().stream()
                .flatMap(entry -> entry.getValue().getIncomeSources().stream()
                        .filter(com.daviddunn.retirementplanner.domain.income.Pension.class::isInstance)
                        .map(com.daviddunn.retirementplanner.domain.income.Pension.class::cast)
                        .map(pension -> entry.getKey() + " / " + pension.getName() + ": " + pension.getPlanClassification()))
                .toList() + ". Only verified qualifying DB receipts receive full relief; current recipients are captured annually.";
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.AL, request.assumptions().calculationMethod(),
                List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), explanation);
    }
}
