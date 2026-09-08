package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityContinuationTest.mortality;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.strategy;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyEquivalenceTest.*;

/** Characterizes the unmodified provider, before enabling prefix coverage. */
class LongevityEquivalencePrefixCharacterizationTest {
    @ParameterizedTest
    @CsvSource({"false,false,primary", "false,true,primary", "true,false,primary", "true,true,primary",
            "false,false,spouse", "false,true,spouse", "true,false,spouse", "true,true,spouse",
            "false,false,simultaneous", "false,true,simultaneous", "true,false,simultaneous", "true,true,simultaneous"})
    void completeAnnualPrefixesMatchEveryFieldAndScale(boolean young, boolean partial, String direction) {
        var plan = young ? youngPlan() : Stage4TestPlans.plan();
        if (partial) opening(plan, LocalDate.of(2030, 7, 1), 5);
        var carrier = member(plan, direction, 2055);
        var provider = new SocialSecurityProjectionIncomeProvider();
        for (int primaryOwn : List.of(62, 67, 70)) {
            for (int spouseOwn : List.of(62, 67, 70)) {
                for (int primarySurvivor : List.of(60, 67)) {
                    for (int spouseSurvivor : List.of(60, 67)) {
                        var candidate = survivorAges(plan, strategy(plan, primaryOwn, spouseOwn),
                                primarySurvivor, spouseSurvivor);
                        var longSchedule = provider.calculate(plan, 2030, 2054, context(candidate, carrier));
                        for (int secondDeath : List.of(2037, 2040, 2048)) {
                            var context = context(candidate, member(plan, direction, secondDeath));
                            provider.validateForContinuation(plan, 2030, secondDeath - 1, context);
                            assertTrue(LongevityEquivalenceCoverage.covers(
                                    provider.validatedKeyForEquivalence(plan, 2030, secondDeath - 1, context),
                                    provider.validatedKeyForEquivalence(plan, 2030, 2054, context(candidate, carrier))));
                            assertSchedule(prefix(longSchedule, secondDeath - 1),
                                    provider.calculate(plan, 2030, secondDeath - 1, context));
                        }
                    }
                }
            }
        }
    }

    @Test
    void earlyHorizonBothDeadSuffixCannotBeReplacedByLongCarrier() {
        var plan = youngPlan();
        opening(plan, LocalDate.of(2030, 1, 1), 25);
        var candidate = strategy(plan, 62, 62);
        var provider = new SocialSecurityProjectionIncomeProvider();
        var shortSchedule = provider.calculate(plan, 2030, 2054, context(candidate, member(plan, "primary", 2040)));
        var longSchedule = provider.calculate(plan, 2030, 2059, context(candidate, member(plan, "primary", 2060)));
        assertNotEquals(shortSchedule, prefix(longSchedule, 2054));
        assertNotEquals(HouseholdSocialSecurityResult.zero(), shortSchedule.get(2041));
        assertEquals(0, shortSchedule.get(2041).householdBenefit().signum());
        assertEquals(2, shortSchedule.get(2041).householdBenefit().scale());
    }

    @Test
    void partialOpeningRetainsFullCalendarSocialSecurityYear() {
        var plan = youngPlan();
        var candidate = strategy(plan, 62, 62);
        var scenario = member(plan, "primary", 2055);
        var provider = new SocialSecurityProjectionIncomeProvider();
        var january = provider.calculate(plan, 2032, 2054, context(candidate, scenario));
        opening(plan, LocalDate.of(2032, 7, 1), 5);
        assertSchedule(january, provider.calculate(plan, 2032, 2054, context(candidate, scenario)));
        assertTrue(january.get(2032).primaryOwnBenefit().signum() > 0);
    }

    static SocialSecurityJointMortalityScenario member(RetirementPlan plan, String direction, int secondDeath) {
        return mortality(plan, List.of(direction.equals("primary") ? 2033 : secondDeath),
                List.of(direction.equals("spouse") ? 2033 : secondDeath)).scenarios().getFirst();
    }

    static ProjectionEvaluationContext context(SocialSecurityHouseholdClaimingStrategy candidate,
            SocialSecurityJointMortalityScenario scenario) {
        return ProjectionEvaluationContext.withSocialSecurityStrategy(candidate, new HouseholdLifetimeScenarioMapper().map(scenario));
    }

    static Map<Integer, HouseholdSocialSecurityResult> prefix(Map<Integer, HouseholdSocialSecurityResult> schedule, int last) {
        Map<Integer, HouseholdSocialSecurityResult> result = new LinkedHashMap<>();
        schedule.forEach((year, row) -> { if (year <= last) result.put(year, row); });
        return Map.copyOf(result);
    }

    static void assertSchedule(Map<Integer, HouseholdSocialSecurityResult> expected,
            Map<Integer, HouseholdSocialSecurityResult> actual) {
        assertEquals(expected, actual); // Record equality compares every BigDecimal with equals, including scale.
        expected.forEach((year, row) -> {
            assertEquals(row.primarySelectedBenefit(), actual.get(year).primarySelectedBenefit());
            assertEquals(row.spouseSelectedBenefit(), actual.get(year).spouseSelectedBenefit());
        });
    }

    static void opening(RetirementPlan plan, LocalDate start, int length) {
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), length, start));
    }
}
