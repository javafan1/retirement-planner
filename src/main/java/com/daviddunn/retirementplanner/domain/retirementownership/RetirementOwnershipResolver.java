package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.rules.RmdRules;
import com.daviddunn.retirementplanner.domain.rules.RmdStartingAgeRule;

import java.time.LocalDate;
import java.time.Year;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition.RefusalReason.*;

/** Pure resolution of ownership for a hypothetical, explicitly affirmed own-IRA election. */
public final class RetirementOwnershipResolver {

    public enum Affirmation {
        SOLE_DIRECT_SPOUSE_BENEFICIARY(SOLE_DIRECT_SPOUSE_BENEFICIARY_UNVERIFIED),
        UNLIMITED_WITHDRAWAL_RIGHTS(UNLIMITED_WITHDRAWAL_RIGHTS_UNVERIFIED),
        FULLY_PRETAX_AND_NO_RELEVANT_BASIS(FULLY_PRETAX_AND_NO_RELEVANT_BASIS_UNVERIFIED),
        COMPATIBLE_PERSONAL_IRA_HISTORY(ACCOUNT_HISTORY_UNVERIFIED_OR_CONFLICTING),
        NO_UNRESOLVED_MANDATORY_DISTRIBUTION(MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED);

        private final RetirementOwnershipDisposition.RefusalReason missingReason;

        Affirmation(RetirementOwnershipDisposition.RefusalReason missingReason) {
            this.missingReason = missingReason;
        }
    }

    /** Run-only planning input. Missing affirmations are unresolved and prevent admission.
     * Mandatory distribution evidence covers the proposed own-IRA election and any earlier obligations.
     */
    public record SpouseAsOwnerAssumption(
            AccountReference originalAccount,
            AccountOwnership survivor,
            LocalDate survivorBirthDate,
            LocalDate modeledDeathDate,
            LocalDate modeledEffectiveDate,
            Set<Affirmation> affirmations) {
        public SpouseAsOwnerAssumption {
            Objects.requireNonNull(originalAccount, "Original account reference is required.");
            Objects.requireNonNull(survivor, "Surviving spouse slot is required.");
            Objects.requireNonNull(survivorBirthDate, "Surviving spouse birth date is required.");
            Objects.requireNonNull(modeledDeathDate, "Modeled death date is required.");
            Objects.requireNonNull(modeledEffectiveDate, "Modeled effective date is required.");
            affirmations = Set.copyOf(affirmations);
            if (originalAccount.owner() == JOINT || survivor == JOINT || survivor == originalAccount.owner()) {
                throw new IllegalArgumentException("The assumption must identify opposite individual spouse slots.");
            }
        }
    }

    private RetirementOwnershipResolver() { }

    /**
     * Applies the owner's hypothetical personal-IRA convention. The caller separately establishes
     * which original positions have no unresolved earlier or election-year mandatory obligations.
     */
    public static AnnualRetirementOwnership resolveForPlanning(
            Year year, LocalDate opening, EffectiveHouseholdDeathView deaths,
            Map<AccountOwnership, LocalDate> birthDates, RmdRules rules,
            List<AccountReference> originalAccounts, SpousalInheritanceAssumption selection,
            Set<AccountReference> clearedMandatoryHistory) {
        return resolveForPlanning(year, opening, deaths, birthDates, rules, originalAccounts, selection,
                clearedMandatoryHistory, Map.of());
    }

    public static AnnualRetirementOwnership resolveForPlanning(
            Year year, LocalDate opening, EffectiveHouseholdDeathView deaths,
            Map<AccountOwnership, LocalDate> birthDates, RmdRules rules,
            List<AccountReference> originalAccounts, SpousalInheritanceAssumption selection,
            Set<AccountReference> clearedMandatoryHistory, Map<AccountReference, Spousal401kRollover> rollovers) {
        Objects.requireNonNull(selection, "Spousal inheritance selection is required.");
        var accounts = List.copyOf(originalAccounts);
        var roster = Map.copyOf(birthDates);
        var cleared = Set.copyOf(clearedMandatoryHistory);
        if (!Set.copyOf(accounts).containsAll(cleared)) {
            throw new IllegalArgumentException("Mandatory history references an account outside the original portfolio.");
        }
        List<SpouseAsOwnerAssumption> assumptions = new java.util.ArrayList<>();
        if (selection.treatment() == SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER
                && roster.keySet().equals(Set.of(PRIMARY, SPOUSE))
                && deaths.state(SPOUSE, year.getValue()) != EffectiveHouseholdDeathView.PersonState.ABSENT) {
            for (var account : accounts) {
                if (account.type() != AccountType.TRADITIONAL_IRA) continue;
                var survivor = account.owner() == PRIMARY ? SPOUSE : PRIMARY;
                var death = deaths.deathDate(account.owner());
                if (death.isEmpty() || death.get().getYear() >= Year.MAX_VALUE
                        || death.get().getYear() > year.getValue() || !deaths.isAlive(survivor, year.getValue())) {
                    continue;
                }
                // These are assumptions of the admitted pretax model, not documentary findings.
                var facts = EnumSet.of(Affirmation.SOLE_DIRECT_SPOUSE_BENEFICIARY,
                        Affirmation.UNLIMITED_WITHDRAWAL_RIGHTS, Affirmation.FULLY_PRETAX_AND_NO_RELEVANT_BASIS,
                        Affirmation.COMPATIBLE_PERSONAL_IRA_HISTORY);
                if (cleared.contains(account)) facts.add(Affirmation.NO_UNRESOLVED_MANDATORY_DISTRIBUTION);
                assumptions.add(new SpouseAsOwnerAssumption(account, survivor, roster.get(survivor),
                        death.get(), death.get().plusYears(1), facts));
            }
        }
        if (!rollovers.isEmpty() && selection.treatment() != SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER) {
            throw new IllegalArgumentException("A direct rollover requires selected spouse-as-owner treatment.");
        }
        return resolve(year, opening, deaths, roster, rules, accounts, assumptions, Map.copyOf(rollovers), Set.of());
    }

    /** Explicit no-election, sole-spouse life-expectancy method, with mandatory history already cleared. */
    public static AnnualRetirementOwnership resolveForBeneficiaryPlanning(
            Year year, LocalDate opening, EffectiveHouseholdDeathView deaths,
            Map<AccountOwnership, LocalDate> birthDates, RmdRules rules, List<AccountReference> originalAccounts,
            SpousalInheritanceAssumption selection, Set<AccountReference> clearedMandatoryHistory) {
        if (selection.treatment() != SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY) {
            throw new IllegalArgumentException("The life-expectancy method requires retained spouse-as-beneficiary status.");
        }
        return resolve(year, opening, deaths, birthDates, rules, originalAccounts,
                List.of(), Map.of(), Set.copyOf(clearedMandatoryHistory));
    }

    /** Resolves membership using birth and account facts frozen with the existing death view and RMD rules. */
    public static AnnualRetirementOwnership resolve(
            Year year,
            LocalDate projectionOpening,
            EffectiveHouseholdDeathView deathView,
            Map<AccountOwnership, LocalDate> birthDates,
            RmdRules rmdRules,
            List<AccountReference> originalAccounts,
            List<SpouseAsOwnerAssumption> assumptions) {
        return resolve(year, projectionOpening, deathView, birthDates, rmdRules, originalAccounts, assumptions, Map.of(), Set.of());
    }

    private static AnnualRetirementOwnership resolve(
            Year year, LocalDate projectionOpening, EffectiveHouseholdDeathView deathView,
            Map<AccountOwnership, LocalDate> birthDates, RmdRules rmdRules, List<AccountReference> originalAccounts,
            List<SpouseAsOwnerAssumption> assumptions, Map<AccountReference, Spousal401kRollover> rollovers,
            Set<AccountReference> beneficiaryAccounts) {
        Objects.requireNonNull(year, "Projection year is required.");
        Objects.requireNonNull(projectionOpening, "Projection opening is required.");
        Objects.requireNonNull(deathView, "Frozen household death view is required.");
        Objects.requireNonNull(rmdRules, "Frozen RMD rules are required.");
        if (year.getValue() < projectionOpening.getYear() || projectionOpening.getYear() <= 0) {
            throw new IllegalArgumentException("The ownership year must be in the modeled period.");
        }

        Map<AccountOwnership, LocalDate> roster = Map.copyOf(birthDates);
        Set<AccountOwnership> living = livingOwners(year, projectionOpening, deathView, roster);
        List<AccountReference> accounts = List.copyOf(originalAccounts);
        Map<AccountReference, AccountReference> identities = new LinkedHashMap<>();
        for (AccountReference account : accounts) {
            if (identities.putIfAbsent(account, account) != null) {
                throw new IllegalArgumentException("Duplicate original account identity.");
            }
            if (account.owner() != JOINT && !roster.containsKey(account.owner())) {
                throw new IllegalArgumentException("Original account references an absent household member.");
            }
        }
        Map<AccountReference, SpouseAsOwnerAssumption> byAccount =
                validateAssumptions(List.copyOf(assumptions), identities, roster, deathView);
        if (!identities.keySet().containsAll(beneficiaryAccounts)) {
            throw new IllegalArgumentException("Beneficiary history must identify original portfolio occurrences.");
        }
        for (var entry : rollovers.entrySet()) {
            var original = identities.get(entry.getKey());
            var event = entry.getValue();
            if (original == null || !original.equals(event.source())
                    || !original.participantFacts().equals(event.source().participantFacts())) {
                throw new IllegalArgumentException("A rollover must identify an original employer-plan occurrence and participant.");
            }
        }

        Map<AccountReference, RetirementOwnershipDisposition> dispositions = new LinkedHashMap<>();
        for (AccountReference account : accounts) {
            dispositions.put(account, resolveAccount(
                    account, byAccount.get(account), year, projectionOpening, deathView, roster, living, rmdRules,
                    rollovers.get(account), beneficiaryAccounts.contains(account)));
        }
        return new AnnualRetirementOwnership(year, projectionOpening, deathView, living, roster, dispositions);
    }

    private static Set<AccountOwnership> livingOwners(
            Year year, LocalDate opening, EffectiveHouseholdDeathView deathView, Map<AccountOwnership, LocalDate> roster) {
        boolean hasSpouse = deathView.state(SPOUSE, year.getValue()) != EffectiveHouseholdDeathView.PersonState.ABSENT;
        if (!roster.keySet().equals(hasSpouse ? Set.of(PRIMARY, SPOUSE) : Set.of(PRIMARY))) {
            throw new IllegalArgumentException("Birth dates must identify exactly the members of the frozen death view.");
        }
        Set<AccountOwnership> living = EnumSet.noneOf(AccountOwnership.class);
        for (var member : roster.entrySet()) {
            var death = deathView.deathDate(member.getKey());
            if (member.getValue().isAfter(opening) || death.filter(member.getValue()::isAfter).isPresent()) {
                throw new IllegalArgumentException("A modeled household member's birth date conflicts with the chronology.");
            }
            if (deathView.isAlive(member.getKey(), year.getValue())) {
                living.add(member.getKey());
            }
        }
        return living;
    }

    private static Map<AccountReference, SpouseAsOwnerAssumption> validateAssumptions(
            List<SpouseAsOwnerAssumption> assumptions,
            Map<AccountReference, AccountReference> identities,
            Map<AccountOwnership, LocalDate> roster,
            EffectiveHouseholdDeathView deathView) {
        Map<AccountReference, SpouseAsOwnerAssumption> byAccount = new LinkedHashMap<>();
        for (SpouseAsOwnerAssumption assumption : assumptions) {
            AccountReference account = assumption.originalAccount();
            AccountReference original = identities.get(account);
            if (original == null) {
                throw new IllegalArgumentException("The assumption must reference an original account in this household.");
            }
            if (!original.participantFacts().equals(account.participantFacts())
                    || !original.inheritedInformation().equals(account.inheritedInformation())) {
                throw new IllegalArgumentException("The assumed account facts must match the original account reference.");
            }
            if (!assumption.survivorBirthDate().equals(roster.get(assumption.survivor()))) {
                throw new IllegalArgumentException("The assumed survivor must match the frozen household member.");
            }
            if (deathView.deathDate(account.owner()).filter(assumption.modeledDeathDate()::equals).isEmpty()) {
                throw new IllegalArgumentException("The assumed death must match the frozen death view.");
            }
            if (byAccount.putIfAbsent(account, assumption) != null) {
                throw new IllegalArgumentException("An original account may have only one spouse-as-owner assumption.");
            }
        }
        return byAccount;
    }

    private static RetirementOwnershipDisposition resolveAccount(
            AccountReference account, SpouseAsOwnerAssumption assumption, Year year, LocalDate opening,
            EffectiveHouseholdDeathView deathView, Map<AccountOwnership, LocalDate> roster,
            Set<AccountOwnership> living, RmdRules rmdRules, Spousal401kRollover rollover, boolean beneficiary) {
        if (!account.type().requiresIndividualOwnership()) {
            return assumption == null
                    ? RetirementOwnershipDisposition.notApplicable(year, account)
                    : RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_SUCCESSOR_ACCOUNT);
        }
        if (living.contains(account.owner())) {
            return account.type().isSubjectToOwnerRmd()
                    ? RetirementOwnershipDisposition.original(year, account)
                    : RetirementOwnershipDisposition.notApplicable(year, account);
        }
        if (living.size() != 1 || roster.size() != 2) {
            return RetirementOwnershipDisposition.refused(year, account, NO_SOLE_SURVIVING_SPOUSE);
        }
        if (deathView.deathDate(living.iterator().next()).isPresent()) {
            return RetirementOwnershipDisposition.refused(year, account, MULTIPLE_MODELED_DEATHS);
        }
        if (rollover != null) {
            var death = deathView.deathDate(account.owner()).orElseThrow();
            var participantAge = supportedStartingAge(roster.get(account.owner()), rmdRules);
            if (death.isBefore(opening) || !rollover.effectiveDate().equals(death.plusYears(1))
                    || !living.contains(rollover.survivor()) || participantAge.isEmpty()
                    || supportedStartingAge(roster.get(rollover.survivor()), rmdRules).isEmpty()) {
                return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_EFFECTIVE_DATE);
            }
            if (year.getValue() < rollover.effectiveDate().getYear()) {
                return RetirementOwnershipDisposition.refused(year, account, BEFORE_EFFECTIVE_YEAR);
            }
            if (rollover.effectiveDate().getYear() >= roster.get(account.owner()).getYear() + participantAge.getAsInt()) {
                return RetirementOwnershipDisposition.refused(year, account, MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED);
            }
            return RetirementOwnershipDisposition.successor(year, account, rollover.survivor(), rollover.effectiveDate());
        }
        boolean retainedEmployer = beneficiary && account.type() == AccountType.TRADITIONAL_401K;
        if ((!retainedEmployer && account.type() != AccountType.TRADITIONAL_IRA)
                || account.inheritedInformation().isPresent()
                || (!retainedEmployer && !account.participantFacts().equals(RetirementParticipantFacts.UNKNOWN))) {
            return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_SUCCESSOR_ACCOUNT);
        }
        if (beneficiary) {
            var survivor = living.iterator().next();
            var death = deathView.deathDate(account.owner()).orElseThrow();
            var originalBirth = roster.get(account.owner());
            var originalAge = supportedStartingAge(originalBirth, rmdRules);
            if (death.isBefore(opening) || death.getYear() < 2025 || year.getValue() <= death.getYear()) {
                return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_EFFECTIVE_DATE);
            }
            if (originalAge.isEmpty() || supportedStartingAge(roster.get(survivor), rmdRules).isEmpty()) {
                return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_BIRTH_COHORT);
            }
            if (death.getYear() >= originalBirth.getYear() + originalAge.getAsInt()) {
                return RetirementOwnershipDisposition.refused(year, account, DEATH_IN_OR_AFTER_FIRST_DISTRIBUTION_YEAR);
            }
            var facts = account.participantFacts();
            if (retainedEmployer && ((facts.separationDate() != null && facts.separationDate().isAfter(death))
                    || (facts.retirementConditionsMetDate() != null && facts.retirementConditionsMetDate().isAfter(death)))) {
                return RetirementOwnershipDisposition.refused(year, account, ACCOUNT_HISTORY_UNVERIFIED_OR_CONFLICTING);
            }
            return RetirementOwnershipDisposition.beneficiary(year, account, survivor);
        }
        if (assumption == null) {
            return RetirementOwnershipDisposition.refused(year, account, MISSING_SPOUSE_AS_OWNER_ASSUMPTION);
        }
        return resolveSuccessor(account, assumption, year, opening, roster.get(account.owner()), rmdRules);
    }

    private static RetirementOwnershipDisposition resolveSuccessor(
            AccountReference account, SpouseAsOwnerAssumption assumption, Year year,
            LocalDate opening, LocalDate originalBirthDate, RmdRules rmdRules) {
        LocalDate death = assumption.modeledDeathDate();
        LocalDate effective = assumption.modeledEffectiveDate();
        if (death.isBefore(opening)) {
            return RetirementOwnershipDisposition.refused(year, account, DEATH_BEFORE_PROJECTION_OPENING);
        }
        if (effective.getDayOfYear() != 1 || effective.getYear() != death.getYear() + 1) {
            return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_EFFECTIVE_DATE);
        }
        if (year.getValue() < effective.getYear()) {
            return RetirementOwnershipDisposition.refused(year, account, BEFORE_EFFECTIVE_YEAR);
        }
        var originalStartingAge = supportedStartingAge(originalBirthDate, rmdRules);
        if (originalStartingAge.isEmpty() || supportedStartingAge(assumption.survivorBirthDate(), rmdRules).isEmpty()) {
            return RetirementOwnershipDisposition.refused(year, account, UNSUPPORTED_BIRTH_COHORT);
        }
        if (death.getYear() - originalBirthDate.getYear() >= originalStartingAge.getAsInt()) {
            return RetirementOwnershipDisposition.refused(year, account, DEATH_IN_OR_AFTER_FIRST_DISTRIBUTION_YEAR);
        }
        for (Affirmation fact : Affirmation.values()) {
            if (!assumption.affirmations().contains(fact)) {
                return RetirementOwnershipDisposition.refused(year, account, fact.missingReason);
            }
        }
        return RetirementOwnershipDisposition.successor(year, account, assumption.survivor(), effective);
    }

    private static OptionalInt supportedStartingAge(LocalDate birthDate, RmdRules rmdRules) {
        // This scope excludes the unresolved 1959 cohort and the legacy integer-age approximation of 70½.
        if (birthDate.getYear() == 1959 || birthDate.isBefore(LocalDate.of(1949, 7, 1))) {
            return OptionalInt.empty();
        }
        return rmdRules.getStartingAgeRules().stream()
                .filter(rule -> rule.appliesTo(birthDate))
                .mapToInt(RmdStartingAgeRule::getRmdStartingAge)
                .findFirst();
    }
}
