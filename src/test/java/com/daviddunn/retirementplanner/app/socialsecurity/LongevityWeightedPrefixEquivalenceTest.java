package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityContinuationTest.mortality;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityEquivalencePrefixCharacterizationTest.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyEquivalenceTest.*;

class LongevityWeightedPrefixEquivalenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"older", "younger", "early", "survivor", "simultaneous", "partial", "extended", "reverse-order"})
    void fullPartitionsMatchReferenceAcrossFixtures(String fixture) throws Exception {
        var plan = fixture.equals("older") ? Stage4TestPlans.plan() : youngPlan();
        if (fixture.equals("partial")) opening(plan, LocalDate.of(2032, 7, 1), 5);
        if (fixture.equals("extended")) opening(plan, LocalDate.of(2030, 1, 1), 25);
        var scenarios = fixture.equals("early") ? mortality(plan, List.of(2031, 2032), List.of(2031, 2034))
                : fixture.equals("simultaneous") ? mortality(plan, List.of(2040, 2050), List.of(2040, 2050))
                : fixture.equals("reverse-order") ? mortality(plan, List.of(2055, 2033), List.of(2055, 2040))
                : mortality(plan, List.of(2033, 2045), List.of(2040, 2055));
        var candidates = new ArrayList<>(LongevityWeightedEquivalenceBenchmarkTest.universe(plan).stream()
                .filter(s -> List.of(62, 67, 70).contains(s.primaryRetirementAge())
                        && List.of(62, 67, 70).contains(s.spouseRetirementAge())).toList());
        candidates.add(candidates.getFirst());
        candidates.add(candidates.get(100));
        var before = Stage4TestPlans.json(plan);
        var request = request(plan, candidates, scenarios);
        var exact = new LongevityWeightedStrategyEquivalencePlanner().plan(request);
        var actual = new LongevityWeightedPrefixEquivalencePlanner().plan(request);
        assertPartitions(exact, actual);
        assertEquals(before, Stage4TestPlans.json(plan));
        for (int i = 0; i < candidates.size(); i++) assertSame(candidates.get(i), request.candidates().get(i));
        assertTrue(actual.coverageWork().isPresent());
        if (fixture.equals("early")) assertEquals(0, actual.coverageWork().orElseThrow().coveredScenarios());
        else if (!fixture.equals("extended")) assertTrue(actual.coverageWork().orElseThrow().coveredScenarios() > 0);
    }

    @Test
    void guardUnavailabilityReplaysOriginalMemberRefinement() {
        var plan = youngPlan();
        var candidates = LongevityWeightedEquivalenceBenchmarkTest.universe(plan).subList(0, 128);
        var request = request(plan, candidates, mortality(plan, List.of(2033), List.of(2040, 2055)));
        var exact = new LongevityWeightedStrategyEquivalencePlanner().plan(request);
        var fallback = new LongevityWeightedPrefixEquivalencePlanner((member, carrier) -> false).plan(request);
        assertPartitions(exact, fallback);
        assertEquals(exact.scheduleCalculations(), fallback.scheduleCalculations());
        assertEquals(1, fallback.coverageWork().orElseThrow().fallbackScenarios());
        assertEquals(0, fallback.coverageWork().orElseThrow().scheduleCalculationsAvoidedByCoverage());
    }

    @Test
    void invalidCandidatesAndDuplicateIdentitiesRemainExactSingletons() {
        var plan = youngPlan();
        var own = Stage4TestPlans.strategy(plan);
        var invalidDate = new SocialSecurityHouseholdClaimingStrategy(62, own.spouseRetirementAge(),
                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(), own.primarySurvivorElection(), own.spouseSurvivorElection());
        var invalidSurvivor = new SocialSecurityHouseholdClaimingStrategy(own.primaryRetirementAge(), own.spouseRetirementAge(),
                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(59),
                        60, 0, "Invalid"), own.spouseSurvivorElection());
        var request = request(plan, List.of(own, invalidDate, own, invalidDate, invalidSurvivor, invalidSurvivor),
                mortality(plan, List.of(2033), List.of(2040, 2055)));
        var actual = new LongevityWeightedPrefixEquivalencePlanner().plan(request);
        assertPartitions(new LongevityWeightedStrategyEquivalencePlanner().plan(request), actual);
        assertEquals(List.of(1, 2, 1, 4, 5, 6), actual.representativeOrders());
    }

    @Test
    void zeroProbabilityCarrierIsExcludedAndCarrierTiesKeepOriginalOrder() {
        var plan = youngPlan();
        var scenarios = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(63, BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(68, new BigDecimal("0.5")),
                        new SocialSecurityMortalityProbability(78, new BigDecimal("0.5")),
                        new SocialSecurityMortalityProbability(120, BigDecimal.ZERO)));
        var members = new ArrayList<>(scenarios.scenarios());
        members.add(members.get(1));
        var coverage = LongevityEquivalenceCoverage.plan(members, 2034, AnalysisCancellationToken.none());
        assertEquals(Map.of(0, 1, 1, 1, 3, 1), coverage.carrierByMember());
        var candidates = List.of(Stage4TestPlans.strategy(plan), Stage4TestPlans.strategy(plan));
        var request = request(plan, candidates, scenarios);
        assertPartitions(new LongevityWeightedStrategyEquivalencePlanner().plan(request),
                new LongevityWeightedPrefixEquivalencePlanner().plan(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "coverage", "candidate", "carrier", "fallback", "final"})
    void cancellationDoesNotPublishCompletedPartitions(String boundary) {
        var plan = youngPlan();
        var stop = new AtomicBoolean(boundary.equals("before"));
        var checks = new AtomicInteger();
        var candidates = List.of(Stage4TestPlans.strategy(plan), Stage4TestPlans.strategy(plan));
        List<Integer> progress = new ArrayList<>();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates,
                mortality(plan, List.of(2033), List.of(2040, 2055)), LocalDate.of(2030, 1, 1), BigDecimal.ZERO,
                Optional.empty(), LongevityWeightedDetailRetentionPolicy.aggregateOnly(), event -> {
                    progress.add(event.completedWork());
                    if (boundary.equals("carrier") && event.completedWork() == 1
                            || boundary.equals("final") && event.completedWork() == 2) stop.set(true);
                }, () -> stop.get() || boundary.equals("coverage") && checks.incrementAndGet() == 3);
        var planner = new LongevityWeightedPrefixEquivalencePlanner((member, carrier) -> {
            if (boundary.equals("candidate") || boundary.equals("fallback")) stop.set(true);
            return !boundary.equals("fallback");
        });
        assertThrows(AnalysisCancelledException.class, () -> planner.plan(request));
        assertEquals(progress.stream().sorted().toList(), progress);
    }

    @Test
    void frozenInputsSurviveProgressCallbacksAndResultsAreImmutable() {
        var plan = youngPlan();
        var candidates = List.of(Stage4TestPlans.strategy(plan), Stage4TestPlans.strategy(plan));
        var scenarios = mortality(plan, List.of(2033), List.of(2040, 2055));
        var expected = new LongevityWeightedStrategyEquivalencePlanner().plan(request(plan, candidates, scenarios));
        List<Integer> progress = new ArrayList<>();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO, Optional.empty(), LongevityWeightedDetailRetentionPolicy.aggregateOnly(),
                event -> {
                    progress.add(event.completedWork());
                    opening(plan, LocalDate.of(2030, 7, 1), 60);
                }, AnalysisCancellationToken.none());
        var actual = new LongevityWeightedPrefixEquivalencePlanner().plan(request);
        assertPartitions(expected, actual);
        assertEquals(List.of(0, 1, 2), progress);
        assertThrows(UnsupportedOperationException.class, () -> actual.groups().getFirst().clear());
    }

    @Test
    void emptyCandidatesRetainEmptyPartition() {
        var plan = youngPlan();
        var request = request(plan, List.of(), mortality(plan, List.of(2033), List.of(2040, 2055)));
        assertPartitions(new LongevityWeightedStrategyEquivalencePlanner().plan(request),
                new LongevityWeightedPrefixEquivalencePlanner().plan(request));
    }

    static LongevityWeightedIntegratedStrategyComparisonRequest request(RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates, HouseholdLongevityScenarios scenarios) {
        return new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"));
    }

    static void assertPartitions(LongevityWeightedStrategyEquivalencePlanner.Plan expected,
            LongevityWeightedStrategyEquivalencePlanner.Plan actual) {
        assertEquals(expected.groups(), actual.groups());
        assertEquals(expected.representativeOrders(), actual.representativeOrders());
        assertEquals(expected.inputStrategyCount(), actual.inputStrategyCount());
        assertEquals(expected.equivalenceGroupCount(), actual.equivalenceGroupCount());
    }
}
