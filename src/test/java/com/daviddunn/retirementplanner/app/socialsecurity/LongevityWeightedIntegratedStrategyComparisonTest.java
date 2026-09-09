package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;

class LongevityWeightedIntegratedStrategyComparisonTest {
    private final LongevityWeightedIntegratedStrategyComparisonService service = new LongevityWeightedIntegratedStrategyComparisonService();

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void candidatesMatchDirectStageFourExactlyAndRankByWeightedObjective(int count) {
        var plan = Stage4TestPlans.plan();
        var candidates = List.of(strategy(plan, 62, 62), strategy(plan, 70, 70), strategy(plan, 67, 67)).subList(0, count);
        var request = request(plan, candidates);
        var result = service.compareExact(request);
        assertEquals(count, result.inputStrategyCount());
        assertEquals(count, result.completedStrategyCount());
        assertEquals(0, result.failedStrategyCount());
        assertEquals(count, result.work().stageFourEvaluations());
        assertEquals(count * 2, result.work().projectionEngineRuns());
        assertEquals(count * 2, result.work().completedScenarioEvaluations());
        assertEquals(LongevityWeightedComparisonObjective.EXPECTED_PV_AFTER_TAX_ESTATE, result.objective());
        assertEquals("AFTER_TAX_ESTATE", IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE.name());
        for (int i = 0; i < count; i++) {
            var entry = result.orderedEntries().get(i);
            assertSame(candidates.get(i), entry.strategy());
            assertEquals(i + 1, entry.inputOrder());
            assertEquals(LongevityWeightedStrategyAggregate.from(direct(plan, candidates.get(i), request.longevityScenarios())),
                    entry.aggregate().orElseThrow());
            assertTrue(entry.scenarioDetails().isEmpty());
            assertTrue(entry.pvDifferenceFromBaseline().isEmpty());
        }
        var values = result.rankedSuccessfulEntries().stream().map(e -> e.aggregate().orElseThrow().expectedPvAfterTaxEstate()).toList();
        assertEquals(values.stream().sorted(Comparator.reverseOrder()).toList(), values);
        assertEquals(0, result.retainedDetailedScenarioOutcomeCount());
    }

    @Test
    void duplicateOccurrencesAndFinanciallyEqualDistinctInputsAreNeverCollapsed() {
        var plan = Stage4TestPlans.plan();
        var a = strategy(plan, 67, 67);
        var b = new SocialSecurityHouseholdClaimingStrategy(a.primaryRetirementAge(), a.spouseRetirementAge(),
                a.primaryRetirementClaimDate(), a.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(a.primarySurvivorElection().claimDate(), 62, 0, "Different identity"),
                a.spouseSurvivorElection());
        var result = service.compareExact(request(plan, List.of(a, b, a)));
        assertEquals(3, result.work().stageFourEvaluations());
        assertEquals(6, result.work().projectionEngineRuns());
        assertEquals(List.of(1, 2, 3), result.rankedSuccessfulEntries().stream().map(e -> e.inputOrder()).toList());
        assertEquals(List.of(1, 1, 1), result.rankedSuccessfulEntries().stream().map(e -> e.rank().orElseThrow()).toList());
        assertSame(b, result.orderedEntries().get(1).strategy());
        assertSame(a, result.orderedEntries().get(2).strategy());
    }

    @Test
    void emptyCandidatesCompleteWithoutFinancialWork() {
        var result = service.compareExact(request(Stage4TestPlans.plan(), List.of()));
        assertEquals(0, result.inputStrategyCount());
        assertTrue(result.rankedSuccessfulEntries().isEmpty());
        assertEquals(0, result.work().stageFourEvaluations());
        assertEquals(0, result.work().projectionEngineRuns());
        assertEquals(LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED, result.status());
    }

    @Test
    void baselineAndExplicitSelectedDetailsAreBoundedAndReconcileExactly() {
        var plan = Stage4TestPlans.plan();
        var baseline = LongevityWeightedIntegratedStrategyComparisonRequest.explicitCurrentStrategy(plan);
        var candidates = List.of(strategy(plan, 62, 62), strategy(plan, 70, 70), strategy(plan, 67, 67));
        var request = fullRequest(plan, candidates, Optional.of(baseline),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of(2)), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = service.compareExact(request);
        assertEquals(4, result.work().stageFourEvaluations());
        assertEquals(8, result.work().projectionEngineRuns());
        assertEquals(4, result.retainedDetailedScenarioOutcomeCount());
        assertTrue(result.baseline().orElseThrow().scenarioDetails().isPresent());
        assertTrue(result.orderedEntries().get(0).scenarioDetails().isEmpty());
        var selected = result.orderedEntries().get(1);
        var independentlyRepeated = direct(request.newPlanCopy(), candidates.get(1), request.longevityScenarios());
        assertEquals(independentlyRepeated.scenarioOutcomes(), selected.scenarioDetails().orElseThrow());
        assertEquals(LongevityWeightedStrategyAggregate.from(independentlyRepeated), selected.aggregate().orElseThrow());
        var base = result.baseline().orElseThrow().aggregate().orElseThrow();
        assertEquals(selected.aggregate().orElseThrow().expectedPvAfterTaxEstate().subtract(base.expectedPvAfterTaxEstate()),
                selected.pvDifferenceFromBaseline().orElseThrow());
        assertEquals(selected.aggregate().orElseThrow().expectedNominalEstateAtSecondDeath().subtract(base.expectedNominalEstateAtSecondDeath()),
                selected.nominalDifferenceFromBaseline().orElseThrow());
        assertSame(request.longevityScenarios().assumptions(), result.metadata().longevityAssumptions());
        assertEquals("STAGE5G_EXACT_SECOND_DEATH_V1", result.metadata().methodologyVersion());
        assertThrows(UnsupportedOperationException.class, () -> selected.scenarioDetails().orElseThrow().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.orderedEntries().clear());
    }

    @Test
    void invalidCandidateRemainsAnOrderedFailureAndLaterCandidatesContinue() {
        var plan = Stage4TestPlans.plan();
        var valid = strategy(plan, 67, 67);
        var invalid = new SocialSecurityHouseholdClaimingStrategy(62, 67, valid.primaryRetirementClaimDate(),
                valid.spouseRetirementClaimDate(), valid.primarySurvivorElection(), valid.spouseSurvivorElection());
        var result = service.compareExact(request(plan, List.of(valid, invalid, valid)));
        assertEquals(2, result.completedStrategyCount());
        assertEquals(1, result.failedStrategyCount());
        assertEquals(List.of(1, 3), result.rankedSuccessfulEntries().stream().map(e -> e.inputOrder()).toList());
        var failure = result.failures().getFirst();
        assertSame(invalid, failure.strategy());
        assertEquals(2, failure.inputOrder());
        assertTrue(failure.aggregate().isEmpty());
        assertTrue(failure.rank().isEmpty());
        assertEquals(IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION, failure.failure().orElseThrow().category());
        assertEquals(LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED_WITH_FAILURES, result.status());
    }

    @Test
    void positiveProbabilityFailureCannotPublishPartialWeightedValue() {
        var plan = Stage4TestPlans.plan();
        var s = strategy(plan, 67, 67);
        var mortality = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(71, new BigDecimal("0.5")),
                        new SocialSecurityMortalityProbability(69, new BigDecimal("0.5"))),
                List.of(new SocialSecurityMortalityProbability(67, BigDecimal.ONE)));
        var result = service.compareExact(new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(s), mortality,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(1, result.failedStrategyCount());
        assertTrue(result.orderedEntries().getFirst().aggregate().isEmpty());
        assertEquals(2, result.work().scenarioEvaluations());
        assertEquals(1, result.work().completedScenarioEvaluations());
        assertEquals(1, result.work().projectionEngineRuns());
        assertEquals(1, result.work().completedProjectionEngineRuns());
        assertTrue(result.failures().getFirst().failure().orElseThrow().message().contains("No completed expected value"));
    }

    @Test
    void failedEngineAttemptsAndFailedBaselineAreCountedWithoutInventingMetrics() throws Exception {
        var plan = Stage4TestPlans.plan();
        plan.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2030, new BigDecimal("500000"), new BigDecimal("1000")));
        var source = Stage4TestPlans.json(plan);
        var s = strategy(plan, 67, 67);
        var mortality = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(70, BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(72, BigDecimal.ONE)));
        var result = service.compareExact(new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(s), mortality,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO, Optional.of(s),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of(1)), AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertEquals(2, result.failures().size());
        assertEquals(0, result.completedStrategyCount());
        assertEquals(1, result.failedStrategyCount());
        assertEquals(2, result.work().projectionEngineRuns());
        assertEquals(0, result.work().completedProjectionEngineRuns());
        assertEquals(0, result.work().completedScenarioEvaluations());
        assertEquals(0, result.retainedDetailedScenarioOutcomeCount());
        assertTrue(result.baseline().orElseThrow().aggregate().isEmpty());
        assertTrue(result.baseline().orElseThrow().failure().orElseThrow().message().contains("Cause:"));
        assertTrue(result.orderedEntries().getFirst().pvDifferenceFromBaseline().isEmpty());
        assertEquals(source, Stage4TestPlans.json(plan));
    }

    @Test
    void sourceAndDeterministicResultsAreUnchangedAndFrozenInputsSurviveLiveEdits() throws Exception {
        var plan = Stage4TestPlans.plan();
        var source = Stage4TestPlans.json(plan);
        var before = Stage4TestPlans.json(new ProjectionEngine().project(plan));
        var s = strategy(plan, 67, 67);
        var candidates = new ArrayList<>(List.of(s));
        var request = request(plan, candidates);
        candidates.clear();
        var reference = service.compareExact(request);
        assertEquals(source, Stage4TestPlans.json(plan));
        assertEquals(before, Stage4TestPlans.json(new ProjectionEngine().project(plan)));
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ONE);
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(assumptions.getEconomicAssumptions(), assumptions.getTaxAssumptions(),
                assumptions.getWithdrawalAssumptions(), assumptions.getDeathScenarioAssumptions(), 1, LocalDate.of(2040, 1, 1)));
        var detached = request.newPlanCopy();
        detached.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        assertEquals(reference.orderedEntries(), service.compareExact(request).orderedEntries());
        assertEquals(1, request.candidates().size());
        assertThrows(UnsupportedOperationException.class, () -> request.candidates().clear());
    }

    @Test
    void progressIsStrategyOnlyAndMonotonicIncludingBaseline() {
        var plan = Stage4TestPlans.plan();
        var s = strategy(plan, 67, 67);
        var progress = new ArrayList<AnalysisProgress>();
        service.compareExact(fullRequest(plan, List.of(s, s), Optional.of(s), LongevityWeightedDetailRetentionPolicy.aggregateOnly(),
                progress::add, AnalysisCancellationToken.none()));
        assertEquals(List.of(0, 1, 2, 3), progress.stream().map(AnalysisProgress::completedWork).toList());
        assertTrue(progress.stream().allMatch(p -> p.totalWork() == 3 && p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void cancellationAtEachStrategyBoundaryNeverReturnsCompletedComparison(int stopAt) throws Exception {
        var plan = Stage4TestPlans.plan();
        var source = Stage4TestPlans.json(plan);
        var cancelled = new AtomicBoolean();
        var s = strategy(plan, 67, 67);
        assertThrows(AnalysisCancelledException.class, () -> service.compareExact(fullRequest(plan, List.of(s, s), Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), p -> cancelled.set(p.completedWork() == stopAt), cancelled::get)));
        assertEquals(source, Stage4TestPlans.json(plan));
    }

    @Test
    void cancellationInsideStageFourPropagatesWithoutBecomingStrategyFailure() {
        var plan = Stage4TestPlans.plan();
        var checks = new AtomicInteger();
        var progress = new ArrayList<AnalysisProgress>();
        assertThrows(AnalysisCancelledException.class, () -> service.compareExact(fullRequest(plan, List.of(strategy(plan, 67, 67)),
                Optional.empty(), LongevityWeightedDetailRetentionPolicy.aggregateOnly(), progress::add,
                () -> checks.incrementAndGet() >= 6)));
        assertEquals(List.of(0), progress.stream().map(AnalysisProgress::completedWork).toList());
    }

    static LongevityWeightedIntegratedStrategyComparisonRequest fullRequest(RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates, Optional<SocialSecurityHouseholdClaimingStrategy> baseline,
            LongevityWeightedDetailRetentionPolicy retention, AnalysisProgressListener progress, AnalysisCancellationToken token) {
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios(plan),
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), baseline, retention, progress, token);
    }
}
