package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LongevityContinuationTest {
    static HouseholdLongevityScenarios mortality(RetirementPlan plan, List<Integer> primaryYears, List<Integer> spouseYears) {
        int p = plan.getHousehold().getPrimaryPerson().getBirthDate().getYear();
        int s = plan.getHousehold().getSpouse().getBirthDate().getYear();
        return PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                primaryYears.stream().map(y -> new SocialSecurityMortalityProbability(y - p,
                        BigDecimal.ONE.divide(BigDecimal.valueOf(primaryYears.size())))).toList(),
                spouseYears.stream().map(y -> new SocialSecurityMortalityProbability(y - s,
                        BigDecimal.ONE.divide(BigDecimal.valueOf(spouseYears.size())))).toList());
    }

    static LongevityWeightedIntegratedStrategyRequest request(RetirementPlan p, HouseholdLongevityScenarios s) {
        return new LongevityWeightedIntegratedStrategyRequest(p, Stage4TestPlans.strategy(p), s,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"));
    }

    static void financialEquals(LongevityWeightedIntegratedStrategyResult exact,
            LongevityWeightedIntegratedStrategyResult optimized) throws Exception {
        for (var component : LongevityWeightedIntegratedStrategyResult.class.getRecordComponents()) {
            if (!component.getName().equals("actualProjectionRunCount")) {
                assertEquals(component.getAccessor().invoke(exact), component.getAccessor().invoke(optimized), component.getName());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "spouse", "simultaneous", "early", "partial", "fixed", "bracket"})
    void exactOutcomesIdentityProbabilityAndIsolation(String fixture) throws Exception {
        var p = Stage4TestPlans.plan();
        if (fixture.equals("partial")) {
            var assumptions = p.getPlanningAssumptions();
            p.setPlanningAssumptions(new PlanningAssumptions(assumptions.getEconomicAssumptions(),
                    assumptions.getTaxAssumptions(), assumptions.getWithdrawalAssumptions(),
                    assumptions.getDeathScenarioAssumptions(), 5, LocalDate.of(2030, 7, 1)));
        }
        if (fixture.equals("fixed") || fixture.equals("bracket")) {
            p.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("100000"),
                    fixture.equals("fixed") ? RothConversionStopRule.FIRST_HOUSEHOLD_RMD : RothConversionStopRule.NEVER,
                    fixture.equals("fixed") ? RothConversionStrategy.FIXED_AMOUNT : RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                    RothConversionFrequency.ANNUAL));
        }
        var s = fixture.equals("spouse") ? mortality(p, List.of(2040,2050), List.of(2031))
                : fixture.equals("simultaneous") ? mortality(p, List.of(2040,2050), List.of(2040,2050))
                : fixture.equals("early") ? mortality(p, List.of(2030,2031), List.of(2030,2034))
                : mortality(p, List.of(2031), List.of(2040,2050));
        var req = request(p, s);
        var before = Stage4TestPlans.json(p);
        var deterministic = Stage4TestPlans.json(new ProjectionEngine().project(p));
        var exact = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req);
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        var optimized = new LongevityWeightedContinuationEvaluator().evaluate(req, work::set);
        financialEquals(exact, optimized);
        assertEquals(s.scenarios().size(), work.get().outcomesProduced());
        assertEquals(work.get().projectionStarts(), optimized.actualProjectionRunCount());
        assertEquals(exact.actualProjectionRunCount() - optimized.actualProjectionRunCount(), work.get().evaluationsAvoided());
        assertEquals(before, Stage4TestPlans.json(p));
        assertEquals(deterministic, Stage4TestPlans.json(new ProjectionEngine().project(p)));
        assertEquals(exact, new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req));
        assertEquals(optimized, new LongevityWeightedContinuationEvaluator().evaluate(req));
    }

    @Test
    void configuredPostDeathFailureCannotBeHiddenByAnAvailableEstate() {
        var p = Stage4TestPlans.plan();
        p.getHousehold().addExpense(new Expense("Later one-time cost", new BigDecimal("100000000"),
                GrowthCategory.GENERAL, LocalDate.of(2034,1,1), LocalDate.of(2034,12,31), ExpenseType.ONE_TIME));
        var req = request(p, mortality(p, List.of(2031), List.of(2031)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        sameFailure(req, work);
        assertEquals(1, work.get().independentEarlyHorizonRuns());
        assertEquals(0, work.get().carrierAttempts());
        assertEquals(0, work.get().outcomesProduced());
    }

    @Test
    void failedLongestDoesNotFailShorterMemberAndOriginalFirstFailureIsPreserved() {
        var p = LongevityWeightedStrategyEquivalenceTest.youngPlan();
        var req = request(p, mortality(p, List.of(2031), List.of(2040,2081)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        sameFailure(req, work);
        assertEquals(1, work.get().failedCarriers());
        assertEquals(2, work.get().fallbackIndependentRuns());
        assertEquals(3, work.get().projectionStarts());
        assertEquals(1, work.get().outcomesProduced());
        assertEquals(1, work.get().completedProjections());
        assertEquals(-1, work.get().evaluationsAvoided(), "Failed speculation costs one extra run");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void livingRmdAgeBoundaryAndZeroBalanceBypass(boolean zero) throws Exception {
        var p = Stage4TestPlans.plan();
        var req = request(p, mortality(p, List.of(2031), List.of(2080,2082)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        var rules = new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository()
                .load("/rules/government-rules-2026.json");
        // Valid mortality stops at age 120. Inject the real unsupported-age calculation into the
        // speculative attempt to prove the fallback policy also handles this exception, without
        // inventing age-122 mortality or extending any actual carrier beyond mortality support.
        var engine = new ProjectionEngine() {
            boolean first = true;
            @Override public Projection project(RetirementPlan plan, ProjectionEvaluationContext context) {
                if (first) {
                    first = false;
                    new com.daviddunn.retirementplanner.domain.rmd.RmdCalculator()
                            .calculateRmd(zero ? BigDecimal.ZERO : BigDecimal.ONE, 121, rules);
                }
                return super.project(plan, context);
            }
        };
        var session = new LongevityContinuationSession(p, req, 2, work::set, engine);
        var exact = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req);
        for (int i = 0; i < req.longevityScenarios().scenarios().size(); i++) {
            session.scenarioStarted();
            assertEquals(exact.scenarioOutcomes().get(i).estateSnapshot(), session.snapshot(req.longevityScenarios().scenarios().get(i)));
            session.scenarioCompleted();
        }
        assertEquals(zero ? 0 : 1, work.get().failedCarriers());
        assertEquals(zero ? 0 : 2, work.get().fallbackIndependentRuns());
    }

    @Test
    void originalOpeningRmdValidationIsPreserved() {
        var p = Stage4TestPlans.plan();
        p.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2030,
                        new BigDecimal("500000"), BigDecimal.ONE));
        sameFailure(request(p, mortality(p, List.of(2030), List.of(2040,2050))),
                new AtomicReference<>(LongevityContinuationWork.zero()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "carrier", "assignment", "fallback", "final"})
    void cancellationNeverReturnsCompletedValue(String boundary) {
        var p = boundary.equals("fallback") ? LongevityWeightedStrategyEquivalenceTest.youngPlan() : Stage4TestPlans.plan();
        var s = mortality(p, List.of(2031), boundary.equals("fallback") ? List.of(2040,2081) : List.of(2040,2050));
        var cancel = new AtomicBoolean(boundary.equals("before"));
        List<Integer> progress = new ArrayList<>();
        var req = new LongevityWeightedIntegratedStrategyRequest(p, Stage4TestPlans.strategy(p), s,
                LocalDate.of(2029,7,1), new BigDecimal("0.03"), value -> {
                    progress.add(value.completedWork());
                    if (boundary.equals("assignment") && value.completedWork() == 1
                            || boundary.equals("final") && value.completedWork() == value.totalWork()) cancel.set(true);
                }, cancel::get);
        assertThrows(AnalysisCancelledException.class, () -> new LongevityWeightedContinuationEvaluator().evaluate(req, work -> {
            if (boundary.equals("carrier") && work.completedProjections() > 0
                    || boundary.equals("fallback") && work.fallbackIndependentRuns() > 0) cancel.set(true);
        }));
        assertEquals(progress.stream().sorted().toList(), progress);
    }

    @Test
    void invalidSurvivorElectionUsesOriginalFiniteHorizonOmissionAndFailure() throws Exception {
        var p = LongevityWeightedStrategyEquivalenceTest.youngPlan();
        var own = Stage4TestPlans.strategy(p);
        var invalid = new SocialSecurityHouseholdClaimingStrategy(own.primaryRetirementAge(), own.spouseRetirementAge(),
                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(p.getHousehold().getPrimaryPerson().getBirthDate().plusYears(59),60,0,"Invalid date"),
                new SocialSecuritySurvivorClaimingCandidate(p.getHousehold().getSpouse().getBirthDate().plusYears(59),60,0,"Invalid date"));
        for (var s : List.of(mortality(p,List.of(2031),List.of(2031)), mortality(p,List.of(2031),List.of(2040,2050)))) {
            var req = new LongevityWeightedIntegratedStrategyRequest(p, invalid, s, LocalDate.of(2030,1,1), BigDecimal.ZERO);
            if (s.scenarios().size() == 1) {
                financialEquals(new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req),
                        new LongevityWeightedContinuationEvaluator().evaluate(req));
            } else {
                sameFailure(req, new AtomicReference<>(LongevityContinuationWork.zero()));
            }
        }
    }

    static IllegalStateException sameFailure(LongevityWeightedIntegratedStrategyRequest request,
            AtomicReference<LongevityContinuationWork> work) {
        var exact = assertThrows(IllegalStateException.class, () -> new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request));
        var optimized = assertThrows(IllegalStateException.class,
                () -> new LongevityWeightedContinuationEvaluator().evaluate(request, work::set));
        assertEquals(exact.getMessage(), optimized.getMessage());
        assertEquals(exact.getCause().getClass(), optimized.getCause().getClass());
        assertEquals(exact.getCause().getMessage(), optimized.getCause().getMessage());
        return optimized;
    }

    @Test
    void zeroProbabilityTerminalMemberCannotBecomeCarrier() throws Exception {
        var p = Stage4TestPlans.plan();
        var s = PreparedLongevityTestSupport.create(p.getHousehold().getPrimaryPerson().getBirthDate(),
                p.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030,1,1),
                List.of(new SocialSecurityMortalityProbability(71,BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(78,BigDecimal.ONE),
                        new SocialSecurityMortalityProbability(120,BigDecimal.ZERO)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        var req = request(p,s);
        financialEquals(new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req),
                new LongevityWeightedContinuationEvaluator().evaluate(req,work::set));
        assertEquals(1,work.get().positiveScenarios());
        assertEquals(1,work.get().outcomesProduced());
        assertEquals(2040-2030,work.get().completedAnnualRows());
    }

    @Test
    void progressFinalizesOriginalMembersOnceAndFrozenPlanSurvivesLiveEdits() throws Exception {
        var p = Stage4TestPlans.plan();
        var mortality = mortality(p, List.of(2031), List.of(2040, 2050));
        var exact = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(request(p, mortality));
        List<Integer> progress = new ArrayList<>();
        var req = new LongevityWeightedIntegratedStrategyRequest(p, Stage4TestPlans.strategy(p), mortality,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), event -> {
                    progress.add(event.completedWork());
                    assertEquals(mortality.scenarios().size(), event.totalWork());
                    if (event.completedWork() == 0) {
                        p.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ONE);
                    }
                }, AnalysisCancellationToken.none());
        financialEquals(exact, new LongevityWeightedContinuationEvaluator().evaluate(req));
        assertEquals(List.of(0, 1, 2), progress);
    }
}
