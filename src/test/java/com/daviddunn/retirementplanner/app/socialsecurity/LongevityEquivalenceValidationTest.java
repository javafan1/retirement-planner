package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyCalculator.ScheduleKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityEquivalencePrefixCharacterizationTest.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyEquivalenceTest.*;

class LongevityEquivalenceValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"missing-policy", "invalid-range", "death-before-birth", "retirement-date", "survivor-date", "owner"})
    void validationKeyHasSameFailureAsUnchangedProvider(String failure) {
        var plan = youngPlan();
        var candidate = Stage4TestPlans.strategy(plan);
        var context = context(candidate, member(plan, "primary", 2055));
        int first = 2030;
        if (failure.equals("invalid-range")) first = 2055;
        if (failure.equals("missing-policy")) {
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                    a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null, null),
                    5, LocalDate.of(2030, 1, 1)));
            context = ProjectionEvaluationContext.withLifetimeScenario(context.householdLifetimeScenario().orElseThrow());
        }
        if (failure.equals("death-before-birth")) context = ProjectionEvaluationContext.withSocialSecurityStrategy(candidate,
                new HouseholdLifetimeScenario(Optional.of(Year.of(1960)), Optional.of(Year.of(2055))));
        if (failure.equals("retirement-date")) context = context(new SocialSecurityHouseholdClaimingStrategy(62,
                candidate.spouseRetirementAge(), candidate.primaryRetirementClaimDate(), candidate.spouseRetirementClaimDate(),
                candidate.primarySurvivorElection(), candidate.spouseSurvivorElection()), member(plan, "primary", 2055));
        if (failure.equals("survivor-date")) context = context(invalidSurvivor(plan, candidate), member(plan, "primary", 2055));
        if (failure.equals("owner")) plan.getHousehold().getSpouse().removeIncomeSource(
                plan.getHousehold().getSpouse().getIncomeSources().getFirst());
        var provider = new SocialSecurityProjectionIncomeProvider();
        final var input = context;
        final int start = first;
        var expected = assertThrows(RuntimeException.class, () -> provider.calculate(plan, start, 2054, input));
        var actual = assertThrows(RuntimeException.class, () -> provider.validatedKeyForEquivalence(plan, start, 2054, input));
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test
    void omittedInapplicableInvalidElectionDoesNotBecomeCarrierValidationSuccess() {
        var plan = youngPlan();
        var invalid = invalidSurvivor(plan, Stage4TestPlans.strategy(plan));
        var shortScenario = LongevityContinuationTest.mortality(plan, List.of(2031), List.of(2032)).scenarios().getFirst();
        var longScenario = LongevityContinuationTest.mortality(plan, List.of(2031), List.of(2055)).scenarios().getFirst();
        var provider = new SocialSecurityProjectionIncomeProvider();
        var key = provider.validatedKeyForEquivalence(plan, 2030, 2031, context(invalid, shortScenario));
        assertNull(key.spouseSurvivorEntitlement());
        assertDoesNotThrow(() -> provider.calculate(plan, 2030, 2031, context(invalid, shortScenario)));
        var expected = assertThrows(IllegalArgumentException.class,
                () -> provider.calculate(plan, 2030, 2054, context(invalid, longScenario)));
        var actual = assertThrows(IllegalArgumentException.class,
                () -> provider.validatedKeyForEquivalence(plan, 2030, 2054, context(invalid, longScenario)));
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "end", "death", "cola-scale", "election", "entitlement"})
    void incompatibleExactInputsRejectCoverage(String dimension) {
        var plan = youngPlan();
        var candidate = survivorAges(plan, Stage4TestPlans.strategy(plan), 60, 60);
        var provider = new SocialSecurityProjectionIncomeProvider();
        var member = provider.validatedKeyForEquivalence(plan, 2030, 2039, context(candidate, member(plan, "primary", 2040)));
        var carrier = provider.validatedKeyForEquivalence(plan, 2030, 2054, context(candidate, member(plan, "primary", 2055)));
        assertTrue(LongevityEquivalenceCoverage.covers(member, carrier));
        var changed = new ScheduleKey(dimension.equals("start") ? carrier.start().plusYears(1) : carrier.start(),
                dimension.equals("end") ? member.end().minusYears(1) : carrier.end(),
                dimension.equals("election") ? new SocialSecurityClaimingElection(carrier.primary().owner(),
                        carrier.primary().birthDate(), carrier.primary().fullRetirementMonthlyBenefit().add(java.math.BigDecimal.ONE),
                        carrier.primary().benefitValuationYear(), carrier.primary().retirementClaimDate()) : carrier.primary(),
                carrier.spouse(), carrier.primarySurvivorEntitlement(),
                dimension.equals("entitlement") ? carrier.spouseSurvivorEntitlement().plusMonths(1) : carrier.spouseSurvivorEntitlement(),
                dimension.equals("death") ? carrier.primaryDeath().plusYears(1) : carrier.primaryDeath(), carrier.spouseDeath(),
                dimension.equals("cola-scale") ? carrier.cola().setScale(carrier.cola().scale() + 1) : carrier.cola());
        assertFalse(LongevityEquivalenceCoverage.covers(member, changed));
    }

    private static SocialSecurityHouseholdClaimingStrategy invalidSurvivor(RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy own) {
        return new SocialSecurityHouseholdClaimingStrategy(own.primaryRetirementAge(), own.spouseRetirementAge(),
                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(59),
                        60, 0, "Invalid date"),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getSpouse().getBirthDate().plusYears(59),
                        60, 0, "Invalid date"));
    }
}
