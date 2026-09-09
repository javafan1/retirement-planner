package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/**
 * Sequentially compares a small caller-selected strategy set through the full
 * deterministic plan. This service performs no search, ranking, or optimization.
 */
public final class IntegratedSocialSecurityStrategyComparisonService {

    private final IntegratedSocialSecurityStrategyEvaluator evaluator;

    public IntegratedSocialSecurityStrategyComparisonService() {
        this(new IntegratedSocialSecurityStrategyEvaluator());
    }

    IntegratedSocialSecurityStrategyComparisonService(
            IntegratedSocialSecurityStrategyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public IntegratedSocialSecurityStrategyComparisonResult compare(
            RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates) {
        return compare(plan, candidates, AnalysisProgressListener.none());
    }

    public IntegratedSocialSecurityStrategyComparisonResult compare(
            RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates,
            AnalysisProgressListener progressListener) {
        return compare(plan, candidates, progressListener, AnalysisCancellationToken.none());
    }

    public IntegratedSocialSecurityStrategyComparisonResult compare(
            RetirementPlan plan,
            List<SocialSecurityHouseholdClaimingStrategy> candidates,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        List<SocialSecurityHouseholdClaimingStrategy> supplied = List.copyOf(
                Objects.requireNonNull(candidates, "Candidate strategies are required."));
        return compareInputs(plan, supplied.stream()
                .map(strategy -> new CandidateInput(strategy, Optional.empty()))
                .toList(), progressListener, cancellation);
    }

    /** Retains the analyzer's existing expected-value object without recalculation. */
    public IntegratedSocialSecurityStrategyComparisonResult compareAnalyzerCandidates(
            RetirementPlan plan,
            List<SocialSecuritySurvivorClaimingOptimizationCell> candidates) {
        return compareAnalyzerCandidates(plan, candidates, AnalysisProgressListener.none());
    }

    public IntegratedSocialSecurityStrategyComparisonResult compareAnalyzerCandidates(
            RetirementPlan plan,
            List<SocialSecuritySurvivorClaimingOptimizationCell> candidates,
            AnalysisProgressListener progressListener) {
        return compareAnalyzerCandidates(plan, candidates, progressListener, AnalysisCancellationToken.none());
    }

    public IntegratedSocialSecurityStrategyComparisonResult compareAnalyzerCandidates(
            RetirementPlan plan,
            List<SocialSecuritySurvivorClaimingOptimizationCell> candidates,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(candidates, "Analyzer candidates are required.");
        return compareInputs(plan, List.copyOf(candidates).stream()
                .map(cell -> new CandidateInput(cell.strategy(),
                        Optional.of(cell.expectedValue())))
                .toList(), progressListener, cancellation);
    }

    private IntegratedSocialSecurityStrategyComparisonResult compareInputs(
            RetirementPlan plan,
            List<CandidateInput> supplied,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Objects.requireNonNull(progressListener, "Progress listener is required.");

        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.CURRENT_PLAN_BASELINE, 0, 1));
        cancellation.throwIfCancellationRequested();
        IntegratedSocialSecurityStrategyResult baseline =
                evaluator.evaluateCurrentStrategy(plan);
        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.CURRENT_PLAN_BASELINE, 1, 1));
        cancellation.throwIfCancellationRequested();
        Set<SocialSecurityHouseholdClaimingStrategy> seen = new LinkedHashSet<>();
        List<IntegratedSocialSecurityStrategyComparisonEntry> entries = new ArrayList<>();
        int uniqueCount = (int) supplied.stream()
                .map(CandidateInput::strategy)
                .distinct()
                .count();
        int completed = 0;
        progressListener.onProgress(new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 0, uniqueCount));

        for (int index = 0; index < supplied.size(); index++) {
            CandidateInput input = Objects.requireNonNull(
                    supplied.get(index), "Candidate at index " + index + " is required.");
            SocialSecurityHouseholdClaimingStrategy strategy = input.strategy();
            if (!seen.add(strategy)) {
                continue;
            }
            cancellation.throwIfCancellationRequested();
            int callerOrder = index + 1;
            try {
                IntegratedSocialSecurityStrategyResult result = evaluator.evaluate(plan, strategy);
                entries.add(IntegratedSocialSecurityStrategyComparisonEntry.success(
                        callerOrder, strategy, input.expectedValue(), result,
                        ProjectionMetricsDifferenceCalculator.subtract(
                                result.metrics(), baseline.metrics())));
            } catch (AnalysisCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                        || exception.getMessage().isBlank()
                        ? "Unable to evaluate the supplied Social Security strategy."
                        : exception.getMessage();
                IntegratedSocialSecurityStrategyEvaluationFailure.Category category =
                        exception instanceof IllegalArgumentException
                                ? IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION
                                : IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION;
                entries.add(IntegratedSocialSecurityStrategyComparisonEntry.failure(
                        callerOrder, strategy, input.expectedValue(),
                        new IntegratedSocialSecurityStrategyEvaluationFailure(
                                strategy, message, category)));
            }
            completed++;
            progressListener.onProgress(new AnalysisProgress(
                    AnalysisPhase.QUICK_COMPARISON_CANDIDATES,
                    completed,
                    uniqueCount));
        }
        cancellation.throwIfCancellationRequested();
        return new IntegratedSocialSecurityStrategyComparisonResult(
                baseline, supplied.size(), entries);
    }

    private record CandidateInput(
            SocialSecurityHouseholdClaimingStrategy strategy,
            Optional<SocialSecurityMortalityWeightedStrategyValue> expectedValue) {
        private CandidateInput {
            Objects.requireNonNull(strategy, "Candidate strategy is required.");
            Objects.requireNonNull(expectedValue, "Expected-value context is required.");
        }
    }
}
