package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Isolated ordinary-source qualification/reconciliation, not a production admission.
 * Current DOR IRA/pension guidance is applied conservatively. Conversion interpretation,
 * employer periodicity, inherited exceptions and private annuity maturity remain gated.
 * Metadata reconciles existing modeled income; it never adds cash proceeds to AGI.
 */
public final class ColoradoIncomeReconciliation {
    public record Receipts(Map<AccountOwnership, BigDecimal> grossSocialSecurity,
            Map<AccountOwnership, BigDecimal> eligibleOtherRetirement) {
        public Receipts {
            grossSocialSecurity = Map.copyOf(grossSocialSecurity);
            eligibleOtherRetirement = Map.copyOf(eligibleOtherRetirement);
        }
    }

    public Receipts reconcile(int year, FilingStatus status, TaxHouseholdContext household,
            TaxIncome income, FederalTaxCalculation federal) {
        ColoradoTaxRules.requireVerifiedYear(year);
        var filers = ColoradoFederalTaxBaseBridge.filers(year, status, household);
        Objects.requireNonNull(income);
        Objects.requireNonNull(federal);
        require(federal.getAdjustedGrossIncome().compareTo(income.getOrdinaryIncomeBeforeSocialSecurity()
                .add(federal.getTaxableSocialSecurity())) == 0, "Federal AGI does not reconcile with modeled income.");
        var social = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        var other = new EnumMap<AccountOwnership, BigDecimal>(AccountOwnership.class);
        BigDecimal withdrawals = BigDecimal.ZERO;
        BigDecimal pensions = BigDecimal.ZERO;
        BigDecimal conditional = BigDecimal.ZERO;
        BigDecimal investments = BigDecimal.ZERO;
        for (var source : income.getIncomeSources()) {
            require(source.taxYear().isEmpty() || source.taxYear().getAsInt() == year,
                    "Colorado source belongs to another tax year.");
            if (source.grossAmount().signum() == 0) continue;
            require(source.taxYear().isPresent(), "Positive Colorado source evidence needs its tax year.");
            BigDecimal eligible = BigDecimal.ZERO;
            switch (source.kind()) {
                case SOCIAL_SECURITY -> {
                    var owner = activeOwner(source, household);
                    social.merge(owner, source.grossAmount(), BigDecimal::add);
                }
                case ROTH_CONVERSION -> throw new IllegalArgumentException(
                        "Colorado IRA conversion interpretation requires current official rule confirmation; no eligibility is inferred.");
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD -> {
                    var owner = activeOwner(source, household);
                    require(source.account().isPresent() && source.modeledTaxableAmount().isPresent(),
                            "Retirement receipts need account and taxable-amount facts.");
                    var type = source.account().orElseThrow().type();
                    BigDecimal taxable = source.modeledTaxableAmount().orElseThrow();
                    require(type != AccountType.INHERITED_TRADITIONAL_IRA && type != AccountType.INHERITED_ROTH_IRA,
                            "Inherited qualification/history is outside the ordinary Colorado subset.");
                    if (type.getTaxTreatment() == TaxTreatment.ROTH && taxable.signum() == 0) continue;
                    require((type == AccountType.TRADITIONAL_IRA || type == AccountType.ROLLOVER_IRA)
                                    && taxable.compareTo(source.grossAmount()) == 0,
                            "Only fully taxable ordinary IRA receipts are qualified here; employer periodicity and partial basis need review.");
                    withdrawals = withdrawals.add(taxable);
                    int age = ColoradoFederalTaxBaseBridge.ageAtTaxClose(year, household, owner);
                    if (age >= 55) {
                        LocalDate minimumReceiptAge = household.birthDates().get(owner).plusYears(59).plusMonths(6);
                        // Annual receipts can predate the projection start (e.g. opening income).
                        // No exact distribution date / early-penalty exception is recorded per receipt.
                        require(!minimumReceiptAge.isAfter(LocalDate.of(year, 1, 1)),
                                "Early or midyear age-59.5 IRA receipts need actual timing/penalty-exception facts.");
                        eligible = taxable;
                    }
                }
                case PENSION -> {
                    var owner = activeOwner(source, household);
                    require(source.pension().isPresent() && source.modeledTaxableAmount().isPresent()
                                    && source.modeledTaxableAmount().orElseThrow().compareTo(source.grossAmount()) == 0,
                            "Colorado pension needs ordinary fully taxable periodic-plan evidence.");
                    var facts = source.pension().orElseThrow();
                    require(facts.participantFacts().separationStatusOn(household.distributionDate())
                                    != RetirementParticipantFacts.SeparationStatus.NOT_SEPARATED
                                    && facts.participantFacts().retirementConditionsStatusOn(household.distributionDate())
                                    != RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET,
                            "Represented pension nonretirement facts contradict ordinary qualifying-plan treatment.");
                    require(facts.originalOwner() != AccountOwnership.JOINT && facts.startDate().isPresent()
                                    && !facts.startDate().orElseThrow().isAfter(LocalDate.of(year, 12, 31))
                                    && (facts.endDate().isEmpty() || facts.endDate().orElseThrow()
                                            .isAfter(facts.startDate().orElseThrow().plusYears(1))),
                            "Colorado ordinary pension requires a periodic term longer than one year.");
                    int age = ColoradoFederalTaxBaseBridge.ageAtTaxClose(year, household, owner);
                    require(age >= 55 || facts.originalOwner() == owner,
                            "Under-55 survivor/death-benefit qualification is not established by recipient transfer alone.");
                    pensions = pensions.add(source.grossAmount());
                    if (age >= 55) eligible = source.grossAmount();
                }
                case NONQUALIFIED_ANNUITY -> throw new IllegalArgumentException(
                        "Colorado fully matured privately purchased annuity qualification is not established by current terms.");
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM -> {
                    activeOwner(source, household);
                    require(source.modeledTaxableAmount().isPresent(), "Conditional receipt needs its modeled taxable amount.");
                    conditional = conditional.add(source.modeledTaxableAmount().orElseThrow());
                    // A retirement/death trigger is not qualifying pension evidence.
                }
                case ORDINARY_WITHDRAWAL, CAPITAL_GAIN, INTEREST, DIVIDEND -> {
                    if (source.account().isPresent()) {
                        var treatment = source.account().orElseThrow().type().getTaxTreatment();
                        require(treatment != TaxTreatment.TAX_DEFERRED
                                        && (source.modeledTaxableAmount().orElse(BigDecimal.ZERO).signum() == 0
                                            || treatment == TaxTreatment.TAXABLE),
                                "Retirement/cash evidence conflicts with taxable-investment classification.");
                    }
                    if (source.kind() == IncomeSourceAttribution.Kind.INTEREST
                            || source.kind() == IncomeSourceAttribution.Kind.DIVIDEND) {
                        require(source.modeledTaxableAmount().isPresent()
                                        && source.modeledTaxableAmount().orElseThrow().compareTo(source.grossAmount()) == 0,
                                "Special/exempt investment sourcing needs Colorado adjustment facts.");
                    }
                    investments = investments.add(source.modeledTaxableAmount().orElse(BigDecimal.ZERO));
                }
                case OTHER -> throw new IllegalArgumentException("Unclassified Colorado income is unsupported.");
                case NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
            }
            if (eligible.signum() > 0) other.merge(activeOwner(source, household), eligible, BigDecimal::add);
        }
        require(income.getRothConversion().signum() == 0,
                "Colorado conversion income requires current official rule confirmation and complete execution evidence.");
        require(withdrawals.compareTo(income.getTaxDeferredWithdrawals()) == 0
                        && pensions.compareTo(income.getPensionIncome()) == 0
                        && conditional.compareTo(income.getConditionalOrdinaryIncome()) == 0
                        && total(social).compareTo(income.getSocialSecurityIncome()) == 0
                        && investments.compareTo(income.getTaxableInterestIncome()) <= 0,
                "Colorado recipient totals must reconcile; explicit investment evidence cannot exceed modeled income.");
        require(filers.containsAll(other.keySet()) && filers.containsAll(social.keySet()), "Income belongs to a nonfiler.");
        return new Receipts(social, other);
    }

    private static AccountOwnership activeOwner(IncomeSourceAttribution source, TaxHouseholdContext household) {
        var owner = source.owner().orElse(null);
        require(owner != null && household.activeOwners().contains(owner),
                "Colorado retirement/SS receipts require the actual active recipient; no household split is inferred.");
        return owner;
    }

    private static BigDecimal total(Map<AccountOwnership, BigDecimal> amounts) {
        return amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
}
