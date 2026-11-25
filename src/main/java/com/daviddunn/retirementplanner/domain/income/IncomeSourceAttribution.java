package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedRothIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Runtime financial facts, never an assessment of state-tax eligibility.
 * A missing taxable amount means unknown, not zero. Known taxable amounts reflect
 * the existing federal projection model, not a new basis or qualification rule.
 */
public record IncomeSourceAttribution(
        Kind kind,
        Purpose purpose,
        Optional<AccountOwnership> owner,
        Optional<AccountReference> account,
        Optional<AccountReference> destination,
        String description,
        BigDecimal grossAmount,
        Optional<BigDecimal> modeledTaxableAmount,
        OptionalInt taxYear,
        Optional<PensionFacts> pension,
        @com.fasterxml.jackson.annotation.JsonIgnore Optional<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt> conditionalBenefit,
        @com.fasterxml.jackson.annotation.JsonIgnore Optional<com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation> niit,
        @com.fasterxml.jackson.annotation.JsonIgnore Optional<com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode> calculationMode,
        @com.fasterxml.jackson.annotation.JsonIgnore Optional<SuccessorRmd> successorRmd) {

    public IncomeSourceAttribution(Kind kind, Purpose purpose, Optional<AccountOwnership> owner,
            Optional<AccountReference> account, Optional<AccountReference> destination, String description,
            BigDecimal grossAmount, Optional<BigDecimal> modeledTaxableAmount, OptionalInt taxYear,
            Optional<PensionFacts> pension, Optional<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt> conditionalBenefit,
            Optional<com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation> niit,
            Optional<com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode> calculationMode) {
        this(kind, purpose, owner, account, destination, description, grossAmount, modeledTaxableAmount,
                taxYear, pension, conditionalBenefit, niit, calculationMode, Optional.empty());
    }

    public IncomeSourceAttribution(Kind kind, Purpose purpose, Optional<AccountOwnership> owner,
            Optional<AccountReference> account, Optional<AccountReference> destination, String description,
            BigDecimal grossAmount, Optional<BigDecimal> modeledTaxableAmount, OptionalInt taxYear,
            Optional<PensionFacts> pension, Optional<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt> conditionalBenefit,
            Optional<com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation> niit) {
        this(kind, purpose, owner, account, destination, description, grossAmount, modeledTaxableAmount,
                taxYear, pension, conditionalBenefit, niit, Optional.empty());
    }

    public IncomeSourceAttribution(Kind kind, Purpose purpose, Optional<AccountOwnership> owner,
            Optional<AccountReference> account, Optional<AccountReference> destination, String description,
            BigDecimal grossAmount, Optional<BigDecimal> modeledTaxableAmount, OptionalInt taxYear,
            Optional<PensionFacts> pension, Optional<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt> conditionalBenefit) {
        this(kind, purpose, owner, account, destination, description, grossAmount, modeledTaxableAmount, taxYear,
                pension, conditionalBenefit, Optional.empty());
    }

    public IncomeSourceAttribution(Kind kind, Purpose purpose, Optional<AccountOwnership> owner,
            Optional<AccountReference> account, Optional<AccountReference> destination, String description,
            BigDecimal grossAmount, Optional<BigDecimal> modeledTaxableAmount, OptionalInt taxYear, Optional<PensionFacts> pension) {
        this(kind, purpose, owner, account, destination, description, grossAmount, modeledTaxableAmount, taxYear, pension, Optional.empty());
    }

    public enum Kind {
        ORDINARY_WITHDRAWAL,
        ORDINARY_RETIREMENT_WITHDRAWAL,
        RMD,
        OPENING_RMD,
        ROTH_CONVERSION,
        PENSION,
        SOCIAL_SECURITY,
        INTEREST,
        DIVIDEND,
        CAPITAL_GAIN,
        OTHER,
        CONDITIONAL_RECURRING,
        CONDITIONAL_LUMP_SUM,
        NONQUALIFIED_ANNUITY,
        NIIT_EVIDENCE,
        FINANCIAL_CALCULATION_POLICY
    }

    public enum Purpose {
        CASH_FLOW,
        TAX_FUNDING,
        INCOME,
        CONVERSION
    }

    public IncomeSourceAttribution {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(account);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(description);
        requireNonNegative(grossAmount);
        Objects.requireNonNull(modeledTaxableAmount);
        modeledTaxableAmount.ifPresent(amount -> {
            requireNonNegative(amount);
            if (amount.compareTo(grossAmount) > 0) {
                throw new IllegalArgumentException("Taxable amount exceeds gross amount.");
            }
        });
        Objects.requireNonNull(taxYear);
        Objects.requireNonNull(pension);
        Objects.requireNonNull(conditionalBenefit);
        Objects.requireNonNull(niit);
        Objects.requireNonNull(calculationMode);
        Objects.requireNonNull(successorRmd);
        if ((kind == Kind.FINANCIAL_CALCULATION_POLICY) != calculationMode.isPresent()
                || calculationMode.isPresent() && (grossAmount.signum() != 0 || modeledTaxableAmount.isEmpty()
                    || modeledTaxableAmount.get().signum() != 0 || owner.isPresent() || account.isPresent()
                    || destination.isPresent() || pension.isPresent() || conditionalBenefit.isPresent()
                    || niit.isPresent() || taxYear.isEmpty() || purpose != Purpose.INCOME))
            throw new IllegalArgumentException("Invalid annual calculation policy evidence.");
        calculationMode.ifPresent(mode -> {
            if (!description.equals("Financial Calculation Mode: " + mode.label()))
                throw new IllegalArgumentException("Calculation policy label does not match its mode.");
        });
        if ((kind == Kind.NIIT_EVIDENCE) != niit.isPresent()
                || niit.isPresent() && (grossAmount.signum() != 0 || modeledTaxableAmount.isEmpty()
                    || modeledTaxableAmount.get().signum() != 0 || account.isPresent() || owner.isPresent()
                    || destination.isPresent() || pension.isPresent() || conditionalBenefit.isPresent() || taxYear.isEmpty() || purpose != Purpose.INCOME))
            throw new IllegalArgumentException("Invalid NIIT annual evidence source.");
        boolean conditionalKind = kind == Kind.CONDITIONAL_RECURRING || kind == Kind.CONDITIONAL_LUMP_SUM || kind == Kind.NONQUALIFIED_ANNUITY;
        if (conditionalKind != conditionalBenefit.isPresent())
            throw new IllegalArgumentException("Conditional income requires its exact receipt facts.");
        conditionalBenefit.ifPresent(receipt -> {
            boolean recurring = receipt.pattern() == com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PaymentPattern.MONTHLY_RECURRING;
            if (recurring != (kind == Kind.CONDITIONAL_RECURRING || kind == Kind.NONQUALIFIED_ANNUITY)
                    || (kind == Kind.NONQUALIFIED_ANNUITY) != (receipt.annuityBasis() != null) || purpose != Purpose.INCOME || account.isPresent()
                    || destination.isPresent() || pension.isPresent() || !description.equals(receipt.label())
                    || owner.orElse(null) != com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator.owner(receipt.recipient())
                    || !grossAmount.equals(receipt.gross()) || !modeledTaxableAmount.equals(Optional.of(receipt.taxable()))
                    || taxYear.isEmpty() || taxYear.getAsInt() != receipt.year())
                throw new IllegalArgumentException("Inconsistent conditional income attribution.");
        });
        successorRmd.ifPresent(authorization -> {
            if (kind != Kind.RMD || purpose != Purpose.CASH_FLOW || destination.isPresent() || pension.isPresent()
                    || conditionalBenefit.isPresent() || niit.isPresent() || calculationMode.isPresent()
                    || !account.equals(Optional.of(authorization.account)) || !owner.equals(Optional.of(authorization.recipient))
                    || !taxYear.equals(OptionalInt.of(authorization.year)) || !grossAmount.equals(authorization.debit)
                    || !modeledTaxableAmount.equals(Optional.of(authorization.debit))
                    || !account.orElseThrow().participantFacts().equals(authorization.account.participantFacts())) {
                throw new IllegalArgumentException("Successor RMD evidence must match the authorized debit, account, recipient and year.");
            }
        });
        account.ifPresent(reference -> {
            if (owner.orElse(null) != reference.owner() && successorRmd.isEmpty()) {
                throw new IllegalArgumentException("Source account owner must match income owner.");
            }
        });
        destination.ifPresent(reference -> {
            if (kind != Kind.ROTH_CONVERSION || owner.orElse(null) != reference.owner()) {
                throw new IllegalArgumentException("Conversion destination must have the same owner.");
            }
        });
        taxYear.ifPresent(java.time.Year::of);
    }

    public boolean isRetirementDistribution() {
        return kind == Kind.ORDINARY_RETIREMENT_WITHDRAWAL
                || kind == Kind.RMD
                || kind == Kind.OPENING_RMD
                || kind == Kind.ROTH_CONVERSION;
    }

    public IncomeSourceAttribution inYear(int year) {
        return new IncomeSourceAttribution(kind, purpose, owner, account, destination,
                description, grossAmount, modeledTaxableAmount, OptionalInt.of(year), pension, conditionalBenefit, niit,
                calculationMode, successorRmd);
    }

    /** Reattributes an existing verified RMD debit. It does not move money or emit another debit. */
    public IncomeSourceAttribution forSuccessorRmd(RetirementOwnershipDisposition disposition) {
        return forSuccessorRmd(disposition, null);
    }

    public IncomeSourceAttribution forSuccessorRmd(RetirementOwnershipDisposition disposition,
            com.daviddunn.retirementplanner.domain.rmd.BeneficiaryIraRmd beneficiaryObligation) {
        return forSuccessorRmd(disposition, beneficiaryObligation, null);
    }

    public IncomeSourceAttribution forBeneficiary401kRmd(RetirementOwnershipDisposition disposition,
            com.daviddunn.retirementplanner.domain.rmd.Beneficiary401kRmd obligation) {
        return forSuccessorRmd(disposition, null, Objects.requireNonNull(obligation));
    }

    private IncomeSourceAttribution forSuccessorRmd(RetirementOwnershipDisposition disposition,
            com.daviddunn.retirementplanner.domain.rmd.BeneficiaryIraRmd beneficiaryObligation,
            com.daviddunn.retirementplanner.domain.rmd.Beneficiary401kRmd employerObligation) {
        Objects.requireNonNull(disposition);
        var reference = account.orElseThrow(() -> new IllegalArgumentException("Successor RMD requires an original account debit."));
        if (successorRmd.isPresent() || kind != Kind.RMD || purpose != Purpose.CASH_FLOW || destination.isPresent()
                || pension.isPresent() || grossAmount.signum() <= 0 || !modeledTaxableAmount.equals(Optional.of(grossAmount))
                || !successorRmdSource(reference, disposition.treatment())
                || !reference.equals(disposition.originalAccount())
                || !reference.participantFacts().equals(disposition.originalAccount().participantFacts())
                || !taxYear.equals(OptionalInt.of(disposition.year().getValue()))) {
            throw new IllegalArgumentException("A qualifying successor disposition and matching fully taxable retirement RMD debit are required.");
        }
        var recipient = disposition.effectiveRmdRecipient().orElseThrow();
        if (disposition.treatment() == RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1) {
            if (beneficiaryObligation == null || beneficiaryObligation.year() != taxYear.getAsInt()
                    || beneficiaryObligation.beneficiary() != recipient || beneficiaryObligation.decedent() != reference.owner()
                    || grossAmount.compareTo(beneficiaryObligation.requiredAmount()) > 0) {
                throw new IllegalArgumentException("A beneficiary IRA debit must match the verified payable obligation.");
            }
        } else if (disposition.treatment() == RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1) {
            if (employerObligation == null || employerObligation.year() != taxYear.getAsInt()
                    || employerObligation.beneficiary() != recipient || !employerObligation.source().equals(reference)
                    || !employerObligation.source().participantFacts().equals(reference.participantFacts())
                    || grossAmount.compareTo(employerObligation.requiredAmount()) != 0) {
                throw new IllegalArgumentException("A beneficiary 401(k) debit must satisfy its own verified obligation.");
            }
        }
        if ((beneficiaryObligation != null
                && disposition.treatment() != RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1)
                || (employerObligation != null
                && disposition.treatment() != RetirementOwnershipDisposition.Treatment.MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1)) {
            throw new IllegalArgumentException("The debit and beneficiary obligation treatments must agree.");
        }
        return new IncomeSourceAttribution(kind, purpose, Optional.of(recipient), account, destination,
                description, grossAmount, modeledTaxableAmount, taxYear, pension, conditionalBenefit, niit,
                calculationMode, Optional.of(new SuccessorRmd(reference, recipient, taxYear.getAsInt(),
                        grossAmount, disposition.treatment())));
    }

    private static boolean successorRmdSource(AccountReference reference, RetirementOwnershipDisposition.Treatment treatment) {
        return switch (treatment) {
            case MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1, MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1 ->
                    reference.type() == AccountType.TRADITIONAL_IRA;
            case MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1, MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1 ->
                    reference.type() == AccountType.TRADITIONAL_401K;
            default -> false;
        };
    }

    /** In-memory binding minted from an existing debit and resolved annual ownership. */
    public static final class SuccessorRmd {
        private final AccountReference account;
        private final AccountOwnership recipient;
        private final int year;
        private final BigDecimal debit;
        private final RetirementOwnershipDisposition.Treatment treatment;

        private SuccessorRmd(AccountReference account, AccountOwnership recipient, int year, BigDecimal debit,
                RetirementOwnershipDisposition.Treatment treatment) {
            this.account = account;
            this.recipient = recipient;
            this.year = year;
            this.debit = debit;
            this.treatment = treatment;
        }

        public RetirementOwnershipDisposition.Treatment treatment() {
            return treatment;
        }
    }

    public static IncomeSourceAttribution conditional(com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt receipt) {
        return new IncomeSourceAttribution(receipt.annuityBasis() != null ? Kind.NONQUALIFIED_ANNUITY : receipt.pattern() == com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.PaymentPattern.MONTHLY_RECURRING
                ? Kind.CONDITIONAL_RECURRING : Kind.CONDITIONAL_LUMP_SUM, Purpose.INCOME,
                Optional.of(com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator.owner(receipt.recipient())),
                Optional.empty(), Optional.empty(), receipt.label(), receipt.gross(), Optional.of(receipt.taxable()),
                OptionalInt.of(receipt.year()), Optional.empty(), Optional.of(receipt));
    }

    public static IncomeSourceAttribution niit(com.daviddunn.retirementplanner.domain.tax.NetInvestmentIncomeTaxCalculation calculation, int year) {
        return new IncomeSourceAttribution(Kind.NIIT_EVIDENCE, Purpose.INCOME, Optional.empty(), Optional.empty(), Optional.empty(),
                "Federal NIIT — domestic modeled investment income", BigDecimal.ZERO, Optional.of(BigDecimal.ZERO),
                OptionalInt.of(year), Optional.empty(), Optional.empty(), Optional.of(calculation));
    }

    public static IncomeSourceAttribution calculationPolicy(com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode mode, int year) {
        return new IncomeSourceAttribution(Kind.FINANCIAL_CALCULATION_POLICY, Purpose.INCOME,
                Optional.empty(), Optional.empty(), Optional.empty(), "Financial Calculation Mode: " + mode.label(),
                BigDecimal.ZERO, Optional.of(BigDecimal.ZERO), OptionalInt.of(year), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(mode));
    }

    public static IncomeSourceAttribution withdrawal(
            Account source, BigDecimal amount, Kind kind, Purpose purpose) {
        TaxTreatment treatment = source.getType().getTaxTreatment();
        Optional<BigDecimal> taxable = switch (treatment) {
            case TAX_DEFERRED -> Optional.of(amount);
            case CASH, ROTH -> Optional.of(BigDecimal.ZERO);
            case TAXABLE -> Optional.empty();
        };
        return new IncomeSourceAttribution(kind, purpose, Optional.of(source.getOwnership()),
                Optional.of(AccountReference.capture(source)), Optional.empty(), source.getName(),
                amount, taxable, OptionalInt.empty(), Optional.empty());
    }

    public static Kind withdrawalKind(Account source) {
        TaxTreatment treatment = source.getType().getTaxTreatment();
        return treatment == TaxTreatment.TAX_DEFERRED || treatment == TaxTreatment.ROTH
                ? Kind.ORDINARY_RETIREMENT_WITHDRAWAL : Kind.ORDINARY_WITHDRAWAL;
    }

    public static IncomeSourceAttribution aggregate(
            Kind kind, Purpose purpose, String description, BigDecimal gross,
            Optional<BigDecimal> taxable) {
        return new IncomeSourceAttribution(kind, purpose, Optional.empty(), Optional.empty(),
                Optional.empty(), description, gross, taxable, OptionalInt.empty(), Optional.empty());
    }

    public static IncomeSourceAttribution pension(
            Pension source, AccountOwnership recipient, BigDecimal amount, int year) {
        PensionFacts facts = new PensionFacts(source.getOwnership(), source.getMonthlyBenefit(),
                Optional.ofNullable(source.getSurvivorMonthlyBenefit()), source.getAnnualColaRate(),
                Optional.ofNullable(source.getStartDate()), Optional.ofNullable(source.getEndDate()),
                source.getRetirementParticipantFacts(), source.getPlanClassification(), source.getSourceOrigin());
        return new IncomeSourceAttribution(Kind.PENSION, Purpose.INCOME, Optional.of(recipient),
                Optional.empty(), Optional.empty(), source.getName(), amount, Optional.of(amount),
                OptionalInt.of(year), Optional.of(facts));
    }

    public record PensionFacts(
            AccountOwnership originalOwner,
            BigDecimal monthlyBenefit,
            Optional<BigDecimal> survivorMonthlyBenefit,
            BigDecimal annualColaRate,
            Optional<LocalDate> startDate,
            Optional<LocalDate> endDate,
            RetirementParticipantFacts participantFacts,
            @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,
                    valueFilter = PensionPlanClassification.UnknownFilter.class)
            PensionPlanClassification planClassification,
            @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM,
                    valueFilter = RetirementIncomeSourceOrigin.UnknownFilter.class)
            RetirementIncomeSourceOrigin sourceOrigin) {
        public PensionFacts(AccountOwnership originalOwner, BigDecimal monthlyBenefit,
                Optional<BigDecimal> survivorMonthlyBenefit, BigDecimal annualColaRate,
                Optional<LocalDate> startDate, Optional<LocalDate> endDate,
                RetirementParticipantFacts participantFacts, PensionPlanClassification planClassification) {
            this(originalOwner, monthlyBenefit, survivorMonthlyBenefit, annualColaRate, startDate, endDate,
                    participantFacts, planClassification, RetirementIncomeSourceOrigin.UNKNOWN);
        }

        public PensionFacts(AccountOwnership originalOwner, BigDecimal monthlyBenefit,
                Optional<BigDecimal> survivorMonthlyBenefit, BigDecimal annualColaRate,
                Optional<LocalDate> startDate, Optional<LocalDate> endDate,
                RetirementParticipantFacts participantFacts) {
            this(originalOwner, monthlyBenefit, survivorMonthlyBenefit, annualColaRate, startDate, endDate,
                    participantFacts, PensionPlanClassification.UNKNOWN);
        }
        public PensionFacts(AccountOwnership originalOwner, BigDecimal monthlyBenefit,
                Optional<BigDecimal> survivorMonthlyBenefit, BigDecimal annualColaRate,
                Optional<LocalDate> startDate, Optional<LocalDate> endDate) {
            this(originalOwner, monthlyBenefit, survivorMonthlyBenefit, annualColaRate, startDate, endDate,
                    RetirementParticipantFacts.UNKNOWN);
        }

        public PensionFacts {
            planClassification = Objects.requireNonNullElse(planClassification, PensionPlanClassification.UNKNOWN);
            sourceOrigin = Objects.requireNonNullElse(sourceOrigin, RetirementIncomeSourceOrigin.UNKNOWN);
            Objects.requireNonNull(participantFacts);
            Objects.requireNonNull(originalOwner);
            Objects.requireNonNull(monthlyBenefit);
            Objects.requireNonNull(survivorMonthlyBenefit);
            Objects.requireNonNull(annualColaRate);
            Objects.requireNonNull(startDate);
            Objects.requireNonNull(endDate);
        }
    }

    /**
     * Immutable account description with opaque runtime identity. Identity is
     * reference-based because Account has no persisted ID. It exposes no mutable
     * account and distinguishes accounts with identical names without a schema change.
     */
    public static final class AccountReference {
        private final Account identity;
        private final RetirementParticipantFacts participantFacts;
        private final String name;
        private final AccountOwnership owner;
        private final AccountType type;
        private final Optional<InheritedAccountInformation> inheritedInformation;

        private AccountReference(Account account) {
            this(Objects.requireNonNull(account), account.getRetirementParticipantFacts());
        }

        private AccountReference(Account account, RetirementParticipantFacts frozenParticipantFacts) {
            identity = Objects.requireNonNull(account);
            participantFacts = Objects.requireNonNull(frozenParticipantFacts);
            name = account.getName();
            owner = account.getOwnership();
            type = account.getType();
            inheritedInformation = switch (account) {
                case InheritedTraditionalIRA inherited -> Optional.of(inherited.getInheritedAccountInformation());
                case InheritedRothIRA inherited -> Optional.of(inherited.getInheritedAccountInformation());
                default -> Optional.empty();
            };
        }

        public static AccountReference capture(Account account) {
            return new AccountReference(account);
        }

        /** Reconstruct immutable historical facts without mutating or exposing the identity account. */
        public static AccountReference restore(Account identity, RetirementParticipantFacts frozenParticipantFacts) {
            return new AccountReference(identity, frozenParticipantFacts);
        }

        public RetirementParticipantFacts participantFacts() {
            return participantFacts;
        }

        public String name() {
            return name;
        }

        public AccountOwnership owner() {
            return owner;
        }

        public AccountType type() {
            return type;
        }

        public Optional<InheritedAccountInformation> inheritedInformation() {
            return inheritedInformation;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AccountReference reference && identity == reference.identity;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(identity);
        }
    }

    private static void requireNonNegative(BigDecimal amount) {
        Objects.requireNonNull(amount);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Income source amount cannot be negative.");
        }
    }
}
