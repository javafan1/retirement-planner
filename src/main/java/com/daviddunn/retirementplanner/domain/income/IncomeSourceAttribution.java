package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedRothIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;

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
        Optional<PensionFacts> pension) {

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
        OTHER
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
        account.ifPresent(reference -> {
            if (owner.orElse(null) != reference.owner()) {
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
                description, grossAmount, modeledTaxableAmount, OptionalInt.of(year), pension);
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
                Optional.ofNullable(source.getStartDate()), Optional.ofNullable(source.getEndDate()));
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
            Optional<LocalDate> endDate) {
        public PensionFacts {
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
        private final String name;
        private final AccountOwnership owner;
        private final AccountType type;
        private final Optional<InheritedAccountInformation> inheritedInformation;

        private AccountReference(Account account) {
            identity = Objects.requireNonNull(account);
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
