package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;

import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

/** A resolution for an original position in one calendar year. No transaction has occurred. */
public final class RetirementOwnershipDisposition {

    public enum Status {
        SUPPORTED_ORIGINAL_OWNER,
        ELIGIBLE_SUCCESSOR_OWNERSHIP,
        ELIGIBLE_SPOUSAL_BENEFICIARY,
        UNSUPPORTED_OR_UNRESOLVED,
        NOT_APPLICABLE
    }

    public enum Treatment {
        ORIGINAL_OWNER,
        MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1,
        MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1,
        MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1,
        MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1,
        NONE
    }

    public enum RefusalReason {
        NO_SOLE_SURVIVING_SPOUSE,
        MULTIPLE_MODELED_DEATHS,
        UNSUPPORTED_SUCCESSOR_ACCOUNT,
        MISSING_SPOUSE_AS_OWNER_ASSUMPTION,
        DEATH_BEFORE_PROJECTION_OPENING,
        UNSUPPORTED_EFFECTIVE_DATE,
        BEFORE_EFFECTIVE_YEAR,
        UNSUPPORTED_BIRTH_COHORT,
        DEATH_IN_OR_AFTER_FIRST_DISTRIBUTION_YEAR,
        SOLE_DIRECT_SPOUSE_BENEFICIARY_UNVERIFIED,
        UNLIMITED_WITHDRAWAL_RIGHTS_UNVERIFIED,
        FULLY_PRETAX_AND_NO_RELEVANT_BASIS_UNVERIFIED,
        ACCOUNT_HISTORY_UNVERIFIED_OR_CONFLICTING,
        MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED
    }

    private final Year year;
    private final AccountReference originalAccount;
    private final Status status;
    private final Optional<AccountOwnership> effectiveRmdOwner;
    private final Optional<LocalDate> spouseAsOwnerEffectiveDate;
    private final Optional<RefusalReason> refusalReason;

    private RetirementOwnershipDisposition(
            Year year,
            AccountReference originalAccount,
            Status status,
            Optional<AccountOwnership> effectiveRmdOwner,
            Optional<LocalDate> spouseAsOwnerEffectiveDate,
            Optional<RefusalReason> refusalReason) {
        this.year = year;
        this.originalAccount = originalAccount;
        this.status = status;
        this.effectiveRmdOwner = effectiveRmdOwner;
        this.spouseAsOwnerEffectiveDate = spouseAsOwnerEffectiveDate;
        this.refusalReason = refusalReason;
    }

    static RetirementOwnershipDisposition original(Year year, AccountReference account) {
        return new RetirementOwnershipDisposition(year, account, Status.SUPPORTED_ORIGINAL_OWNER,
                Optional.of(account.owner()), Optional.empty(), Optional.empty());
    }

    static RetirementOwnershipDisposition successor(
            Year year, AccountReference account, AccountOwnership survivor, LocalDate effectiveDate) {
        return new RetirementOwnershipDisposition(year, account, Status.ELIGIBLE_SUCCESSOR_OWNERSHIP,
                Optional.of(survivor), Optional.of(effectiveDate), Optional.empty());
    }

    static RetirementOwnershipDisposition refused(Year year, AccountReference account, RefusalReason reason) {
        return new RetirementOwnershipDisposition(year, account, Status.UNSUPPORTED_OR_UNRESOLVED,
                Optional.empty(), Optional.empty(), Optional.of(reason));
    }

    static RetirementOwnershipDisposition beneficiary(Year year, AccountReference account, AccountOwnership survivor) {
        return new RetirementOwnershipDisposition(year, account, Status.ELIGIBLE_SPOUSAL_BENEFICIARY,
                Optional.of(survivor), Optional.empty(), Optional.empty());
    }

    static RetirementOwnershipDisposition notApplicable(Year year, AccountReference account) {
        return new RetirementOwnershipDisposition(year, account, Status.NOT_APPLICABLE,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Year year() {
        return year;
    }

    public AccountReference originalAccount() {
        return originalAccount;
    }

    public Status status() {
        return status;
    }

    public Treatment treatment() {
        return switch (status) {
            case SUPPORTED_ORIGINAL_OWNER -> Treatment.ORIGINAL_OWNER;
            case ELIGIBLE_SPOUSAL_BENEFICIARY -> originalAccount.type() == AccountType.TRADITIONAL_401K
                    ? Treatment.MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1
                    : Treatment.MODELED_SPOUSAL_BENEFICIARY_IRA_SINGLE_LIFE_V1;
            case ELIGIBLE_SUCCESSOR_OWNERSHIP ->
                    originalAccount.type() == AccountType.TRADITIONAL_401K
                            ? Treatment.MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1
                            : Treatment.MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1;
            case UNSUPPORTED_OR_UNRESOLVED, NOT_APPLICABLE -> Treatment.NONE;
        };
    }

    /** Ownership grouping for this year. The applicable table, requirement, and any payment are separate. */
    public Optional<AccountOwnership> effectiveRmdOwner() {
        return status == Status.ELIGIBLE_SPOUSAL_BENEFICIARY ? Optional.empty() : effectiveRmdOwner;
    }

    public Optional<AccountOwnership> effectiveRmdRecipient() {
        return effectiveRmdOwner;
    }

    public RmdAccountCategory effectiveRmdCategory() {
        if (effectiveRmdOwner().isEmpty()) return RmdAccountCategory.NOT_APPLICABLE;
        return treatment() == Treatment.MODELED_SPOUSAL_401K_DIRECT_ROLLOVER_V1
                ? RmdAccountCategory.IRA : originalAccount.type().getRmdAccountCategory();
    }

    public Optional<LocalDate> spouseAsOwnerEffectiveDate() {
        return spouseAsOwnerEffectiveDate;
    }

    public Optional<RefusalReason> refusalReason() {
        return refusalReason;
    }
}
