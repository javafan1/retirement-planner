package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure assessment of the supported fully pretax conversion transaction.
 * Account-type support is not a determination of legal distribution eligibility.
 */
public final class RothConversionEligibilityPolicy {
    public enum EmployerDistributionAvailability {
        NOT_APPLICABLE, ESTABLISHED, UNAVAILABLE, UNKNOWN
    }

    public enum Reason {
        UNSUPPORTED_SOURCE, MISSING_DESTINATION, UNSUPPORTED_DESTINATION,
        OWNER_NOT_PRESENT, DIFFERENT_OWNER, SAME_ACCOUNT,
        NO_AVAILABLE_BALANCE, EMPLOYER_DISTRIBUTION_UNAVAILABLE,
        EMPLOYER_DISTRIBUTION_UNKNOWN, UNSUPPORTED_TRANSACTION
    }

    public record Assessment(
            boolean supportedPretaxSource,
            boolean supportedDestination,
            boolean sameOwner,
            boolean ownerPresent,
            BigDecimal availableProjectedBalance,
            EmployerDistributionAvailability employerDistributionAvailability,
            Set<Reason> reasons) {

        public Assessment {
            Objects.requireNonNull(availableProjectedBalance);
            Objects.requireNonNull(employerDistributionAvailability);
            reasons = Set.copyOf(reasons);
        }

        /** Compatibility permission only; UNKNOWN is deliberately retained in the assessment. */
        public boolean executableUnderLegacyEmployerAssumption() {
            return reasons.stream().allMatch(reason -> reason == Reason.EMPLOYER_DISTRIBUTION_UNKNOWN);
        }

        public boolean usesLegacyEmployerAssumption() {
            return executableUnderLegacyEmployerAssumption()
                    && employerDistributionAvailability == EmployerDistributionAvailability.UNKNOWN;
        }

        public BigDecimal maximumConvertibleAmount() {
            return executableUnderLegacyEmployerAssumption() ? availableProjectedBalance : BigDecimal.ZERO;
        }
    }

    public static boolean isSupportedPretaxSource(AccountType type) {
        return switch (Objects.requireNonNull(type)) {
            case TRADITIONAL_IRA, ROLLOVER_IRA, TRADITIONAL_401K, TRADITIONAL_403B -> true;
            default -> false;
        };
    }

    public static boolean isSupportedDestination(AccountType type) {
        return Objects.requireNonNull(type) == AccountType.ROTH_IRA;
    }

    public static boolean isEmployerPretaxSource(AccountType type) {
        return type == AccountType.TRADITIONAL_401K || type == AccountType.TRADITIONAL_403B;
    }

    /** Existing account facts do not establish employer distribution availability. */
    public Assessment assessUnderLegacyEmployerAssumption(
            Account source, Account destination, Set<AccountOwnership> presentOwners,
            BigDecimal availableProjectedBalance) {
        Objects.requireNonNull(source);
        return assess(source, destination, presentOwners, availableProjectedBalance, availabilityAt(source, null));
    }

    public Assessment assessForYear(Account source, Account destination, Set<AccountOwnership> owners,
            BigDecimal balance, int calendarYear) {
        return assess(source, destination, owners, balance, availabilityAt(source, calendarYear));
    }

    public EmployerDistributionAvailability availabilityAt(Account source, Integer calendarYear) {
        Objects.requireNonNull(source);
        if (!isEmployerPretaxSource(source.getType())) return EmployerDistributionAvailability.NOT_APPLICABLE;
        var configured = source.getRothConversionAvailability();
        if (configured == null) return EmployerDistributionAvailability.UNKNOWN;
        if (calendarYear == null && configured.firstEligibleYear() != null)
            throw new IllegalArgumentException("A calendar year is required for future employer conversion availability.");
        boolean available = configured.status() == EmployerRothConversionAvailability.Status.AVAILABLE
                || calendarYear != null && configured.availableIn(calendarYear);
        return available ? EmployerDistributionAvailability.ESTABLISHED : EmployerDistributionAvailability.UNAVAILABLE;
    }

    /** Availability is explicit run-only information, never inferred from age, balance or participant facts. */
    public Assessment assess(
            Account source, Account destination, Set<AccountOwnership> presentOwners,
            BigDecimal availableProjectedBalance, EmployerDistributionAvailability availability) {
        Objects.requireNonNull(source);
        Set<AccountOwnership> owners = Set.copyOf(Objects.requireNonNull(presentOwners));
        Objects.requireNonNull(availableProjectedBalance);
        Objects.requireNonNull(availability);
        if (isEmployerPretaxSource(source.getType())
                == (availability == EmployerDistributionAvailability.NOT_APPLICABLE)) {
            throw new IllegalArgumentException("Employer distribution availability must match the source category.");
        }

        boolean supportedSource = isSupportedPretaxSource(source.getType());
        boolean supportedDestination = destination != null && isSupportedDestination(destination.getType());
        boolean sameOwner = destination != null && source.getOwnership() == destination.getOwnership();
        boolean ownerPresent = source.getOwnership() != AccountOwnership.JOINT && owners.contains(source.getOwnership());
        var reasons = EnumSet.noneOf(Reason.class);
        if (!supportedSource) reasons.add(Reason.UNSUPPORTED_SOURCE);
        if (destination == null) reasons.add(Reason.MISSING_DESTINATION);
        else if (!supportedDestination) reasons.add(Reason.UNSUPPORTED_DESTINATION);
        if (destination != null && !sameOwner) reasons.add(Reason.DIFFERENT_OWNER);
        if (source == destination) reasons.add(Reason.SAME_ACCOUNT);
        if (!ownerPresent) reasons.add(Reason.OWNER_NOT_PRESENT);
        if (!supportedSource || destination != null && (!supportedDestination || !sameOwner || source == destination)) {
            reasons.add(Reason.UNSUPPORTED_TRANSACTION);
        }
        if (availableProjectedBalance.signum() <= 0) reasons.add(Reason.NO_AVAILABLE_BALANCE);
        if (availability == EmployerDistributionAvailability.UNAVAILABLE) reasons.add(Reason.EMPLOYER_DISTRIBUTION_UNAVAILABLE);
        if (availability == EmployerDistributionAvailability.UNKNOWN) reasons.add(Reason.EMPLOYER_DISTRIBUTION_UNKNOWN);
        return new Assessment(supportedSource, supportedDestination, sameOwner, ownerPresent,
                availableProjectedBalance.max(BigDecimal.ZERO), availability, reasons);
    }
}
