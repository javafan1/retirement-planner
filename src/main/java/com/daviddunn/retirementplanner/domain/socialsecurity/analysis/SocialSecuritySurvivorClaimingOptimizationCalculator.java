package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Constrained Stage-1 retirement and Stage-2 survivor expected-PV search. */
public final class SocialSecuritySurvivorClaimingOptimizationCalculator {

    enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }

    private final SocialSecurityMortalityWeightedClaimingGridCalculator gridCalculator;
    private final SocialSecuritySurvivorClaimingCandidateGenerator candidateGenerator;
    private final ExecutionMode executionMode;
    private final int threadCount;

    public SocialSecuritySurvivorClaimingOptimizationCalculator() {
        this(
                new SocialSecurityMortalityWeightedClaimingGridCalculator(),
                new SocialSecuritySurvivorClaimingCandidateGenerator(),
                ExecutionMode.PARALLEL,
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8)));
    }

    SocialSecuritySurvivorClaimingOptimizationCalculator(
            SocialSecurityMortalityWeightedClaimingGridCalculator gridCalculator,
            SocialSecuritySurvivorClaimingCandidateGenerator candidateGenerator,
            ExecutionMode executionMode,
            int threadCount) {
        this.gridCalculator = Objects.requireNonNull(gridCalculator);
        this.candidateGenerator = Objects.requireNonNull(candidateGenerator);
        this.executionMode = Objects.requireNonNull(executionMode);
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be positive.");
        }
        this.threadCount = threadCount;
    }

    public SocialSecuritySurvivorClaimingOptimizationResult calculate(
            SocialSecuritySurvivorClaimingOptimizationRequest request) {
        return calculate(request, AnalysisProgressListener.none());
    }

    public SocialSecuritySurvivorClaimingOptimizationResult calculate(
            SocialSecuritySurvivorClaimingOptimizationRequest request,
            AnalysisProgressListener progressListener) {
        return calculate(request, progressListener, AnalysisCancellationToken.none());
    }

    public SocialSecuritySurvivorClaimingOptimizationResult calculate(
            SocialSecuritySurvivorClaimingOptimizationRequest request,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(cancellation, "Cancellation token is required.");
        cancellation.throwIfCancellationRequested();
        Objects.requireNonNull(request, "Survivor optimization request is required.");
        Objects.requireNonNull(progressListener, "Progress listener is required.");
        SocialSecurityMortalityWeightedClaimingGridResult stageOne =
                gridCalculator.calculate(request.retirementGridRequest(), progressListener, cancellation);
        List<SocialSecurityMortalityWeightedClaimingGridCell> retained =
                retainTopWithCutoffTies(
                        stageOne.cells(),
                        request.settings().topRetirementCandidates());
        List<SocialSecuritySurvivorClaimingCandidate> primaryCandidates =
                candidateGenerator.generate(request.retirementGridRequest()
                        .baseStrategy().primaryElection().birthDate());
        List<SocialSecuritySurvivorClaimingCandidate> spouseCandidates =
                candidateGenerator.generate(request.retirementGridRequest()
                        .baseStrategy().spouseElection().birthDate());

        List<CompleteCandidate> completeCandidates = new ArrayList<>();
        for (SocialSecurityMortalityWeightedClaimingGridCell retirement : retained) {
            for (SocialSecuritySurvivorClaimingCandidate primary : primaryCandidates) {
                for (SocialSecuritySurvivorClaimingCandidate spouse : spouseCandidates) {
                    completeCandidates.add(new CompleteCandidate(
                            retirement, primary, spouse));
                }
            }
        }
        ProgressCounter progress = new ProgressCounter(progressListener, completeCandidates.size(), cancellation);
        List<SocialSecuritySurvivorClaimingOptimizationCell> evaluated =
                evaluateCandidates(request, stageOne, completeCandidates, progress);

        BigDecimal maximum = evaluated.stream()
                .map(SocialSecuritySurvivorClaimingOptimizationCell::expectedPresentValue)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        List<SocialSecuritySurvivorClaimingOptimizationCell> winners = evaluated.stream()
                .filter(cell -> cell.expectedPresentValue().compareTo(maximum) == 0)
                .toList();
        int strategyCount = evaluated.size();
        cancellation.throwIfCancellationRequested();
        return new SocialSecuritySurvivorClaimingOptimizationResult(
                request,
                stageOne,
                retained,
                primaryCandidates,
                spouseCandidates,
                evaluated,
                maximum,
                winners,
                strategyCount,
                Math.multiplyExact(
                        strategyCount,
                        stageOne.jointMortalityScenarios().size()));
    }

    private List<SocialSecuritySurvivorClaimingOptimizationCell> evaluateCandidates(
            SocialSecuritySurvivorClaimingOptimizationRequest request,
            SocialSecurityMortalityWeightedClaimingGridResult stageOne,
            List<CompleteCandidate> candidates,
            ProgressCounter progress) {
        if (executionMode == ExecutionMode.SEQUENTIAL || candidates.size() == 1) {
            List<SocialSecuritySurvivorClaimingOptimizationCell> results = new ArrayList<>();
            for (CompleteCandidate candidate : candidates) {
                progress.cancellation.throwIfCancellationRequested();
                results.add(evaluate(request, stageOne, candidate));
                progress.completedOne();
            }
            return List.copyOf(results);
        }
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(threadCount, candidates.size()));
        try {
            List<Future<SocialSecuritySurvivorClaimingOptimizationCell>> futures =
                    candidates.stream()
                            .map(candidate -> executor.submit(
                                    () -> {
                                        progress.cancellation.throwIfCancellationRequested();
                                        return evaluate(request, stageOne, candidate);
                                    }))
                            .toList();
            List<SocialSecuritySurvivorClaimingOptimizationCell> results =
                    new ArrayList<>(futures.size());
            for (Future<SocialSecuritySurvivorClaimingOptimizationCell> future : futures) {
                try {
                    results.add(future.get());
                    progress.completedOne();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    futures.forEach(item -> item.cancel(true));
                    throw new IllegalStateException(
                            "Survivor optimization was interrupted.", exception);
                } catch (ExecutionException exception) {
                    futures.forEach(item -> item.cancel(true));
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException(
                            "Unable to calculate survivor optimization.", cause);
                }
            }
            return List.copyOf(results);
        } finally {
            executor.shutdown();
            executor.close();
        }
    }

    private SocialSecuritySurvivorClaimingOptimizationCell evaluate(
            SocialSecuritySurvivorClaimingOptimizationRequest request,
            SocialSecurityMortalityWeightedClaimingGridResult stageOne,
            CompleteCandidate candidate) {
        SocialSecurityMortalityWeightedClaimingGridCell retirement =
                candidate.retirement();
        try {
            SocialSecurityStrategyRequest retirementStrategy =
                    SocialSecurityClaimingGridCalculator.deriveStrategy(
                            request.retirementGridRequest().baseStrategy(),
                            retirement.primaryClaimDate(),
                            retirement.spouseClaimDate());
            SocialSecurityMortalityWeightedStrategyValue value =
                    new SocialSecurityMortalityWeightedStrategyCalculator().calculate(
                            retirementStrategy,
                            candidate.primary().claimDate(),
                            candidate.spouse().claimDate(),
                            stageOne.jointMortalityScenarios(),
                            request.retirementGridRequest().presentValueBaseDate(),
                            request.retirementGridRequest().realDiscountRate());
            return new SocialSecuritySurvivorClaimingOptimizationCell(
                    new SocialSecurityHouseholdClaimingStrategy(
                            retirement.primaryClaimAge(),
                            retirement.spouseClaimAge(),
                            retirement.primaryClaimDate(),
                            retirement.spouseClaimDate(),
                            candidate.primary(),
                            candidate.spouse()),
                    value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unable to evaluate survivor strategy primaryRetirement="
                            + retirement.primaryClaimAge()
                            + ", spouseRetirement="
                            + retirement.spouseClaimAge()
                            + ", primarySurvivor="
                            + candidate.primary().claimDate()
                            + ", spouseSurvivor="
                            + candidate.spouse().claimDate()
                            + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    static List<SocialSecurityMortalityWeightedClaimingGridCell> retainTopWithCutoffTies(
            List<SocialSecurityMortalityWeightedClaimingGridCell> cells,
            int topCount) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Retirement cells cannot be empty.");
        }
        List<SocialSecurityMortalityWeightedClaimingGridCell> ranked = cells.stream()
                .sorted(Comparator.comparing(
                                SocialSecurityMortalityWeightedClaimingGridCell
                                        ::expectedPresentValue)
                        .reversed())
                .toList();
        int cutoffIndex = Math.min(topCount, ranked.size()) - 1;
        BigDecimal cutoff = ranked.get(cutoffIndex).expectedPresentValue();
        // Return Stage-1 row-major order for stable explainability.
        return cells.stream()
                .filter(cell -> cell.expectedPresentValue().compareTo(cutoff) >= 0)
                .toList();
    }

    private record CompleteCandidate(
            SocialSecurityMortalityWeightedClaimingGridCell retirement,
            SocialSecuritySurvivorClaimingCandidate primary,
            SocialSecuritySurvivorClaimingCandidate spouse) {
    }

    private static final class ProgressCounter {
        private final AnalysisProgressListener listener;
        private final int total;
        private final AnalysisCancellationToken cancellation;
        private int completed;

        private ProgressCounter(AnalysisProgressListener listener, int total, AnalysisCancellationToken cancellation) {
            this.cancellation = cancellation;
            this.listener = listener;
            this.total = total;
            listener.onProgress(new AnalysisProgress(
                    AnalysisPhase.SOCIAL_SECURITY_SURVIVOR_STRATEGIES, 0, total));
        }

        private synchronized void completedOne() {
            cancellation.throwIfCancellationRequested();
            completed++;
            listener.onProgress(new AnalysisProgress(
                    AnalysisPhase.SOCIAL_SECURITY_SURVIVOR_STRATEGIES,
                    completed,
                    total));
        }
    }
}
