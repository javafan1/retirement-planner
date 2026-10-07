package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.PersonMortalityCategories;
import com.daviddunn.retirementplanner.testing.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SinglePersonHouseholdTest {
    private static Person primary() {
        return new Person("Primary", "Example", LocalDate.of(1960, 1, 1));
    }

    @Test
    void rolesAndPresenceAreExplicitAndCollectionsCannotAddAnotherSpouse() {
        var primary = primary();
        var single = new Household(primary);
        assertSame(primary, single.getPrimaryPerson());
        assertFalse(single.hasSpouse());
        assertTrue(single.spouse().isEmpty());
        assertEquals(java.util.List.of(primary), single.members());
        assertThrows(UnsupportedOperationException.class, () -> single.members().add(primary()));
        var spouse = primary();
        var couple = new Household(primary, spouse);
        assertTrue(couple.hasSpouse());
        assertSame(spouse, couple.spouse().orElseThrow());
        assertSame(spouse, couple.getSpouse());
        assertEquals(java.util.List.of(primary, spouse), couple.members());
        assertThrows(NullPointerException.class, () -> new Household(null));
        assertThrows(NullPointerException.class, () -> new Household(null, spouse));
        assertThrows(IllegalArgumentException.class, () -> new Household(primary, primary));
    }

    @Test
    void legacyRequiredSpouseAccessFailsIntentionally() {
        var failure = assertThrows(UnsupportedOperationException.class,
                () -> new Household(primary()).getSpouse());
        assertTrue(failure.getMessage().contains("Single-person household not yet supported"));
    }

    @Test
    void absenceAliveAndDeathAreDifferentStates() {
        var persisted = new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null);
        var single = EffectiveHouseholdDeathView.resolve(new Household(primary()), persisted, Optional.empty());
        assertEquals(EffectiveHouseholdDeathView.PersonState.ABSENT, single.state(AccountOwnership.SPOUSE, 2030));
        assertFalse(single.isAlive(AccountOwnership.SPOUSE, 2030));
        assertEquals(EffectiveHouseholdDeathView.PersonState.ALIVE, single.state(AccountOwnership.PRIMARY, 2030));
        assertThrows(IllegalArgumentException.class, () -> single.deathDate(AccountOwnership.SPOUSE));
        var household = new Household(primary(), primary());
        var couple = EffectiveHouseholdDeathView.resolve(household, persisted, Optional.of(
                new HouseholdLifetimeScenario(Optional.empty(), Optional.of(Year.of(2031)))));
        assertEquals(EffectiveHouseholdDeathView.PersonState.ALIVE, couple.state(AccountOwnership.SPOUSE, 2030));
        assertEquals(EffectiveHouseholdDeathView.PersonState.DECEASED, couple.state(AccountOwnership.SPOUSE, 2031));
        assertEquals(Optional.of(LocalDate.of(2031, 1, 1)), couple.deathDate(AccountOwnership.SPOUSE));
        assertThrows(IllegalArgumentException.class, () -> single.state(AccountOwnership.JOINT, 2030));
    }

    @Test
    void absentSpouseCannotCarryADeathAndSingleHouseholdEndsAtPrimaryDeath() {
        var single = new Household(primary());
        var persisted = new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null);
        assertThrows(IllegalArgumentException.class, () -> EffectiveHouseholdDeathView.resolve(single, persisted,
                Optional.of(new HouseholdLifetimeScenario(Optional.empty(), Optional.of(Year.of(2031))))));
        var view = EffectiveHouseholdDeathView.resolve(single, persisted,
                Optional.of(new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.empty())));
        assertFalse(view.isHouseholdDeceased(2030));
        assertTrue(view.isHouseholdDeceased(2031));
        assertFalse(view.isAlive(AccountOwnership.JOINT, 2031));
        assertEquals(EffectiveHouseholdDeathView.PersonState.ABSENT, view.state(AccountOwnership.SPOUSE, 2032));
        assertThrows(UnsupportedOperationException.class, () -> view.areBothDeceased(2031));
    }

    @Test
    void rejectsSpouseOwnedPortfolioAndPersonRecordsWithoutReassignment() {
        var account = AccountFactory.create(AccountType.TRADITIONAL_IRA, "Spouse IRA",
                AccountOwnership.SPOUSE, BigDecimal.TEN);
        var portfolio = new AccountPortfolio(java.util.List.of(account));
        assertThrows(IllegalArgumentException.class, () -> new RetirementPlan(new Household(primary()),
                portfolio, TestDataFactory.planningAssumptions()));
        assertEquals(AccountOwnership.SPOUSE, account.getOwnership());
        var person = primary();
        person.addAccount(account);
        assertThrows(IllegalArgumentException.class, () -> new Household(person));
        var incomeOwner = primary();
        incomeOwner.addIncomeSource(new Pension("Invalid pension", AccountOwnership.SPOUSE,
                LocalDate.of(2026, 1, 1), null, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Household(incomeOwner));
    }

    @Test
    void mutableInvalidReferencesAreDetectedAtValidationBoundary() {
        var plan = new RetirementPlan(new Household(primary()), new AccountPortfolio(), TestDataFactory.planningAssumptions());
        plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Invalid",
                AccountOwnership.SPOUSE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, plan::validateHouseholdReferences);
    }

    @Test
    void downstreamMortalityAndSurvivorAnalysisRejectBeforeComputation() {
        var household = new Household(primary());
        var plan = new RetirementPlan(household, new AccountPortfolio(), TestDataFactory.planningAssumptions());
        var mortality = assertThrows(UnsupportedOperationException.class, () -> PersonMortalityCategories.from(household));
        assertTrue(mortality.getMessage().contains("PersonMortalityCategories"));
        var socialSecurity = assertThrows(UnsupportedOperationException.class,
                () -> new SocialSecurityProjectionIncomeProvider().calculate(plan, 2026, 2030,
                        ProjectionEvaluationContext.withLifetimeScenario(HouseholdLifetimeScenario.bothSurvive()).withSurvivorClaimingAge(67)));
        assertTrue(socialSecurity.getMessage().contains("survivor"));
    }

    @Test
    void absentSpouseInputsRemainInvalidButIndividualMortalityNeedsOnlyPrimaryCategory() {
        var plan = new RetirementPlan(new Household(primary()), new AccountPortfolio(), TestDataFactory.planningAssumptions());
        var engine = new ProjectionEngine();
        var context = ProjectionEvaluationContext.withLifetimeScenario(HouseholdLifetimeScenario.bothSurvive()).withSurvivorClaimingAge(67);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> engine.project(plan, context))
                .getMessage().contains("survivor"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> engine.projectWithOutcome(plan, context,
                        ProjectionEconomicPath.constant(BigDecimal.ZERO)))
                .getMessage().contains("survivor"));
        var settings = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings(
                1, 417, BigDecimal.ZERO, BigDecimal.ZERO);
        var session = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings
                .defaults(LocalDate.of(2026, 1, 1));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityRequest(plan, settings, session))
                .getMessage().contains("Primary mortality category"));
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        var request = new com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityRequest(plan, settings, session);
        assertFalse(request.hasSpouse());
        assertTrue(request.survivorClaimingAge().isEmpty());
    }

    @Test
    void presentButIncompleteSpouseStillFailsMortalityValidation() {
        var primary = primary();
        primary.setMortalityCategory(MortalityCategory.MALE);
        var household = new Household(primary, new Person());
        assertTrue(household.hasSpouse());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PersonMortalityCategories.from(household))
                .getMessage().contains("Mortality category is required for Spouse"));
    }
}
