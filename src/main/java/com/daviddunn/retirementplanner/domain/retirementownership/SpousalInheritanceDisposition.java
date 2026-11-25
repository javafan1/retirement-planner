package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;

import java.time.LocalDate;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER;

/** Classification of a proposed strategy. It confers no RMD, debit or conversion authority. */
public record SpousalInheritanceDisposition(
        Year year,
        AccountReference originalAccount,
        SpousalInheritanceAssumption assumption,
        Treatment proposedTreatment,
        Basis basis,
        Optional<AccountOwnership> modeledSurvivor,
        Optional<LocalDate> modeledTreatmentDate,
        Optional<Reason> unresolvedReason) {

    public enum Treatment {
        ORIGINAL_OWNER,
        OWN_IRA_ELECTION,
        EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER,
        IRA_BENEFICIARY,
        EMPLOYER_PLAN_BENEFICIARY,
        NONE
    }

    public enum Basis {
        ACCOUNT_FACT_VERIFIED_FROM_INPUT,
        SUPPORTED_MODELING_ASSUMPTION,
        NOT_VERIFIABLE_FROM_EXISTING_INPUT,
        UNSUPPORTED_SCENARIO
    }

    public enum Reason {
        NO_SOLE_SURVIVING_SPOUSE,
        MULTIPLE_MODELED_DEATHS,
        UNSUPPORTED_ACCOUNT_TYPE,
        INCOMPATIBLE_ACCOUNT_FACTS,
        DEATH_BEFORE_PROJECTION_OPENING,
        DEATH_YEAR_OBLIGATION_REQUIRES_SEPARATE_TREATMENT,
        OWN_IRA_FACTS_UNVERIFIED,
        IRA_BENEFICIARY_FACTS_UNVERIFIED,
        EMPLOYER_PLAN_ROLLOVER_FACTS_UNVERIFIED,
        EMPLOYER_PLAN_PAYOUT_FACTS_UNVERIFIED
    }

    public SpousalInheritanceDisposition {
        Objects.requireNonNull(year);
        Objects.requireNonNull(originalAccount);
        Objects.requireNonNull(assumption);
        Objects.requireNonNull(proposedTreatment);
        Objects.requireNonNull(basis);
        Objects.requireNonNull(modeledSurvivor);
        Objects.requireNonNull(modeledTreatmentDate);
        Objects.requireNonNull(unresolvedReason);
    }

    public static Map<AccountReference, SpousalInheritanceDisposition> classify(
            AnnualRetirementOwnership ownership, SpousalInheritanceAssumption assumption) {
        Objects.requireNonNull(ownership);
        Objects.requireNonNull(assumption);
        Map<AccountReference, SpousalInheritanceDisposition> dispositions = new LinkedHashMap<>();
        for (var original : ownership.accounts().values()) {
            var disposition = classifyAccount(ownership, assumption, original);
            dispositions.put(disposition.originalAccount(), disposition);
        }
        return Collections.unmodifiableMap(dispositions);
    }

    private static SpousalInheritanceDisposition classifyAccount(
            AnnualRetirementOwnership ownership, SpousalInheritanceAssumption assumption,
            RetirementOwnershipDisposition original) {
        var account = original.originalAccount();
        var living = ownership.livingOwners();
        if (living.contains(account.owner())) {
            return new SpousalInheritanceDisposition(ownership.year(), account, assumption, Treatment.ORIGINAL_OWNER,
                    Basis.ACCOUNT_FACT_VERIFIED_FROM_INPUT, Optional.empty(), Optional.empty(), Optional.empty());
        }
        var treatment = switch (account.type()) {
            case TRADITIONAL_IRA -> assumption.treatment() == SPOUSE_AS_OWNER
                    ? Treatment.OWN_IRA_ELECTION : Treatment.IRA_BENEFICIARY;
            case TRADITIONAL_401K -> assumption.treatment() == SPOUSE_AS_OWNER
                    ? Treatment.EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER : Treatment.EMPLOYER_PLAN_BENEFICIARY;
            default -> Treatment.NONE;
        };
        if (treatment == Treatment.NONE) {
            return unsupported(ownership, assumption, account, Reason.UNSUPPORTED_ACCOUNT_TYPE);
        }
        if (living.size() != 1 || ownership.birthDates().size() != 2) {
            return unsupported(ownership, assumption, account, Reason.NO_SOLE_SURVIVING_SPOUSE);
        }
        var survivor = living.iterator().next();
        if (ownership.deathView().deathDate(survivor).isPresent()) {
            return unsupported(ownership, assumption, account, Reason.MULTIPLE_MODELED_DEATHS);
        }
        if (account.inheritedInformation().isPresent()
                || (account.type() == AccountType.TRADITIONAL_IRA
                    && !account.participantFacts().equals(RetirementParticipantFacts.UNKNOWN))) {
            return unsupported(ownership, assumption, account, Reason.INCOMPATIBLE_ACCOUNT_FACTS);
        }
        var death = ownership.deathView().deathDate(account.owner()).orElseThrow();
        if (death.isBefore(ownership.projectionOpening())) {
            return unsupported(ownership, assumption, account, Reason.DEATH_BEFORE_PROJECTION_OPENING);
        }
        if (death.getYear() == ownership.year().getValue()) {
            return unsupported(ownership, assumption, account, Reason.DEATH_YEAR_OBLIGATION_REQUIRES_SEPARATE_TREATMENT);
        }

        if ((treatment == Treatment.OWN_IRA_ELECTION || treatment == Treatment.EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER)
                && original.effectiveRmdOwner().filter(survivor::equals).isPresent()) {
            return new SpousalInheritanceDisposition(ownership.year(), account, assumption, treatment,
                    Basis.SUPPORTED_MODELING_ASSUMPTION, Optional.of(survivor),
                    original.spouseAsOwnerEffectiveDate(), Optional.empty());
        }
        if ((treatment == Treatment.IRA_BENEFICIARY || treatment == Treatment.EMPLOYER_PLAN_BENEFICIARY)
                && original.status()
                == RetirementOwnershipDisposition.Status.ELIGIBLE_SPOUSAL_BENEFICIARY
                && original.effectiveRmdRecipient().filter(survivor::equals).isPresent()) {
            return new SpousalInheritanceDisposition(ownership.year(), account, assumption, treatment,
                    Basis.SUPPORTED_MODELING_ASSUMPTION, Optional.of(survivor), Optional.empty(), Optional.empty());
        }
        var reason = switch (treatment) {
            case OWN_IRA_ELECTION -> Reason.OWN_IRA_FACTS_UNVERIFIED;
            case EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER -> Reason.EMPLOYER_PLAN_ROLLOVER_FACTS_UNVERIFIED;
            case IRA_BENEFICIARY -> Reason.IRA_BENEFICIARY_FACTS_UNVERIFIED;
            case EMPLOYER_PLAN_BENEFICIARY -> Reason.EMPLOYER_PLAN_PAYOUT_FACTS_UNVERIFIED;
            default -> throw new IllegalStateException("A successor treatment is required.");
        };
        return new SpousalInheritanceDisposition(ownership.year(), account, assumption, treatment,
                Basis.NOT_VERIFIABLE_FROM_EXISTING_INPUT, Optional.of(survivor), Optional.empty(), Optional.of(reason));
    }

    private static SpousalInheritanceDisposition unsupported(
            AnnualRetirementOwnership ownership, SpousalInheritanceAssumption assumption,
            AccountReference account, Reason reason) {
        return new SpousalInheritanceDisposition(ownership.year(), account, assumption, Treatment.NONE,
                Basis.UNSUPPORTED_SCENARIO, Optional.empty(), Optional.empty(), Optional.of(reason));
    }
}
