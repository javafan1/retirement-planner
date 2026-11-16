package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.kentucky.KentuckyTaxRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Bounded actual-joint/Single estimate. Source evidence never creates another income channel. */
public final class KentuckyProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final KentuckyTaxRules rules = new KentuckyTaxRules();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.KY)
            throw new IllegalArgumentException("Kentucky calculator requires Kentucky residency.");
        if (request.taxYear() < 2026 || request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)
            return unsupported(request, "Kentucky supports Single/actual joint returns from 2026.");
        var household = request.household();
        var filers = request.filingStatus() == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (filers.size() != (request.filingStatus() == FilingStatus.SINGLE ? 1 : 2)
                || !household.birthDates().keySet().containsAll(filers))
            return unsupported(request, "Kentucky requires complete annual filer birth dates and survivor context.");
        var income = request.income();
        var federal = request.federal();
        var ss = federal.getTaxableSocialSecurity();
        if (federal.getAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity().add(ss)) != 0
                || ss.signum() < 0 || ss.compareTo(income.getSocialSecurityIncome().multiply(new BigDecimal("0.85"))) > 0)
            return unsupported(request, "Federal AGI and taxable Social Security do not reconcile.");
        var eligible = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        BigDecimal withdrawals = BigDecimal.ZERO, conversions = BigDecimal.ZERO, pensions = BigDecimal.ZERO;
        BigDecimal investments = BigDecimal.ZERO, social = BigDecimal.ZERO, conditional = BigDecimal.ZERO;
        for (var source : income.getIncomeSources()) {
            if (source.taxYear().isPresent() && source.taxYear().getAsInt() != request.taxYear())
                return unsupported(request, "Kentucky source attribution belongs to another year.");
            if (source.grossAmount().signum() == 0) continue;
            var taxable = source.modeledTaxableAmount();
            boolean investmentEvidence = switch (source.kind()) {
                case ORDINARY_WITHDRAWAL, CAPITAL_GAIN, INTEREST, DIVIDEND -> true;
                default -> false;
            };
            if (investmentEvidence && source.account().isPresent()) {
                var treatment = source.account().orElseThrow().type().getTaxTreatment();
                if (treatment == TaxTreatment.TAX_DEFERRED
                        || treatment == TaxTreatment.ROTH && taxable.orElse(BigDecimal.ZERO).signum() > 0)
                    return unsupported(request, "Represented retirement-account receipts conflict with taxable-investment classification.");
            }
            BigDecimal retirement = BigDecimal.ZERO;
            switch (source.kind()) {
                case ORDINARY_WITHDRAWAL, CAPITAL_GAIN -> investments = investments.add(taxable.orElse(BigDecimal.ZERO));
                case INTEREST, DIVIDEND -> {
                    if (taxable.isEmpty() || taxable.orElseThrow().compareTo(source.grossAmount()) != 0)
                        return unsupported(request, "Special/exempt investment sources need Kentucky adjustment facts.");
                    investments = investments.add(taxable.orElseThrow());
                }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> {
                    if (taxable.isEmpty() || source.account().isEmpty())
                        return unsupported(request, "Retirement taxable amount/account attribution is required.");
                    var type = source.account().orElseThrow().type();
                    if (type == AccountType.INHERITED_TRADITIONAL_IRA || type == AccountType.INHERITED_ROTH_IRA)
                        return unsupported(request, "Inherited retirement qualification/history is outside the ordinary Kentucky subset.");
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
                case OTHER -> { return unsupported(request, "Unclassified income cannot establish Kentucky treatment."); }
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
                || social.compareTo(income.getSocialSecurityIncome()) > 0 || investments.compareTo(income.getTaxableInterestIncome()) > 0)
            return unsupported(request, "Kentucky retirement totals require complete attribution; other evidence must not exceed modeled income.");
        BigDecimal exclusion = eligible.values().stream().map(value -> value.min(KentuckyTaxRules.RETIREMENT_LIMIT))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = BigDecimal.ZERO;
        for (var filer : filers) {
            LocalDate ageDate = household.activeOwners().contains(filer) ? LocalDate.of(request.taxYear(), 12, 31)
                    : LocalDate.of(request.taxYear(), 1, 1);
            if (!household.birthDates().get(filer).plusYears(65).isAfter(ageDate)) credit = credit.add(KentuckyTaxRules.SENIOR_CREDIT);
        }
        var deduction = rules.standardDeduction(request.taxYear());
        var base = federal.getAdjustedGrossIncome().subtract(ss).subtract(exclusion).subtract(deduction).max(BigDecimal.ZERO);
        var tax = base.multiply(rules.rate(request.taxYear())).subtract(credit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        var notes = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        notes.add("Annual Kentucky base: modeled AGI " + federal.getAdjustedGrossIncome().toPlainString()
                + "; taxable Social Security subtraction " + ss.toPlainString() + "; individual eligible retirement receipts " + eligible
                + "; capped retirement subtraction " + exclusion.toPlainString() + "; one state deduction " + deduction.toPlainString()
                + "; taxable income " + base.toPlainString() + "; nominal nonrefundable age credit " + credit.toPlainString()
                + "; rate " + rules.rate(request.taxYear()) + "; rule version " + KentuckyTaxRules.VERSION + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.KY, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, tax, notes, Optional.empty());
    }

    public static List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Kentucky disclosure year range.");
        return List.of(
                "Kentucky full-year domestic Single/actual joint retirement-planning estimate. Modeled MFJ uses one joint standard deduction, not separate-on-combined filing optimization. Subtracts federally taxable Social Security. Ordinary fully taxable private pensions, traditional withdrawals/RMDs, confirmed IRA-to-Roth IRA conversions and admitted taxable annuity receipts share each recipient's $31,110 exclusion; unused exclusions do not transfer. Includes the $40 nonrefundable age-65 credit per qualifying filer.",
                "Brokerage withdrawal proceeds are cash flow, not automatically taxable income. Uses taxable investment income already modeled. Unmodeled realized gains may cause actual taxes to be higher; security lots, capital losses/carryovers and special Treasury/municipal or state-basis adjustments are not modeled.",
                "Rule version " + KentuckyTaxRules.VERSION + ": 3.5% from 2026; conditional reductions are not forecast. Published standard deduction $3,360 in 2026/$3,470 in 2027. Uses a disclosed nominal $3,470 carry-forward from 2028 until reviewed updates; statutory future CPI adjustments are not predicted. Retirement limit and age credit are nominal.",
                "Assumes ordinary written private retirement plans; pension names do not establish classification. Special government/pre-1998 service, military/railroad, inherited histories, partial traditional basis and taxable nonqualified Roth treatment require separate consideration. Employer-plan conversions are unsupported pending source-specific review; ordinary employer-plan withdrawals remain admitted.",
                "Standard-deduction-only estimate for nondependent filers. Omits the family-size credit, blindness/other credits, itemization, business/foreign adjustments, part-year residence and multistate sourcing. Omitted credits or separate-on-combined filing may lower actual tax. Federal below-AGI deductions are not imported as Kentucky deductions. Existing federal/annuity/NIIT limitations remain; exact intermediates and final HALF_UP cents differ from whole-dollar return preparation.");
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.KY, request.assumptions().calculationMethod(),
                List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), explanation);
    }
}
