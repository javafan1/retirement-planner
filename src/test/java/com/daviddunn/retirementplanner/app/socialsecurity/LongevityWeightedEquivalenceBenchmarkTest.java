package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;

class LongevityWeightedEquivalenceBenchmarkTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cachedAuthoritativeSchedulesMatchUncachedAcrossOwnAndSurvivorGrid(boolean young) {
        var plan = young ? LongevityWeightedStrategyEquivalenceTest.youngPlan() : Stage4TestPlans.plan();
        var provider = new SocialSecurityProjectionIncomeProvider();
        var universe = universe(plan).stream().filter(s -> List.of(62, 67, 70).contains(s.primaryRetirementAge())
                && List.of(62, 67, 70).contains(s.spouseRetirementAge())).toList();
        long compared = 0;
        long calculations = 0;
        for (int[] ages : List.of(new int[]{63, 72}, new int[]{74, 61}, new int[]{74, 72}, new int[]{85, 85})) {
            var scenario = LongevityWeightedStrategyEquivalenceTest.mortality(plan, ages[0], ages[1]).scenarios().getFirst();
            var lifetime = new HouseholdLifetimeScenarioMapper().map(scenario);
            int last = Math.max(2034, Math.max(scenario.primaryDeathDate().getYear(), scenario.spouseDeathDate().getYear()) - 1);
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache = new HashMap<>();
            for (var strategy : universe) {
                var context = ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime);
                assertEquals(provider.calculate(plan, 2030, last, context),
                        provider.calculateForEquivalence(plan, 2030, last, context, cache));
                compared++;
            }
            calculations += cache.size();
        }
        assertTrue(calculations < compared);
        System.out.println("Stage 5C1 cached/uncached schedule proof young=" + young + " schedules=" + compared
                + " actualSSCalculations=" + calculations);
    }

    @Test
    void representativeSubsetOfTwentyFourCandidatesUsesThreeFinancialEvaluations() throws Exception {
        var plan = Stage4TestPlans.plan();
        var original = Stage4TestPlans.json(plan);
        List<SocialSecurityHouseholdClaimingStrategy> candidates = new ArrayList<>();
        for (int own : List.of(62, 67, 70)) {
            for (int survivor = 60; survivor <= 67; survivor++) {
                candidates.add(LongevityWeightedStrategyEquivalenceTest.survivorAges(plan,
                        strategy(plan, own, own), survivor, survivor));
            }
        }
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, productionScenarios(plan),
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.empty(),
                new LongevityWeightedDetailRetentionPolicy(false, Set.of(8)),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareWithEquivalenceOnly(request);
        assertEquals(24, result.completedStrategyCount());
        assertEquals(3, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertEquals(3, result.work().stageFourEvaluations());
        assertEquals(21, result.evaluationsAvoided());
        assertEquals(7800, result.work().projectionEngineRuns());
        assertEquals(2600, result.retainedDetailedScenarioOutcomeCount());
        assertEquals(original, Stage4TestPlans.json(plan));
        System.out.println("Stage 5C1 24-candidate benchmark: groups=3 avoided=21 engineRuns=7800 retainedOutcomes=2600"
                + " planningMillis=" + result.equivalencePlan().orElseThrow().elapsedTime().toMillis()
                + " financialMillis=" + result.elapsedTime().minus(result.equivalencePlan().orElseThrow().elapsedTime()).toMillis()
                + " totalMillis=" + result.elapsedTime().toMillis());
    }

    /** Opt-in long measurement; normal full-suite execution still runs the smaller regression benchmark. */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "stage5c1.fullPlanningBenchmark", matches = "true")
    void completeUniversePlanningOnly() {
        var plan = Stage4TestPlans.plan();
        var candidates = universe(plan);
        var scenarios = productionScenarios(plan);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), p -> {
                    if (p.completedWork() % 250 == 0) System.out.println("Full proof mortality outcomes "
                            + p.completedWork() + "/" + p.totalWork());
                }, AnalysisCancellationToken.none());
        var result = new LongevityWeightedStrategyEquivalencePlanner().plan(request);
        assertEquals(5184, result.inputStrategyCount());
        assertEquals(81, result.equivalenceGroupCount());
        assertEquals(5103, result.potentialEvaluationsAvoided());
        System.out.println("Stage 5C1 full planning ONLY: inputs=" + result.inputStrategyCount()
                + " groups=" + result.equivalenceGroupCount() + " avoided=" + result.potentialEvaluationsAvoided()
                + " mortalityScenarios=" + scenarios.scenarios().size() + " planningMillis=" + result.elapsedTime().toMillis()
                + " scheduleRequests=" + result.scheduleRequests() + " actualSSCalculations=" + result.scheduleCalculations());
    }

    static List<SocialSecurityHouseholdClaimingStrategy> universe(RetirementPlan plan) {
        var grid = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        List<SocialSecurityHouseholdClaimingStrategy> result = new ArrayList<>();
        for (int primary : grid.primaryRetirementAges()) {
            for (int spouse : grid.spouseRetirementAges()) {
                var own = strategy(plan, primary, spouse);
                for (var primarySurvivor : grid.primarySurvivorCandidates()) {
                    for (var spouseSurvivor : grid.spouseSurvivorCandidates()) {
                        result.add(new SocialSecurityHouseholdClaimingStrategy(primary, spouse,
                                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(), primarySurvivor, spouseSurvivor));
                    }
                }
            }
        }
        return result;
    }

    static HouseholdLongevityScenarios productionScenarios(RetirementPlan plan) {
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), LocalDate.of(2030, 1, 1), table.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        return new HouseholdLongevityScenarioFactory(table).create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), assumptions);
    }
}
