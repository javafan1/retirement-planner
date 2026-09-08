package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.time.Duration;
import java.util.*;

/**
 * Refines partitions by exact, complete annual provider results for every positive
 * mortality outcome. Hash maps resolve collisions using full record equality,
 * including BigDecimal scale. No hash-only proof or household-total shortcut.
 * Caches live for one mortality outcome only. Singleton partitions need no further
 * proof. Invalid/unprovable inputs remain singleton and are evaluated individually.
 */
public final class LongevityWeightedStrategyEquivalencePlanner {
    public Plan plan(LongevityWeightedIntegratedStrategyComparisonRequest request) {
        Objects.requireNonNull(request);
        long started = System.nanoTime();
        var source = request.newPlanCopy();
        var token = request.cancellationToken();
        List<List<Integer>> partitions = new ArrayList<>();
        List<Integer> valid = new ArrayList<>();
        for (int index = 0; index < request.candidates().size(); index++) {
            token.throwIfCancellationRequested();
            try {
                LongevityWeightedIntegratedStrategyComparisonRequest.validateCompleteStrategy(
                        source, request.candidates().get(index));
                valid.add(index + 1);
            } catch (IllegalArgumentException invalid) {
                partitions.add(List.of(index + 1));
            }
        }
        if (!valid.isEmpty()) {
            partitions.add(valid);
        }
        var provider = new SocialSecurityProjectionIncomeProvider();
        var mapper = new HouseholdLifetimeScenarioMapper();
        int first = source.getPlanningAssumptions().getProjectionStartDate().getYear();
        int configuredLast = Math.addExact(first,
                source.getPlanningAssumptions().getProjectionLengthYears() - 1);
        var scenarios = request.longevityScenarios().scenarios().stream()
                .filter(scenario -> scenario.jointProbability().signum() > 0).toList();
        long scheduleCalculations = 0;
        long scheduleRequests = 0;
        int completed = 0;
        report(request, completed, scenarios.size());
        for (var scenario : scenarios) {
            token.throwIfCancellationRequested();
            var lifetime = mapper.map(scenario);
            int last = Math.max(configuredLast,
                    Math.max(scenario.primaryDeathDate().getYear(), scenario.spouseDeathDate().getYear()) - 1);
            Map<SocialSecurityStrategyCalculator.ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> cache = new HashMap<>();
            Map<SocialSecurityHouseholdClaimingStrategy, Map<Integer, HouseholdSocialSecurityResult>> duplicateCache = new HashMap<>();
            List<List<Integer>> refined = new ArrayList<>();
            for (var partition : partitions) {
                if (partition.size() == 1) {
                    refined.add(partition);
                    continue;
                }
                Map<Map<Integer, HouseholdSocialSecurityResult>, List<Integer>> bySchedule = new LinkedHashMap<>();
                for (int order : partition) {
                    token.throwIfCancellationRequested();
                    var strategy = request.candidates().get(order - 1);
                    try {
                        var schedule = duplicateCache.get(strategy);
                        if (schedule == null) {
                            scheduleRequests++;
                            schedule = provider.calculateForEquivalence(source, first, last,
                                    ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime), cache);
                            duplicateCache.put(strategy, schedule);
                        }
                        bySchedule.computeIfAbsent(schedule, ignored -> new ArrayList<>()).add(order);
                    } catch (AnalysisCancelledException cancelled) {
                        throw cancelled;
                    } catch (RuntimeException unproven) {
                        // Let the financial reference report the original failure, if any.
                        refined.add(List.of(order));
                    }
                }
                refined.addAll(bySchedule.values());
            }
            scheduleCalculations += cache.size();
            partitions = refined;
            report(request, ++completed, scenarios.size());
        }
        partitions.sort(Comparator.comparingInt(List::getFirst));
        List<Integer> representatives = new ArrayList<>(Collections.nCopies(request.candidates().size(), 0));
        for (var partition : partitions) {
            for (int order : partition) {
                representatives.set(order - 1, partition.getFirst());
            }
        }
        token.throwIfCancellationRequested();
        return new Plan(partitions, representatives, scheduleRequests, scheduleCalculations,
                Duration.ofNanos(System.nanoTime() - started));
    }

    private static void report(LongevityWeightedIntegratedStrategyComparisonRequest request, int done, int total) {
        request.progressListener().onProgress(new AnalysisProgress(
                AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, done, total));
        request.cancellationToken().throwIfCancellationRequested();
    }

    public record Plan(List<List<Integer>> groups, List<Integer> representativeOrders,
            long scheduleRequests, long scheduleCalculations, Duration elapsedTime,
            Optional<LongevityEquivalencePlanningWork> coverageWork) {
        public Plan(List<List<Integer>> groups, List<Integer> representativeOrders,
                long scheduleRequests, long scheduleCalculations, Duration elapsedTime) {
            this(groups, representativeOrders, scheduleRequests, scheduleCalculations, elapsedTime, Optional.empty());
        }

        public Plan {
            groups = groups.stream().map(List::copyOf).toList();
            representativeOrders = List.copyOf(representativeOrders);
            coverageWork = Objects.requireNonNull(coverageWork);
        }

        public int equivalenceGroupCount() { return groups.size(); }
        public int inputStrategyCount() { return representativeOrders.size(); }
        public int potentialEvaluationsAvoided() { return inputStrategyCount() - equivalenceGroupCount(); }
    }
}
