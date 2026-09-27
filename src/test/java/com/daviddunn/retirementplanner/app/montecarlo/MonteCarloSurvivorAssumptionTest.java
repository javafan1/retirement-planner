package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityExecutionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloSurvivorAssumptionTest {
    static MonteCarloMortalityRequest requestWithAge(RetirementPlan plan, int age, int count) {
        return new MonteCarloMortalityRequest(plan, MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal("0.12")),
                LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), age);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sessionAgeReachesSurvivorCashFlowsInBothDirectionsWithoutPlanTransport(boolean primaryDies) {
        var plan = MonteCarloWorldGeneratorTest.plan();
        assertNull(plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        for (var person : List.of(primary, spouse)) {
            for (var income : List.copyOf(person.getIncomeSources())) person.removeIncomeSource(income);
            boolean isPrimary = person == primary;
            person.addIncomeSource(new SocialSecurityIncome("SS", isPrimary ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE,
                    person.getBirthDate().plusYears(67), null, new BigDecimal(isPrimary == primaryDies ? "5000" : "1000"),
                    67, BigDecimal.ZERO, 2027));
        }
        var before = JSON.valueToTree(plan);
        var world = world(0, primaryDies ? 2027 : 2034, primaryDies ? 2034 : 2027,
                ProjectionEconomicPath.constant(BigDecimal.ZERO));
        BigDecimal earlyIncome = null;
        for (int age : new int[]{60, 67}) {
            // Independent oracle: the existing exact-date strategy override, not the new shared-age context.
            var strategy = new SocialSecurityHouseholdClaimingStrategy(67, 67,
                    primary.getBirthDate().plusYears(67), spouse.getBirthDate().plusYears(67),
                    new SocialSecuritySurvivorClaimingCandidate(primary.getBirthDate().plusYears(age), age, 0, "Primary"),
                    new SocialSecuritySurvivorClaimingCandidate(spouse.getBirthDate().plusYears(age), age, 0, "Spouse"));
            var oracleContext = ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, world.lifetimeScenario())
                    .withExactEndingYear(2033);
            var oracle = new ProjectionEngine().projectWithOutcome(plan, oracleContext, world.economicPath());
            assertInstanceOf(ProjectionExecutionResult.Completed.class, oracle);
            var calls = new AtomicInteger();
            var engine = new ProjectionEngine() {
                @Override
                public ProjectionExecutionResult projectWithOutcome(RetirementPlan isolated, ProjectionEvaluationContext context,
                                                                    ProjectionEconomicPath path) {
                    calls.incrementAndGet();
                    assertEquals(before, JSON.valueToTree(isolated), "No plan mutation as hidden transport");
                    assertEquals(Optional.of(age), context.survivorClaimingAge());
                    assertTrue(context.socialSecurityStrategy().isEmpty(), "Own retirement elections remain plan-owned");
                    var actual = super.projectWithOutcome(isolated, context, path);
                    assertEquals(JSON.valueToTree(oracle.completedYears()), JSON.valueToTree(actual.completedYears()));
                    return actual;
                }
            };
            var result = new MonteCarloAnalyzer(engine).analyzeMortality(plan, requestWithAge(plan, age, 1), i -> world,
                    AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            assertEquals(1, calls.get());
            assertEquals(1, result.completedCount());
            var income = oracle.completedYears().getFirst().getSocialSecurityResult().householdBenefit();
            if (age == 60) {
                earlyIncome = income;
                assertTrue(income.signum() > 0);
            } else {
                assertTrue(earlyIncome.compareTo(income) > 0, "Age changes actual survivor Social Security cash flow");
            }
            assertEquals(before, JSON.valueToTree(plan));
            assertEquals(DeathScenario.BOTH_SURVIVE, plan.getPlanningAssumptions().getDeathScenarioAssumptions().getDeathScenario());
            assertNull(plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        }
    }

    @Test
    void survivorAgeCannotChangeIndexedMortalityWorldsOrAnyAnnualMarketReturn() {
        var plan = MonteCarloWorldGeneratorTest.plan();
        var early = new MonteCarloWorldGenerator(requestWithAge(plan, 60, 225));
        var late = new MonteCarloWorldGenerator(requestWithAge(plan, 67, 225));
        for (int index = 0; index < 225; index++) {
            var a = early.generate(index);
            var b = late.generate(index);
            assertEquals(a.scenarioIndex(), b.scenarioIndex());
            assertEquals(a.lifetimeScenario(), b.lifetimeScenario());
            assertEquals(a.economicPath().annualReturns(), b.economicPath().annualReturns());
        }
    }

    @Test
    void requestAndContextValidateAgeAndRetainItAcrossHorizonChanges() {
        var plan = plan();
        assertThrows(IllegalArgumentException.class, () -> requestWithAge(plan, 59, 1));
        assertThrows(IllegalArgumentException.class, () -> requestWithAge(plan, Integer.MAX_VALUE, 1));
        assertEquals(Optional.of(75), requestWithAge(plan, 75, 1).survivorClaimingAge());
        var frozen = requestWithAge(plan, 60, 1);
        configure(plan, plan.getPlanningAssumptions().getProjectionStartDate(), 30, 70);
        assertEquals(Optional.of(60), frozen.survivorClaimingAge());
        var lifetime = world(0, 2029, 2030, ProjectionEconomicPath.constant(BigDecimal.ZERO)).lifetimeScenario();
        var context = ProjectionEvaluationContext.withLifetimeScenario(lifetime).withSurvivorClaimingAge(60);
        assertEquals(Optional.of(60), context.withEndingYear(2050).survivorClaimingAge());
        assertEquals(Optional.of(60), context.withExactEndingYear(2030).survivorClaimingAge());
        assertNotEquals(context, context.withSurvivorClaimingAge(67));
        assertThrows(IllegalArgumentException.class, () -> context.withSurvivorClaimingAge(59));
        assertThrows(IllegalArgumentException.class, () -> ProjectionEvaluationContext.empty().withSurvivorClaimingAge(60));
    }

    @Test
    void explicitAnalysisAgeSupportsOpeningSecondDeathWithoutProjectionOrSavedElection() {
        var plan = MonteCarloWorldGeneratorTest.plan();
        var before = JSON.valueToTree(plan);
        var world = world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                throw new AssertionError("Opening estate must not run a projection");
            }
        };
        var result = new MonteCarloAnalyzer(engine).analyzeMortality(plan, requestWithAge(plan, 60, 1), i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, result.completedCount());
        assertTrue(result.annualResults().isEmpty());
        assertEquals(Optional.of(60), result.request().survivorClaimingAge());
        assertEquals(before, JSON.valueToTree(plan));
    }
}
