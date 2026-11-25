package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.rules.RmdRules;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition.RefusalReason;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver.Affirmation;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver.SpouseAsOwnerAssumption;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition.RefusalReason.*;
import static com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipDisposition.Status.*;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class RetirementOwnershipResolverTest {
    private static final LocalDate OPENING = LocalDate.of(2033, 1, 1);
    private static final LocalDate DEATH = LocalDate.of(2034, 1, 1);
    private static final LocalDate ELECTION = LocalDate.of(2035, 1, 1);
    private static final LocalDate PRIMARY_BIRTH = LocalDate.of(1960, 6, 15);
    private static final LocalDate SPOUSE_BIRTH = LocalDate.of(1962, 6, 15);
    private static final Map<AccountOwnership, LocalDate> COUPLE = Map.of(PRIMARY, PRIMARY_BIRTH, SPOUSE, SPOUSE_BIRTH);
    private static final Set<Affirmation> AFFIRMED = Set.copyOf(EnumSet.allOf(Affirmation.class));

    private static RmdRules rules;

    @BeforeAll
    static void loadRules() throws IOException {
        rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getRmdRules();
    }

    @Test
    void bothLivingOwnersRetainTheirSeparateGroupsAndAccountOrder() {
        var spouse = ira(SPOUSE);
        var primary = ira(PRIMARY);
        var cash = AccountReference.capture(new CheckingAccount("Synthetic cash", JOINT, ZERO));
        var result = resolve(2033, HouseholdLifetimeScenario.bothSurvive(), List.of(spouse, primary, cash));

        assertEquals(Set.of(PRIMARY, SPOUSE), result.livingOwners());
        assertTrue(result.deathView().firstDeathYear().isEmpty());
        assertEquals(List.of(spouse, primary, cash), new ArrayList<>(result.accounts().keySet()));
        assertEquals(SUPPORTED_ORIGINAL_OWNER, result.accounts().get(spouse).status());
        assertEquals(Optional.of(SPOUSE), result.accounts().get(spouse).effectiveRmdOwner());
        assertEquals(Optional.of(PRIMARY), result.accounts().get(primary).effectiveRmdOwner());
        assertEquals(NOT_APPLICABLE, result.accounts().get(cash).status());
        assertTrue(result.accounts().get(cash).effectiveRmdOwner().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void commonCaseUsesThePlanChoiceAndStillRequiresResolvedMandatoryHistory(AccountOwnership decedent) {
        var spouse = other(decedent);
        var ira = ira(decedent);
        var own = ira(spouse);
        var employer = AccountReference.capture(new Traditional401K("Synthetic employer", decedent, ZERO));
        var accounts = List.of(ira, own, employer);
        var household = new Household(new Person("Synthetic", "Primary", PRIMARY_BIRTH),
                new Person("Synthetic", "Spouse", SPOUSE_BIRTH));
        var deaths = EffectiveHouseholdDeathView.resolve(household,
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), Optional.of(death(decedent, 2034)));
        var selection = new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER);
        var unresolved = RetirementOwnershipResolver.resolveForPlanning(Year.of(2037), OPENING,
                deaths, COUPLE, rules, accounts, selection, Set.of());
        assertRefused(unresolved, ira, MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED);
        var resolved = RetirementOwnershipResolver.resolveForPlanning(Year.of(2037), OPENING,
                deaths, COUPLE, rules, accounts, selection, Set.of(ira));
        assertSame(ira, resolved.accounts().get(ira).originalAccount());
        assertEquals(Optional.of(spouse), resolved.accounts().get(ira).effectiveRmdOwner());
        assertEquals(Optional.of(ELECTION), resolved.accounts().get(ira).spouseAsOwnerEffectiveDate());
        assertEquals(SUPPORTED_ORIGINAL_OWNER, resolved.accounts().get(own).status());
        assertRefused(resolved, employer, UNSUPPORTED_SUCCESSOR_ACCOUNT);
        assertEquals(SpousalInheritanceDisposition.Basis.SUPPORTED_MODELING_ASSUMPTION,
                SpousalInheritanceDisposition.classify(resolved, selection).get(ira).basis());
        var before = RetirementOwnershipResolver.resolveForPlanning(Year.of(2034), OPENING,
                deaths, COUPLE, rules, accounts, selection, Set.of(ira));
        assertRefused(before, ira, BEFORE_EFFECTIVE_YEAR);

        var beneficiary = new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY);
        var pending = RetirementOwnershipResolver.resolveForPlanning(Year.of(2037), OPENING,
                deaths, COUPLE, rules, accounts, beneficiary, Set.of(ira));
        assertTrue(pending.accounts().get(ira).effectiveRmdOwner().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RetirementOwnershipResolver.resolveForPlanning(
                Year.of(2037), OPENING, deaths, COUPLE, rules, accounts, selection, Set.of(ira(decedent))));
    }

    @ParameterizedTest
    @EnumSource(value = AccountOwnership.class, names = {"PRIMARY", "SPOUSE"})
    void eitherSurvivorCanBeEffectiveOwnerOfExactlyTheOriginalPosition(AccountOwnership decedent) {
        AccountOwnership survivor = other(decedent);
        var originalAccount = new TraditionalIRA("Synthetic same label", decedent, ZERO);
        var original = AccountReference.capture(originalAccount);
        var existing = ira(survivor);
        var timing = death(decedent, DEATH.getYear());
        var assumption = assumption(original);

        var result = resolve(2037, timing, List.of(original, existing), assumption);
        var disposition = result.accounts().get(original);
        assertSame(original, disposition.originalAccount());
        assertEquals(decedent, disposition.originalAccount().owner());
        assertEquals(originalAccount.getType(), disposition.originalAccount().type());
        assertEquals(Year.of(2037), disposition.year());
        assertEquals(Optional.of(Year.of(2034)), result.deathView().firstDeathYear());
        assertEquals(Set.of(survivor), result.livingOwners());
        assertEquals(COUPLE.get(survivor), result.birthDates().get(survivor));
        assertEquals(Optional.of(survivor), disposition.effectiveRmdOwner());
        assertEquals(Optional.of(ELECTION), disposition.spouseAsOwnerEffectiveDate());
        assertEquals(ELIGIBLE_SUCCESSOR_OWNERSHIP, disposition.status());
        assertEquals(RetirementOwnershipDisposition.Treatment.MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1, disposition.treatment());
        assertTrue(disposition.refusalReason().isEmpty());
        assertEquals(SUPPORTED_ORIGINAL_OWNER, result.accounts().get(existing).status());
        assertEquals(List.of(original, existing), new ArrayList<>(result.accounts().keySet()));

        originalAccount.setCurrentBalance(ONE);
        var copiedReference = AccountReference.capture(originalAccount);
        assertSame(disposition, result.accounts().get(copiedReference));
        assertEquals(decedent, originalAccount.getOwnership());
        assertEquals(disposition.status(),
                resolve(2037, timing, List.of(copiedReference, existing), assumption).accounts().get(original).status());
    }

    @Test
    void plannedElectionDoesNotChangeOwnershipWhileBothLive() {
        var original = ira(PRIMARY);
        var result = resolve(2033, death(PRIMARY, 2034), List.of(original), assumption(original));
        var disposition = result.accounts().get(original);
        assertEquals(SUPPORTED_ORIGINAL_OWNER, disposition.status());
        assertEquals(Optional.of(PRIMARY), disposition.effectiveRmdOwner());
        assertTrue(disposition.spouseAsOwnerEffectiveDate().isEmpty());
    }

    @Test
    void survivalAndTraditionalIraClassDoNotEstablishAnElection() {
        var original = ira(PRIMARY);
        assertRefused(resolve(2035, death(PRIMARY, 2034), List.of(original)), original, MISSING_SPOUSE_AS_OWNER_ASSUMPTION);
    }

    @ParameterizedTest
    @EnumSource(Affirmation.class)
    void eachAffirmationIsIndependentlyRequired(Affirmation omitted) {
        var original = ira(PRIMARY);
        var facts = EnumSet.copyOf(AFFIRMED);
        facts.remove(omitted);
        var reason = switch (omitted) {
            case SOLE_DIRECT_SPOUSE_BENEFICIARY -> SOLE_DIRECT_SPOUSE_BENEFICIARY_UNVERIFIED;
            case UNLIMITED_WITHDRAWAL_RIGHTS -> UNLIMITED_WITHDRAWAL_RIGHTS_UNVERIFIED;
            case FULLY_PRETAX_AND_NO_RELEVANT_BASIS -> FULLY_PRETAX_AND_NO_RELEVANT_BASIS_UNVERIFIED;
            case COMPATIBLE_PERSONAL_IRA_HISTORY -> ACCOUNT_HISTORY_UNVERIFIED_OR_CONFLICTING;
            case NO_UNRESOLVED_MANDATORY_DISTRIBUTION -> MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED;
        };
        assertRefused(resolve(2035, death(PRIMARY, 2034), List.of(original),
                new SpouseAsOwnerAssumption(original, SPOUSE, SPOUSE_BIRTH, DEATH, ELECTION, facts)), original, reason);
    }

    @ParameterizedTest
    @MethodSource("unsupportedAccounts")
    void otherAccountClassesCannotGainSuccessorRmdOwnership(Account account) {
        var original = AccountReference.capture(account);
        assertRefused(resolve(2035, death(PRIMARY, 2034), List.of(original), assumption(original)),
                original, UNSUPPORTED_SUCCESSOR_ACCOUNT);
    }

    static Stream<Account> unsupportedAccounts() {
        var inherited = new InheritedAccountInformation(
                LocalDate.of(1930, 1, 1), LocalDate.of(2028, 1, 1), BeneficiaryRelationship.CHILD);
        return Stream.of(
                new RolloverIRA("Synthetic rollover", PRIMARY, ZERO),
                new InheritedTraditionalIRA("Synthetic inherited", PRIMARY, ZERO, inherited),
                new InheritedRothIRA("Synthetic inherited Roth", PRIMARY, ZERO, inherited),
                new Traditional401K("Synthetic employer", PRIMARY, ZERO),
                new Traditional403B("Synthetic employer 403(b)", PRIMARY, ZERO),
                new RothIRA("Synthetic Roth", PRIMARY, ZERO),
                new Roth401K("Synthetic employer Roth", PRIMARY, ZERO),
                new BrokerageAccount("Synthetic taxable", PRIMARY, ZERO));
    }

    @Test
    void deathAtOpeningQualifiesOnlyAtFollowingJanuary() {
        var original = ira(PRIMARY);
        var timing = death(PRIMARY, 2034);
        var assumption = assumption(original);
        var yearOfDeath = resolve(2034, DEATH, timing, COUPLE, List.of(original), List.of(assumption));
        assertRefused(yearOfDeath, original, BEFORE_EFFECTIVE_YEAR);
        var survivorYear = resolve(2035, DEATH, timing, COUPLE, List.of(original), List.of(assumption));
        assertEquals(Optional.of(SPOUSE), survivorYear.accounts().get(original).effectiveRmdOwner());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2033-01-01", "2034-01-01", "2035-01-02", "2036-01-01"})
    void onlyJanuaryOneFollowingDeathIsInScope(String effectiveDate) {
        var original = ira(PRIMARY);
        var assumption = new SpouseAsOwnerAssumption(
                original, SPOUSE, SPOUSE_BIRTH, DEATH, LocalDate.parse(effectiveDate), AFFIRMED);
        assertRefused(resolve(2037, death(PRIMARY, 2034), List.of(original), assumption), original, UNSUPPORTED_EFFECTIVE_DATE);
    }

    @Test
    void deathBeforeProjectionOpeningCannotBeAssumedEvenInOpeningYear() {
        var original = ira(PRIMARY);
        var result = resolve(2035, DEATH.plusDays(1), death(PRIMARY, 2034), COUPLE, List.of(original),
                List.of(assumption(original)));
        assertRefused(result, original, DEATH_BEFORE_PROJECTION_OPENING);
    }

    @Test
    void unresolvedOrContradictoryDeathChronologyFailsValidation() {
        var original = ira(PRIMARY);
        for (var timing : List.of(HouseholdLifetimeScenario.bothSurvive(), death(PRIMARY, 2035), death(SPOUSE, 2034))) {
            assertThrows(IllegalArgumentException.class,
                    () -> resolve(2035, timing, List.of(original), assumption(original)));
        }
        assertThrows(NullPointerException.class,
                () -> new SpouseAsOwnerAssumption(original, SPOUSE, SPOUSE_BIRTH, null, ELECTION, AFFIRMED));
    }

    @Test
    void missingSurvivorAndMultipleDeathsHaveNoSuccessorMapping() {
        var original = ira(PRIMARY);
        var single = resolve(2035, OPENING, death(PRIMARY, 2034), Map.of(PRIMARY, PRIMARY_BIRTH),
                List.of(original), List.of());
        assertRefused(single, original, NO_SOLE_SURVIVING_SPOUSE);
        var deaths = new HouseholdLifetimeScenario(Optional.of(Year.of(2034)), Optional.of(Year.of(2036)));
        assertRefused(resolve(2035, deaths, List.of(original), assumption(original)), original, MULTIPLE_MODELED_DEATHS);
        assertRefused(resolve(2036, deaths, List.of(original), assumption(original)), original, NO_SOLE_SURVIVING_SPOUSE);
        var simultaneous = new HouseholdLifetimeScenario(Optional.of(Year.of(2034)), Optional.of(Year.of(2034)));
        assertRefused(resolve(2034, simultaneous, List.of(original), assumption(original)), original, NO_SOLE_SURVIVING_SPOUSE);
    }

    @Test
    void everyAssumptionMustBindToTheFrozenAccountAndDifferentHouseholdSlot() {
        var original = ira(PRIMARY);
        var unrelatedSameLabel = ira(PRIMARY);
        var timing = death(PRIMARY, 2034);
        assertEquals(original.name(), unrelatedSameLabel.name());
        assertNotEquals(original, unrelatedSameLabel);
        assertThrows(IllegalArgumentException.class,
                () -> resolve(2035, timing, List.of(original), assumption(unrelatedSameLabel)));
        assertThrows(IllegalArgumentException.class, () -> resolve(2035, timing, List.of(original),
                new SpouseAsOwnerAssumption(original, SPOUSE, SPOUSE_BIRTH.minusYears(1), DEATH, ELECTION, AFFIRMED)));
        assertThrows(IllegalArgumentException.class,
                () -> new SpouseAsOwnerAssumption(original, PRIMARY, PRIMARY_BIRTH, DEATH, ELECTION, AFFIRMED));
        assertThrows(IllegalArgumentException.class,
                () -> new SpouseAsOwnerAssumption(original, JOINT, SPOUSE_BIRTH, DEATH, ELECTION, AFFIRMED));
    }

    @Test
    void duplicateAccountReferencesOrCompetingAssumptionsAreRejected() {
        var account = new TraditionalIRA("Synthetic account", PRIMARY, ZERO);
        var original = AccountReference.capture(account);
        var duplicate = AccountReference.capture(account);
        var timing = death(PRIMARY, 2034);
        assertThrows(IllegalArgumentException.class,
                () -> resolve(2035, timing, List.of(original, duplicate), assumption(original)));
        assertThrows(IllegalArgumentException.class,
                () -> resolve(2035, timing, List.of(original), assumption(original), assumption(duplicate)));
    }

    @Test
    void identicalLabelsAndOwnersDoNotShareElectionEvidence() {
        var original = ira(PRIMARY);
        var sameLabel = ira(PRIMARY);
        var result = resolve(2035, death(PRIMARY, 2034), List.of(original, sameLabel), assumption(original));

        assertEquals(List.of(original, sameLabel), new ArrayList<>(result.accounts().keySet()));
        assertEquals(Optional.of(SPOUSE), result.accounts().get(original).effectiveRmdOwner());
        assertRefused(result, sameLabel, MISSING_SPOUSE_AS_OWNER_ASSUMPTION);
    }

    @Test
    void contradictoryFrozenMetadataCannotRideOnAnEqualAccountReference() {
        var account = new TraditionalIRA("Synthetic account", PRIMARY, ZERO);
        var original = AccountReference.capture(account);
        var otherFacts = new RetirementParticipantFacts(
                RetirementParticipantFacts.SeparationStatus.SEPARATED, OPENING, null, null);
        var conflicting = AccountReference.restore(account, otherFacts);
        assertEquals(original, conflicting);
        assertThrows(IllegalArgumentException.class,
                () -> resolve(2035, death(PRIMARY, 2034), List.of(original), assumption(conflicting)));
        assertRefused(resolve(2035, death(PRIMARY, 2034), List.of(conflicting), assumption(conflicting)),
                conflicting, UNSUPPORTED_SUCCESSOR_ACCOUNT);
    }

    @Test
    void birthCohortAndFirstDistributionYearAreIndependentOfTheAffirmations() {
        var original = ira(PRIMARY);
        var births1959 = Map.of(PRIMARY, PRIMARY_BIRTH, SPOUSE, SPOUSE_BIRTH.withYear(1959));
        var unresolved = new SpouseAsOwnerAssumption(original, SPOUSE, births1959.get(SPOUSE), DEATH, ELECTION, AFFIRMED);
        assertRefused(resolve(2035, OPENING, death(PRIMARY, 2034), births1959, List.of(original), List.of(unresolved)),
                original, UNSUPPORTED_BIRTH_COHORT);

        var firstYearDeath = new SpouseAsOwnerAssumption(original, SPOUSE, SPOUSE_BIRTH, ELECTION,
                ELECTION.plusYears(1), AFFIRMED);
        assertRefused(resolve(2036, death(PRIMARY, 2035), List.of(original), firstYearDeath),
                original, DEATH_IN_OR_AFTER_FIRST_DISTRIBUTION_YEAR);
    }

    @Test
    void immutableInputsAndViewPreserveOriginalLineageWithNoBalanceFacts() {
        var original = ira(PRIMARY);
        var accounts = new ArrayList<>(List.of(original));
        var birthDates = new HashMap<>(COUPLE);
        var facts = EnumSet.copyOf(AFFIRMED);
        var assumption = new SpouseAsOwnerAssumption(original, SPOUSE, SPOUSE_BIRTH, DEATH, ELECTION, facts);
        facts.clear();
        var assumptions = new ArrayList<>(List.of(assumption));

        var view = resolve(2035, OPENING, death(PRIMARY, 2034), birthDates, accounts, assumptions);
        accounts.clear();
        birthDates.clear();
        assumptions.clear();
        assertEquals(AFFIRMED, assumption.affirmations());
        assertEquals(COUPLE, view.birthDates());
        assertEquals(Set.of(original), view.accounts().keySet());
        assertEquals(Optional.of(SPOUSE), view.accounts().get(original).effectiveRmdOwner());
        assertThrows(UnsupportedOperationException.class, () -> view.accounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.livingOwners().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.birthDates().clear());
        assertThrows(UnsupportedOperationException.class, () -> assumption.affirmations().clear());
    }

    private static AccountReference ira(AccountOwnership owner) {
        return AccountReference.capture(new TraditionalIRA("Synthetic same label", owner, ZERO));
    }

    private static AccountOwnership other(AccountOwnership owner) {
        return owner == PRIMARY ? SPOUSE : PRIMARY;
    }

    private static SpouseAsOwnerAssumption assumption(AccountReference original) {
        var survivor = other(original.owner());
        return new SpouseAsOwnerAssumption(original, survivor, COUPLE.get(survivor), DEATH, ELECTION, AFFIRMED);
    }

    private static HouseholdLifetimeScenario death(AccountOwnership owner, int year) {
        return new HouseholdLifetimeScenario(
                owner == PRIMARY ? Optional.of(Year.of(year)) : Optional.empty(),
                owner == SPOUSE ? Optional.of(Year.of(year)) : Optional.empty());
    }

    private static AnnualRetirementOwnership resolve(
            int year, HouseholdLifetimeScenario timing, List<AccountReference> accounts, SpouseAsOwnerAssumption... assumptions) {
        return resolve(year, OPENING, timing, COUPLE, accounts, List.of(assumptions));
    }

    private static AnnualRetirementOwnership resolve(
            int year, LocalDate opening, HouseholdLifetimeScenario timing, Map<AccountOwnership, LocalDate> birthDates,
            List<AccountReference> accounts, List<SpouseAsOwnerAssumption> assumptions) {
        var primary = new Person("Synthetic", "Primary", birthDates.get(PRIMARY));
        var spouse = birthDates.containsKey(SPOUSE) ? new Person("Synthetic", "Spouse", birthDates.get(SPOUSE)) : null;
        var deathView = EffectiveHouseholdDeathView.resolve(new Household(primary, spouse),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), Optional.of(timing));
        return RetirementOwnershipResolver.resolve(Year.of(year), opening, deathView, birthDates, rules, accounts, assumptions);
    }

    private static void assertRefused(AnnualRetirementOwnership view, AccountReference original, RefusalReason reason) {
        var disposition = view.accounts().get(original);
        assertSame(original, disposition.originalAccount());
        assertEquals(UNSUPPORTED_OR_UNRESOLVED, disposition.status());
        assertEquals(Optional.of(reason), disposition.refusalReason());
        assertEquals(RetirementOwnershipDisposition.Treatment.NONE, disposition.treatment());
        assertTrue(disposition.effectiveRmdOwner().isEmpty());
        assertTrue(disposition.spouseAsOwnerEffectiveDate().isEmpty());
    }
}
