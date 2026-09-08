package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/** Exact sequential comparison with optional proven schedule equivalence outside the financial engine. */
public final class LongevityWeightedIntegratedStrategyComparisonService {
    private final LongevityWeightedIntegratedStrategyEvaluator evaluator = new LongevityWeightedIntegratedStrategyEvaluator();
    private final LongevityWeightedContinuationEvaluator continuationEvaluator = new LongevityWeightedContinuationEvaluator();

    public LongevityWeightedIntegratedStrategyComparisonResult compare(
            LongevityWeightedIntegratedStrategyComparisonRequest request) {
        return compare(request, true, true);
    }

    /** Stage 5A/5B financial reference: evaluates every original occurrence independently. */
    public LongevityWeightedIntegratedStrategyComparisonResult compareExact(
            LongevityWeightedIntegratedStrategyComparisonRequest request) {
        return compare(request, false, false);
    }

    /** Stage 5C1 reference, retaining its original independent representative evaluations. */
    LongevityWeightedIntegratedStrategyComparisonResult compareWithEquivalenceOnly(
            LongevityWeightedIntegratedStrategyComparisonRequest request) {
        return compare(request, true, false);
    }

    private LongevityWeightedIntegratedStrategyComparisonResult compare(
            LongevityWeightedIntegratedStrategyComparisonRequest request, boolean optimize, boolean continuations) {
        Objects.requireNonNull(request, "Comparison request is required.");
        long started = System.nanoTime();
        request.cancellationToken().throwIfCancellationRequested();
        var plan = request.newPlanCopy();
        var proof = optimize ? Optional.of(continuations
                ? new LongevityWeightedPrefixEquivalencePlanner().plan(request)
                : new LongevityWeightedStrategyEquivalencePlanner().plan(request))
                : Optional.<LongevityWeightedStrategyEquivalencePlanner.Plan>empty();
        var work = new WorkCounter();
        long avoided = 0;
        int total = Math.addExact(request.candidates().size(), request.baselineStrategy().isPresent() ? 1 : 0);
        int completed = 0;
        report(request, completed, total);
        Optional<LongevityWeightedIntegratedStrategyComparisonEntry> baseline = Optional.empty();
        if (request.baselineStrategy().isPresent()) {
            baseline = Optional.of(evaluate(request, plan, request.baselineStrategy().orElseThrow(), 0,
                    request.detailRetention().retainBaseline(), work, continuations));
            report(request, ++completed, total);
        }
        List<LongevityWeightedIntegratedStrategyComparisonEntry> entries = new ArrayList<>();
        Map<Integer, LongevityWeightedIntegratedStrategyComparisonEntry> representatives = new HashMap<>();
        Set<Integer> detailRepresentatives = new HashSet<>();
        proof.ifPresent(value -> request.detailRetention().selectedCandidateOrders().forEach(order ->
                detailRepresentatives.add(value.representativeOrders().get(order - 1))));
        for (int index = 0; index < request.candidates().size(); index++) {
            request.cancellationToken().throwIfCancellationRequested();
            int order = index + 1;
            int representative = proof.isPresent() ? proof.orElseThrow().representativeOrders().get(index) : order;
            boolean retain = request.detailRetention().selectedCandidateOrders().contains(order);
            var value = representatives.get(representative);
            if (value == null || !value.successful()) {
                value = evaluate(request, plan, request.candidates().get(index), order,
                        retain || detailRepresentatives.contains(order), work, continuations);
                if (order == representative) {
                    representatives.put(order, value);
                }
            } else {
                avoided++;
            }
            // Detail outcomes contain no strategy identity. The original entry owns identity.
            // Failed representatives are never shared: each member executes the reference path.
            entries.add(new LongevityWeightedIntegratedStrategyComparisonEntry(order, request.candidates().get(index),
                    value.aggregate(), value.failure(), retain ? value.scenarioDetails() : Optional.empty(),
                    OptionalInt.empty(), Optional.empty(), Optional.empty()));
            report(request, ++completed, total);
        }
        request.cancellationToken().throwIfCancellationRequested();
        var sorted = entries.stream().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .sorted(ranking()).toList();
        Map<Integer, Integer> ranks = new HashMap<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int index = 0; index < sorted.size(); index++) {
            var value = sorted.get(index).aggregate().orElseThrow().expectedPvAfterTaxEstate();
            if (previous == null || previous.compareTo(value) != 0) {
                rank = index + 1;
                previous = value;
            }
            ranks.put(sorted.get(index).inputOrder(), rank);
        }
        var baselineAggregate = baseline.flatMap(LongevityWeightedIntegratedStrategyComparisonEntry::aggregate);
        var ordered = entries.stream().map(entry -> rankedEntry(entry, ranks, baselineAggregate)).toList();
        var ranked = ordered.stream().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .sorted(ranking()).toList();
        boolean failures = ranked.size() != ordered.size() || baseline.filter(entry -> !entry.successful()).isPresent();
        var metadata = new LongevityWeightedIntegratedStrategyComparisonResult.Metadata(
                "STAGE4_EXACT_V1", "STAGE4-LONGEVITY-EVALUATION.md",
                request.longevityScenarios().assumptions(), request.valuationDate(),
                plan.getPlanningAssumptions().getEconomicAssumptions().getGeneralInflationRate(), request.realDiscountRate(),
                request.longevityScenarios().scenarios().size(), request.longevityScenarios().scenarios().stream()
                        .map(scenario -> scenario.jointProbability()).reduce(BigDecimal.ZERO, BigDecimal::add),
                List.of("Deceased-owner assets remain invested and available for household spending/tax funding, "
                                + "with unchanged tax classification; no deceased-owner RMD or Roth conversion.",
                        "No inheritance, retitling, inherited-account distributions, beneficiary rules or estate settlement; "
                                + "omitted distributions can materially affect expected estate.",
                        "Investable estate only, with the existing estimated tax-deferred heir-tax haircut. "
                                + "January 1 second death uses the preceding ending balance or matching opening snapshot.",
                        "Independent mortality; actual-days/365.25 general-inflation and real discounting. "
                                + "Living owners beyond supported RMD ages remain unsupported."));
        request.cancellationToken().throwIfCancellationRequested();
        return new LongevityWeightedIntegratedStrategyComparisonResult(
                LongevityWeightedComparisonObjective.EXPECTED_PV_AFTER_TAX_ESTATE, metadata, ordered, ranked, baseline,
                work.snapshot(), Duration.ofNanos(System.nanoTime() - started), failures
                ? LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED_WITH_FAILURES
                : LongevityWeightedIntegratedStrategyComparisonResult.CompletionStatus.COMPLETED,
                proof, avoided);
    }

    private LongevityWeightedIntegratedStrategyComparisonEntry evaluate(
            LongevityWeightedIntegratedStrategyComparisonRequest request, RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy, int order, boolean retainDetail, WorkCounter work,
            boolean continuations) {
        request.cancellationToken().throwIfCancellationRequested();
        try {
            LongevityWeightedIntegratedStrategyComparisonRequest.validateCompleteStrategy(plan, strategy);
            var evaluationRequest = new LongevityWeightedIntegratedStrategyRequest(plan, strategy,
                    request.longevityScenarios(), request.valuationDate(), request.realDiscountRate(),
                    AnalysisProgressListener.none(), request.cancellationToken());
            LongevityWeightedIntegratedStrategyResult result;
            if (continuations) {
                work.continuationEvaluations++;
                LongevityContinuationWork[] latest = {LongevityContinuationWork.zero()};
                try {
                    result = continuationEvaluator.evaluate(evaluationRequest, value -> latest[0] = value);
                } finally {
                    work.continuation = work.continuation.plus(latest[0]);
                }
            } else {
                work.evaluations++;
                result = evaluator.evaluate(evaluationRequest, work::observe);
            }
            request.cancellationToken().throwIfCancellationRequested();
            return new LongevityWeightedIntegratedStrategyComparisonEntry(order, strategy,
                    Optional.of(LongevityWeightedStrategyAggregate.from(result)), Optional.empty(),
                    retainDetail ? Optional.of(result.scenarioOutcomes()) : Optional.empty(),
                    OptionalInt.empty(), Optional.empty(), Optional.empty());
        } catch (AnalysisCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException failure) {
            request.cancellationToken().throwIfCancellationRequested();
            var category = failure instanceof IllegalArgumentException
                    ? IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION
                    : IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION;
            var message = failure.getMessage() == null ? "Weighted strategy evaluation failed." : failure.getMessage();
            if (message.isBlank()) {
                message = "Weighted strategy evaluation failed.";
            }
            if (failure.getCause() != null && failure.getCause().getMessage() != null) {
                message += " Cause: " + failure.getCause().getMessage();
            }
            return new LongevityWeightedIntegratedStrategyComparisonEntry(order, strategy, Optional.empty(),
                    Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(strategy, message, category)),
                    Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
        }
    }

    private static Comparator<LongevityWeightedIntegratedStrategyComparisonEntry> ranking() {
        return Comparator.<LongevityWeightedIntegratedStrategyComparisonEntry, BigDecimal>comparing(
                entry -> entry.aggregate().orElseThrow().expectedPvAfterTaxEstate()).reversed()
                .thenComparingInt(LongevityWeightedIntegratedStrategyComparisonEntry::inputOrder);
    }

    private static LongevityWeightedIntegratedStrategyComparisonEntry rankedEntry(
            LongevityWeightedIntegratedStrategyComparisonEntry entry, Map<Integer, Integer> ranks,
            Optional<LongevityWeightedStrategyAggregate> baseline) {
        if (!entry.successful()) {
            return entry;
        }
        var value = entry.aggregate().orElseThrow();
        return new LongevityWeightedIntegratedStrategyComparisonEntry(entry.inputOrder(), entry.strategy(), entry.aggregate(),
                entry.failure(), entry.scenarioDetails(), OptionalInt.of(ranks.get(entry.inputOrder())),
                baseline.map(base -> value.expectedPvAfterTaxEstate().subtract(base.expectedPvAfterTaxEstate())),
                baseline.map(base -> value.expectedNominalEstateAtSecondDeath().subtract(base.expectedNominalEstateAtSecondDeath())));
    }

    private static void report(LongevityWeightedIntegratedStrategyComparisonRequest request, int completed, int total) {
        request.progressListener().onProgress(new AnalysisProgress(AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON,
                completed, total));
        request.cancellationToken().throwIfCancellationRequested();
    }

    private static final class WorkCounter {
        private long evaluations;
        private long scenarios;
        private long completedScenarios;
        private long projections;
        private long completedProjections;
        private long continuationEvaluations;
        private LongevityContinuationWork continuation = LongevityContinuationWork.zero();

        private void observe(LongevityWeightedEvaluationWork event) {
            switch (event) {
                case SCENARIO_STARTED -> scenarios++;
                case SCENARIO_COMPLETED -> completedScenarios++;
                case PROJECTION_STARTED -> projections++;
                case PROJECTION_COMPLETED -> completedProjections++;
            }
        }

        private LongevityWeightedIntegratedStrategyComparisonResult.Work snapshot() {
            return new LongevityWeightedIntegratedStrategyComparisonResult.Work(evaluations,
                    scenarios + continuation.scenariosStarted(), completedScenarios + continuation.outcomesProduced(),
                    projections + continuation.projectionStarts(), completedProjections + continuation.completedProjections(),
                    continuationEvaluations, continuation);
        }
    }
}
