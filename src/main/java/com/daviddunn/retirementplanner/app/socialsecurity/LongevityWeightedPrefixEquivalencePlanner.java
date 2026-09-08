package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyCalculator.ScheduleKey;
import java.time.Duration;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Exact Stage 5C1 partitions with guarded coverage of redundant mortality proofs.
 * Members are validated in original order; covered refinements are deferred to the
 * actual carrier (itself a required scenario). No carrier schedules survive a scenario.
 * A failed carrier at its original position isolates that candidate just as Stage 5C1
 * does; no speculative calculation result or failure is assigned to a member.
 */
public final class LongevityWeightedPrefixEquivalencePlanner {
    private final BiPredicate<ScheduleKey, ScheduleKey> coverageGuard;

    public LongevityWeightedPrefixEquivalencePlanner() {
        this((member, carrier) -> true);
    }

    /** Test seam for unavailable proof; cannot authorize a proof rejected by the production guard. */
    LongevityWeightedPrefixEquivalencePlanner(BiPredicate<ScheduleKey, ScheduleKey> coverageGuard) {
        this.coverageGuard = Objects.requireNonNull(coverageGuard);
    }

    public LongevityWeightedStrategyEquivalencePlanner.Plan plan(
            LongevityWeightedIntegratedStrategyComparisonRequest request) {
        Objects.requireNonNull(request);
        long started = System.nanoTime();
        var token = request.cancellationToken();
        token.throwIfCancellationRequested();
        var source = request.newPlanCopy();
        var provider = new SocialSecurityProjectionIncomeProvider();
        var mapper = new HouseholdLifetimeScenarioMapper();
        int first = source.getPlanningAssumptions().getProjectionStartDate().getYear();
        int configuredLast = Math.addExact(first, source.getPlanningAssumptions().getProjectionLengthYears() - 1);
        List<SocialSecurityJointMortalityScenario> scenarios = new ArrayList<>();
        for (var scenario : request.longevityScenarios().scenarios()) {
            token.throwIfCancellationRequested();
            if (scenario.jointProbability().signum() > 0) scenarios.add(scenario);
        }
        var coverage = LongevityEquivalenceCoverage.plan(scenarios, configuredLast, token);
        var work = new Counter(coverage.groupCount());
        List<List<Integer>> partitions = new ArrayList<>();
        List<Integer> valid = new ArrayList<>();
        for (int index = 0; index < request.candidates().size(); index++) {
            token.throwIfCancellationRequested();
            try {
                LongevityWeightedIntegratedStrategyComparisonRequest.validateCompleteStrategy(source, request.candidates().get(index));
                valid.add(index + 1);
            } catch (IllegalArgumentException invalid) {
                partitions.add(List.of(index + 1));
            }
        }
        if (!valid.isEmpty()) partitions.add(valid);
        report(request, 0, scenarios.size());
        for (int index = 0; index < scenarios.size(); index++) {
            token.throwIfCancellationRequested();
            var scenario = scenarios.get(index);
            var lifetime = mapper.map(scenario);
            int last = Math.max(configuredLast, LongevityEquivalenceCoverage.secondDeath(scenario) - 1);
            Integer carrier = coverage.carrierByMember().get(index);
            boolean checkedCoverage = carrier != null && carrier != index;
            if (checkedCoverage) {
                var carrierScenario = scenarios.get(carrier);
                var carrierLifetime = mapper.map(carrierScenario);
                int carrierLast = Math.max(configuredLast, LongevityEquivalenceCoverage.secondDeath(carrierScenario) - 1);
                Set<SocialSecurityHouseholdClaimingStrategy> checked = new HashSet<>();
                Set<ScheduleKey> coveredKeys = new HashSet<>();
                boolean compatible = true;
                for (var partition : partitions) {
                    token.throwIfCancellationRequested();
                    if (partition.size() == 1) continue;
                    for (int order : partition) {
                        token.throwIfCancellationRequested();
                        var strategy = request.candidates().get(order - 1);
                        if (!checked.add(strategy)) continue;
                        work.logical++;
                        try {
                            work.validations++;
                            var memberKey = provider.validatedKeyForEquivalence(source, first, last,
                                    ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime));
                            work.carrierValidations++;
                            var carrierKey = provider.validatedKeyForEquivalence(source, first, carrierLast,
                                    ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, carrierLifetime));
                            if (!LongevityEquivalenceCoverage.covers(memberKey, carrierKey)
                                    || !coverageGuard.test(memberKey, carrierKey)) compatible = false;
                            coveredKeys.add(memberKey);
                        } catch (AnalysisCancelledException cancelled) {
                            throw cancelled;
                        } catch (RuntimeException unproven) {
                            // Validation/compatibility is not a substitute for the original processing.
                            compatible = false;
                        }
                    }
                }
                if (compatible) {
                    work.coveredScenarios++;
                    work.avoided += coveredKeys.size();
                    report(request, index + 1, scenarios.size());
                    continue;
                }
                work.fallbacks++;
            }

            // Original Stage 5C1 refinement, including duplicate caching and failure isolation.
            // Coverage failure re-enters this path for the complete member, never a partial partition.
            var cache = new CountingCache(work, carrier != null && carrier == index);
            Map<SocialSecurityHouseholdClaimingStrategy, Map<Integer, HouseholdSocialSecurityResult>> duplicates = new HashMap<>();
            List<List<Integer>> refined = new ArrayList<>();
            for (var partition : partitions) {
                token.throwIfCancellationRequested();
                if (partition.size() == 1) {
                    refined.add(partition);
                    continue;
                }
                Map<Map<Integer, HouseholdSocialSecurityResult>, List<Integer>> bySchedule = new LinkedHashMap<>();
                for (int order : partition) {
                    token.throwIfCancellationRequested();
                    var strategy = request.candidates().get(order - 1);
                    try {
                        var schedule = duplicates.get(strategy);
                        if (schedule == null) {
                            if (!checkedCoverage) work.logical++;
                            work.validations++;
                            schedule = provider.calculateForEquivalence(source, first, last,
                                    ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime), cache);
                            duplicates.put(strategy, schedule);
                        }
                        bySchedule.computeIfAbsent(schedule, ignored -> new ArrayList<>()).add(order);
                    } catch (AnalysisCancelledException cancelled) {
                        throw cancelled;
                    } catch (RuntimeException unproven) {
                        work.failedRequests++;
                        refined.add(List.of(order));
                    }
                }
                refined.addAll(bySchedule.values());
            }
            partitions = refined;
            report(request, index + 1, scenarios.size());
        }
        token.throwIfCancellationRequested();
        partitions.sort(Comparator.comparingInt(List::getFirst));
        List<Integer> representatives = new ArrayList<>(Collections.nCopies(request.candidates().size(), 0));
        for (var partition : partitions) {
            for (int order : partition) {
                token.throwIfCancellationRequested();
                representatives.set(order - 1, partition.getFirst());
            }
        }
        var snapshot = work.snapshot();
        token.throwIfCancellationRequested();
        return new LongevityWeightedStrategyEquivalencePlanner.Plan(partitions, representatives,
                snapshot.logicalScheduleRequests(), snapshot.totalAuthoritativeCalculations(),
                Duration.ofNanos(System.nanoTime() - started), Optional.of(snapshot));
    }

    private static void report(LongevityWeightedIntegratedStrategyComparisonRequest request, int done, int total) {
        request.progressListener().onProgress(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, done, total));
        request.cancellationToken().throwIfCancellationRequested();
    }

    private static final class Counter {
        long logical, validations, carrierValidations, carriers, independents, failedRequests, rows, avoided;
        int groups, coveredScenarios, fallbacks;
        Counter(int groups) { this.groups = groups; }
        LongevityEquivalencePlanningWork snapshot() {
            return new LongevityEquivalencePlanningWork(logical, validations, carrierValidations, carriers,
                    independents, failedRequests, rows, groups, coveredScenarios, avoided, fallbacks);
        }
    }

    /** The provider checks this only after validation, immediately before an authoritative calculation. */
    private static final class CountingCache extends HashMap<ScheduleKey, Map<Integer, HouseholdSocialSecurityResult>> {
        private final Counter work;
        private final boolean carrier;
        CountingCache(Counter work, boolean carrier) { this.work = work; this.carrier = carrier; }
        @Override public boolean containsKey(Object key) {
            boolean found = super.containsKey(key);
            if (!found) {
                if (carrier) work.carriers++;
                else work.independents++;
            }
            return found;
        }
        @Override public Map<Integer, HouseholdSocialSecurityResult> put(ScheduleKey key, Map<Integer, HouseholdSocialSecurityResult> value) {
            work.rows += value.size();
            return super.put(key, value);
        }
    }
}
