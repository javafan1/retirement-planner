package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PriorityQueue;

/** Exhaustive sequential deterministic search across supplied retirement and survivor elections. */
public final class IntegratedSocialSecurityCompleteStrategySearchCalculator {

    private final IntegratedSocialSecurityStrategyEvaluator evaluator;

    public IntegratedSocialSecurityCompleteStrategySearchCalculator() {
        this(new IntegratedSocialSecurityStrategyEvaluator());
    }

    IntegratedSocialSecurityCompleteStrategySearchCalculator(
            IntegratedSocialSecurityStrategyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public IntegratedSocialSecurityCompleteStrategySearchResult calculate(
            RetirementPlan plan) {
        return calculate(IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan));
    }

    public IntegratedSocialSecurityCompleteStrategySearchResult calculate(
            IntegratedSocialSecurityCompleteStrategySearchRequest request) {
        return calculate(request, AnalysisProgressListener.none(),
                AnalysisCancellationToken.none());
    }

    public IntegratedSocialSecurityCompleteStrategySearchResult calculate(
            IntegratedSocialSecurityCompleteStrategySearchRequest request,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellationToken) {
        Objects.requireNonNull(request, "Complete strategy search request is required.");
        Objects.requireNonNull(progressListener, "Progress listener is required.");
        Objects.requireNonNull(cancellationToken, "Cancellation token is required.");
        RetirementPlan plan = request.plan();
        cancellationToken.throwIfCancellationRequested();
        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.CURRENT_PLAN_BASELINE, 0, 1));
        IntegratedSocialSecurityStrategyResult baseline =
                evaluator.evaluateCurrentStrategy(plan);
        cancellationToken.throwIfCancellationRequested();
        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.CURRENT_PLAN_BASELINE, 1, 1));
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        List<Outcome> outcomes = new ArrayList<>(request.strategyCount());
        PriorityQueue<DetailCandidate> retainedDetails = new PriorityQueue<>(
                worstDetailFirst(request.rankingMeasure()));
        int generationOrder = 0;
        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES,
                0,
                request.strategyCount()));

        for (int primaryAge : request.primaryRetirementAges()) {
            LocalDate primaryDate = SocialSecurityRetirementDateCalculator
                    .calculateRetirementClaimDate(primary.getBirthDate(), primaryAge);
            for (int spouseAge : request.spouseRetirementAges()) {
                LocalDate spouseDate = SocialSecurityRetirementDateCalculator
                        .calculateRetirementClaimDate(spouse.getBirthDate(), spouseAge);
                for (SocialSecuritySurvivorClaimingCandidate primarySurvivor
                        : request.primarySurvivorCandidates()) {
                    for (SocialSecuritySurvivorClaimingCandidate spouseSurvivor
                            : request.spouseSurvivorCandidates()) {
                        cancellationToken.throwIfCancellationRequested();
                        generationOrder++;
                        SocialSecurityHouseholdClaimingStrategy strategy =
                                new SocialSecurityHouseholdClaimingStrategy(
                                        primaryAge, spouseAge, primaryDate, spouseDate,
                                        primarySurvivor, spouseSurvivor);
                        Outcome outcome = evaluate(
                                plan, strategy, generationOrder, baseline.metrics());
                        outcome.detailCandidate().ifPresent(candidate -> retainDetail(
                                retainedDetails,
                                candidate,
                                request.detailRetentionCount(),
                                request.rankingMeasure()));
                        outcomes.add(outcome.withoutDetail());
                        cancellationToken.throwIfCancellationRequested();
                        progressListener.onProgress(new AnalysisProgress(
                                AnalysisPhase.EXHAUSTIVE_INTEGRATED_STRATEGIES,
                                generationOrder,
                                request.strategyCount()));
                    }
                }
            }
        }

        Comparator<Outcome> ranking = rankingComparator(request.rankingMeasure());
        List<Outcome> rankedOutcomes = outcomes.stream()
                .filter(Outcome::successful)
                .sorted(ranking)
                .toList();
        Map<SocialSecurityHouseholdClaimingStrategy, Integer> ranks = assignRanks(
                rankedOutcomes, request.rankingMeasure());
        Map<SocialSecurityHouseholdClaimingStrategy, IntegratedSocialSecurityStrategyResult>
                details = new HashMap<>();
        retainedDetails.forEach(candidate -> details.put(
                candidate.strategy(), candidate.result()));

        List<IntegratedSocialSecurityCompleteStrategySearchEntry> entries = outcomes.stream()
                .map(outcome -> entry(outcome, ranks, details))
                .toList();
        Map<SocialSecurityHouseholdClaimingStrategy,
                IntegratedSocialSecurityCompleteStrategySearchEntry> entriesByStrategy =
                new HashMap<>();
        entries.forEach(entry -> entriesByStrategy.put(entry.strategy(), entry));
        List<IntegratedSocialSecurityCompleteStrategySearchEntry> rankedEntries =
                rankedOutcomes.stream()
                        .map(outcome -> entriesByStrategy.get(outcome.strategy()))
                        .toList();
        int currentRank = 1 + (int) rankedOutcomes.stream()
                .map(Outcome::metrics)
                .filter(metrics -> request.rankingMeasure().value(metrics).compareTo(
                        request.rankingMeasure().value(baseline.metrics())) > 0)
                .count();

        return new IntegratedSocialSecurityCompleteStrategySearchResult(
                baseline,
                request.rankingMeasure(),
                entries,
                rankedEntries,
                rankedEntries.isEmpty() ? OptionalInt.empty() : OptionalInt.of(currentRank));
    }

    private Outcome evaluate(
            RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy,
            int generationOrder,
            ProjectionMetrics baselineMetrics) {
        try {
            IntegratedSocialSecurityStrategyResult result = evaluator.evaluate(plan, strategy);
            ProjectionMetrics differences = ProjectionMetricsDifferenceCalculator.subtract(
                    result.metrics(), baselineMetrics);
            return Outcome.success(generationOrder, strategy, result, differences);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Unable to evaluate the complete Social Security strategy."
                    : exception.getMessage();
            IntegratedSocialSecurityStrategyEvaluationFailure.Category category =
                    exception instanceof IllegalArgumentException
                            ? IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION
                            : IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION;
            return Outcome.failure(
                    generationOrder,
                    strategy,
                    new IntegratedSocialSecurityStrategyEvaluationFailure(
                            strategy, message, category));
        }
    }

    private static void retainDetail(
            PriorityQueue<DetailCandidate> retained,
            DetailCandidate candidate,
            int limit,
            IntegratedStrategyRankingMeasure measure) {
        if (limit == 0) {
            return;
        }
        if (retained.size() < limit) {
            retained.add(candidate);
            return;
        }
        DetailCandidate worst = retained.peek();
        if (bestDetailFirst(measure).compare(candidate, worst) < 0) {
            retained.poll();
            retained.add(candidate);
        }
    }

    private static Comparator<Outcome> rankingComparator(
            IntegratedStrategyRankingMeasure measure) {
        return Comparator.<Outcome, java.math.BigDecimal>comparing(
                        outcome -> measure.value(outcome.metrics()))
                .reversed()
                .thenComparingInt(Outcome::generationOrder);
    }

    private static Comparator<DetailCandidate> bestDetailFirst(
            IntegratedStrategyRankingMeasure measure) {
        return Comparator.<DetailCandidate, java.math.BigDecimal>comparing(
                        candidate -> measure.value(candidate.result().metrics()))
                .reversed()
                .thenComparingInt(DetailCandidate::generationOrder);
    }

    private static Comparator<DetailCandidate> worstDetailFirst(
            IntegratedStrategyRankingMeasure measure) {
        return Comparator.<DetailCandidate, java.math.BigDecimal>comparing(
                        candidate -> measure.value(candidate.result().metrics()))
                .thenComparing(Comparator.comparingInt(
                        DetailCandidate::generationOrder).reversed());
    }

    private static Map<SocialSecurityHouseholdClaimingStrategy, Integer> assignRanks(
            List<Outcome> ranked,
            IntegratedStrategyRankingMeasure measure) {
        Map<SocialSecurityHouseholdClaimingStrategy, Integer> ranks = new HashMap<>();
        java.math.BigDecimal previous = null;
        int rank = 0;
        for (int index = 0; index < ranked.size(); index++) {
            Outcome outcome = ranked.get(index);
            java.math.BigDecimal value = measure.value(outcome.metrics());
            if (previous == null || value.compareTo(previous) != 0) {
                rank = index + 1;
                previous = value;
            }
            ranks.put(outcome.strategy(), rank);
        }
        return ranks;
    }

    private static IntegratedSocialSecurityCompleteStrategySearchEntry entry(
            Outcome outcome,
            Map<SocialSecurityHouseholdClaimingStrategy, Integer> ranks,
            Map<SocialSecurityHouseholdClaimingStrategy,
                    IntegratedSocialSecurityStrategyResult> details) {
        if (!outcome.successful()) {
            return new IntegratedSocialSecurityCompleteStrategySearchEntry(
                    outcome.generationOrder(), outcome.strategy(), OptionalInt.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), outcome.failure());
        }
        return new IntegratedSocialSecurityCompleteStrategySearchEntry(
                outcome.generationOrder(), outcome.strategy(),
                OptionalInt.of(ranks.get(outcome.strategy())),
                Optional.of(outcome.metrics()), Optional.of(outcome.differences()),
                Optional.ofNullable(details.get(outcome.strategy())), Optional.empty());
    }

    private record DetailCandidate(
            int generationOrder,
            SocialSecurityHouseholdClaimingStrategy strategy,
            IntegratedSocialSecurityStrategyResult result) {
    }

    private record Outcome(
            int generationOrder,
            SocialSecurityHouseholdClaimingStrategy strategy,
            ProjectionMetrics metrics,
            ProjectionMetrics differences,
            Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure,
            Optional<DetailCandidate> detailCandidate) {

        static Outcome success(
                int order,
                SocialSecurityHouseholdClaimingStrategy strategy,
                IntegratedSocialSecurityStrategyResult result,
                ProjectionMetrics differences) {
            return new Outcome(order, strategy, result.metrics(), differences,
                    Optional.empty(), Optional.of(new DetailCandidate(order, strategy, result)));
        }

        static Outcome failure(
                int order,
                SocialSecurityHouseholdClaimingStrategy strategy,
                IntegratedSocialSecurityStrategyEvaluationFailure failure) {
            return new Outcome(order, strategy, null, null,
                    Optional.of(failure), Optional.empty());
        }

        boolean successful() {
            return failure.isEmpty();
        }

        Outcome withoutDetail() {
            return detailCandidate.isEmpty()
                    ? this
                    : new Outcome(generationOrder, strategy, metrics, differences,
                            failure, Optional.empty());
        }
    }
}
