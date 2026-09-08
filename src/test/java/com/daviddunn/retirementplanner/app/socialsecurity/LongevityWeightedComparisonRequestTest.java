package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonTest.fullRequest;

class LongevityWeightedComparisonRequestTest {
    @Test
    void currentPlanBaselineRequiresExplicitPolicyButNoBaselineAndExplicitOverridesStillWork() {
        var plan = Stage4TestPlans.plan();
        var explicit = strategy(plan, 67, 67);
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(assumptions.getEconomicAssumptions(), assumptions.getTaxAssumptions(),
                assumptions.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null),
                assumptions.getProjectionLengthYears(), assumptions.getProjectionStartDate()));
        var error = assertThrows(IllegalArgumentException.class,
                () -> LongevityWeightedIntegratedStrategyComparisonRequest.explicitCurrentStrategy(plan));
        assertTrue(error.getMessage().contains("no default survivor age"));
        var service = new LongevityWeightedIntegratedStrategyComparisonService();
        assertEquals(1, service.compareExact(request(plan, List.of(explicit))).completedStrategyCount());
        var withBaseline = service.compareExact(fullRequest(plan, List.of(explicit), Optional.of(explicit),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of()), AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertSame(explicit, withBaseline.baseline().orElseThrow().strategy());
        assertEquals(62, withBaseline.baseline().orElseThrow().strategy().primarySurvivorElection().ageYears());
        assertEquals(0, withBaseline.orderedEntries().getFirst().pvDifferenceFromBaseline().orElseThrow().signum());
    }

    @Test
    void malformedOrIncompleteBaselineFailsBeforeEvaluation() {
        var plan = Stage4TestPlans.plan();
        var valid = strategy(plan, 67, 67);
        assertThrows(NullPointerException.class, () -> new SocialSecurityHouseholdClaimingStrategy(67, 67,
                valid.primaryRetirementClaimDate(), valid.spouseRetirementClaimDate(), null, valid.spouseSurvivorElection()));
        var invalid = new SocialSecurityHouseholdClaimingStrategy(67, 67, valid.primaryRetirementClaimDate(),
                valid.spouseRetirementClaimDate(), new SocialSecuritySurvivorClaimingCandidate(
                        LocalDate.of(2000, 1, 1), 60, 0, "Invalid early election"), valid.spouseSurvivorElection());
        assertThrows(IllegalArgumentException.class, () -> fullRequest(plan, List.of(valid), Optional.of(invalid),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertThrows(NullPointerException.class, () -> request(plan, Arrays.asList(valid, null)));
    }

    @Test
    void requestedDetailBoundsAndDefensiveCollectionsAreEnforced() {
        var plan = Stage4TestPlans.plan();
        var s = strategy(plan, 67, 67);
        var selected = new HashSet<>(Set.of(1));
        var policy = new LongevityWeightedDetailRetentionPolicy(false, selected);
        selected.clear();
        assertEquals(Set.of(1), policy.selectedCandidateOrders());
        assertThrows(UnsupportedOperationException.class, () -> policy.selectedCandidateOrders().clear());
        assertThrows(IllegalArgumentException.class, () -> new LongevityWeightedDetailRetentionPolicy(false, Set.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new LongevityWeightedDetailRetentionPolicy(true,
                new HashSet<>(IntStream.rangeClosed(1, 20).boxed().toList())));
        assertThrows(IllegalArgumentException.class, () -> fullRequest(plan, List.of(s), Optional.empty(),
                new LongevityWeightedDetailRetentionPolicy(false, Set.of(2)), AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        var request = request(plan, List.of(s));
        assertThrows(UnsupportedOperationException.class, () -> request.longevityScenarios().scenarios().clear());
        assertThrows(IllegalArgumentException.class, () -> new LongevityWeightedIntegratedStrategyComparisonRequest(
                plan, List.of(s), scenarios(plan), LocalDate.of(2030, 1, 1), new BigDecimal("-1")));
    }

    @Test
    void progressCallbackLiveEditsCannotChangeAlreadyFrozenJob() {
        var plan = Stage4TestPlans.plan();
        var s = strategy(plan, 67, 67);
        var expected = direct(plan, s, scenarios(plan));
        var request = fullRequest(plan, List.of(s), Optional.empty(), LongevityWeightedDetailRetentionPolicy.aggregateOnly(),
                progress -> plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO),
                AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareExact(request);
        assertEquals(LongevityWeightedStrategyAggregate.from(expected), result.orderedEntries().getFirst().aggregate().orElseThrow());
    }

    @Test
    void emptyCandidatesMayStillEvaluateAndRetainRequestedBaseline() {
        var plan = Stage4TestPlans.plan();
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareExact(fullRequest(plan, List.of(),
                Optional.of(strategy(plan, 67, 67)), new LongevityWeightedDetailRetentionPolicy(true, Set.of()),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertEquals(0, result.inputStrategyCount());
        assertEquals(1, result.work().stageFourEvaluations());
        assertEquals(2, result.retainedDetailedScenarioOutcomeCount());
        assertTrue(result.baseline().orElseThrow().rank().isEmpty());
    }
}
