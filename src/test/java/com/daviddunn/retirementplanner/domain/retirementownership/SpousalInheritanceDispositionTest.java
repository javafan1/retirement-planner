package com.daviddunn.retirementplanner.domain.retirementownership;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.AccountReference;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver.Affirmation;
import com.daviddunn.retirementplanner.domain.retirementownership.RetirementOwnershipResolver.SpouseAsOwnerAssumption;
import com.daviddunn.retirementplanner.domain.rules.RmdRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.retirementownership.SpousalInheritanceDisposition.Basis.*;
import static com.daviddunn.retirementplanner.domain.retirementownership.SpousalInheritanceDisposition.Reason.*;
import static com.daviddunn.retirementplanner.domain.retirementownership.SpousalInheritanceDisposition.Treatment.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class SpousalInheritanceDispositionTest {

    private static final LocalDate OPENING = LocalDate.of(2033, 1, 1);
    private static final LocalDate DEATH = LocalDate.of(2034, 1, 1);
    private static final LocalDate EFFECTIVE = LocalDate.of(2035, 1, 1);
    private static final Map<AccountOwnership, LocalDate> BIRTHS = Map.of(
            PRIMARY, LocalDate.of(1960, 6, 15), SPOUSE, LocalDate.of(1962, 6, 15));
    private static RmdRules rules;

    @BeforeAll
    static void loadRules() throws IOException {
        rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json").getRmdRules();
    }

    @ParameterizedTest
    @CsvSource({
            "PRIMARY_DIES,SPOUSE_AS_OWNER,OWN_IRA_ELECTION,EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER",
            "SPOUSE_DIES,SPOUSE_AS_OWNER,OWN_IRA_ELECTION,EMPLOYER_PLAN_TO_OWN_IRA_ROLLOVER",
            "PRIMARY_DIES,SPOUSE_AS_BENEFICIARY,IRA_BENEFICIARY,EMPLOYER_PLAN_BENEFICIARY",
            "SPOUSE_DIES,SPOUSE_AS_BENEFICIARY,IRA_BENEFICIARY,EMPLOYER_PLAN_BENEFICIARY"})
    void distinctSourceTreatmentsAreProposalsForEitherSurvivingSpouse(DeathScenario scenario,
            SpousalInheritanceAssumption.Treatment selection, SpousalInheritanceDisposition.Treatment iraTreatment,
            SpousalInheritanceDisposition.Treatment employerTreatment) {
        var original = scenario == DeathScenario.PRIMARY_DIES ? PRIMARY : SPOUSE;
        var survivor = original == PRIMARY ? SPOUSE : PRIMARY;
        var ira = AccountReference.capture(new TraditionalIRA("Synthetic original", original, ZERO));
        var employer = AccountReference.capture(new Traditional401K("Synthetic employer", original, ZERO));
        var own = AccountReference.capture(new TraditionalIRA("Synthetic survivor", survivor, ZERO));
        var portfolio = resolve(2035, OPENING, scenario, List.of(ira, employer, own));
        var proposed = SpousalInheritanceDisposition.classify(portfolio, new SpousalInheritanceAssumption(selection));

        assertEquals(iraTreatment, proposed.get(ira).proposedTreatment());
        assertEquals(employerTreatment, proposed.get(employer).proposedTreatment());
        for (var account : List.of(ira, employer)) {
            var result = proposed.get(account);
            assertSame(account, result.originalAccount());
            assertEquals(original, result.originalAccount().owner());
            assertEquals(Optional.of(survivor), result.modeledSurvivor());
            assertEquals(selection, result.assumption().treatment());
            assertEquals(NOT_VERIFIABLE_FROM_EXISTING_INPUT, result.basis());
            assertTrue(result.unresolvedReason().isPresent());
            assertTrue(result.modeledTreatmentDate().isEmpty());
            assertTrue(portfolio.accounts().get(account).effectiveRmdOwner().isEmpty());
        }
        assertEquals(ORIGINAL_OWNER, proposed.get(own).proposedTreatment());
        assertEquals(ACCOUNT_FACT_VERIFIED_FROM_INPUT, proposed.get(own).basis());
        assertEquals(Optional.of(survivor), portfolio.accounts().get(own).effectiveRmdOwner());
        assertThrows(UnsupportedOperationException.class, proposed::clear);
    }

    @ParameterizedTest
    @EnumSource(SpousalInheritanceAssumption.Treatment.class)
    void deferredAccountClassesRemainOutsideTheProposal(SpousalInheritanceAssumption.Treatment selection) {
        var inherited = new InheritedAccountInformation(
                LocalDate.of(1940, 1, 1), LocalDate.of(2028, 1, 1), BeneficiaryRelationship.CHILD);
        List<AccountReference> references = List.of(
                new Traditional403B("Synthetic 403(b)", PRIMARY, ZERO),
                new RothIRA("Synthetic Roth", PRIMARY, ZERO),
                new Roth401K("Synthetic Roth plan", PRIMARY, ZERO),
                new RolloverIRA("Synthetic rollover", PRIMARY, ZERO),
                new InheritedTraditionalIRA("Synthetic inherited", PRIMARY, ZERO, inherited),
                new InheritedRothIRA("Synthetic inherited Roth", PRIMARY, ZERO, inherited),
                new BrokerageAccount("Synthetic brokerage", PRIMARY, ZERO)).stream().map(AccountReference::capture).toList();
        var classified = SpousalInheritanceDisposition.classify(resolve(2035, OPENING, DeathScenario.PRIMARY_DIES, references),
                new SpousalInheritanceAssumption(selection));
        classified.values().forEach(result -> {
            assertEquals(UNSUPPORTED_SCENARIO, result.basis());
            assertEquals(Optional.of(UNSUPPORTED_ACCOUNT_TYPE), result.unresolvedReason());
            assertTrue(result.modeledSurvivor().isEmpty());
        });
    }

    @Test
    void separatelyAffirmedIraFactsAreStillProspectiveAndNeverBecomeBeneficiaryEvidence() {
        var ira = AccountReference.capture(new TraditionalIRA("Synthetic IRA", PRIMARY, ZERO));
        var facts = new SpouseAsOwnerAssumption(ira, SPOUSE, BIRTHS.get(SPOUSE), DEATH, EFFECTIVE,
                EnumSet.allOf(Affirmation.class));
        var ownership = resolve(2035, OPENING, DeathScenario.PRIMARY_DIES, List.of(ira), facts);
        var owner = SpousalInheritanceDisposition.classify(ownership,
                new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_OWNER)).get(ira);
        var beneficiary = SpousalInheritanceDisposition.classify(ownership,
                new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY)).get(ira);
        assertEquals(SUPPORTED_MODELING_ASSUMPTION, owner.basis());
        assertEquals(Optional.of(EFFECTIVE), owner.modeledTreatmentDate());
        assertEquals(NOT_VERIFIABLE_FROM_EXISTING_INPUT, beneficiary.basis());
        assertEquals(Optional.of(IRA_BENEFICIARY_FACTS_UNVERIFIED), beneficiary.unresolvedReason());
    }

    @Test
    void deathYearAndPreOpeningDeathRequireSeparateEvidence() {
        var ira = AccountReference.capture(new TraditionalIRA("Synthetic IRA", PRIMARY, ZERO));
        var assumption = new SpousalInheritanceAssumption(SpousalInheritanceAssumption.Treatment.SPOUSE_AS_BENEFICIARY);
        var preDeath = SpousalInheritanceDisposition.classify(
                resolve(2033, OPENING, DeathScenario.PRIMARY_DIES, List.of(ira)), assumption).get(ira);
        assertEquals(ORIGINAL_OWNER, preDeath.proposedTreatment());
        var deathYear = SpousalInheritanceDisposition.classify(
                resolve(2034, OPENING, DeathScenario.PRIMARY_DIES, List.of(ira)), assumption).get(ira);
        assertEquals(Optional.of(DEATH_YEAR_OBLIGATION_REQUIRES_SEPARATE_TREATMENT), deathYear.unresolvedReason());
        var preOpening = SpousalInheritanceDisposition.classify(
                resolve(2035, DEATH.plusDays(1), DeathScenario.PRIMARY_DIES, List.of(ira)), assumption).get(ira);
        assertEquals(Optional.of(DEATH_BEFORE_PROJECTION_OPENING), preOpening.unresolvedReason());
        assertTrue(preOpening.modeledTreatmentDate().isEmpty());
    }

    private static AnnualRetirementOwnership resolve(int year, LocalDate opening, DeathScenario scenario,
            List<AccountReference> accounts, SpouseAsOwnerAssumption... evidence) {
        var household = new Household(new Person("Synthetic", "Primary", BIRTHS.get(PRIMARY)),
                new Person("Synthetic", "Spouse", BIRTHS.get(SPOUSE)));
        var deaths = EffectiveHouseholdDeathView.resolve(household, new DeathScenarioAssumptions(scenario, DEATH.getYear()),
                Optional.empty());
        return RetirementOwnershipResolver.resolve(Year.of(year), opening, deaths, BIRTHS, rules, accounts, List.of(evidence));
    }
}
