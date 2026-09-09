package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Searches whole-year retirement elections under one fixed joint mortality
 * model. Joint scenarios and exact claim dates are request-scoped and reused.
 */
public final class SocialSecurityMortalityWeightedClaimingGridCalculator {

    enum ExecutionMode {
        SEQUENTIAL,
        PARALLEL
    }

    private final ExecutionMode executionMode;
    private final int threadCount;

    public SocialSecurityMortalityWeightedClaimingGridCalculator() {
        this(
                ExecutionMode.PARALLEL,
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8)));
    }

    SocialSecurityMortalityWeightedClaimingGridCalculator(
            ExecutionMode executionMode,
            int threadCount) {
        this.executionMode = Objects.requireNonNull(
                executionMode,
                "Execution mode is required.");
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be positive.");
        }
        this.threadCount = threadCount;
    }

    public SocialSecurityMortalityWeightedClaimingGridResult calculate(
            SocialSecurityMortalityWeightedClaimingGridRequest request) {
        return calculate(request, AnalysisProgressListener.none());
    }

    public SocialSecurityMortalityWeightedClaimingGridResult calculate(
            SocialSecurityMortalityWeightedClaimingGridRequest request,
            AnalysisProgressListener progressListener) {
        return calculate(request, progressListener, AnalysisCancellationToken.none());
    }

    public SocialSecurityMortalityWeightedClaimingGridResult calculate(
            SocialSecurityMortalityWeightedClaimingGridRequest request,
            AnalysisProgressListener progressListener,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(cancellation, "Cancellation token is required.");
        cancellation.throwIfCancellationRequested();
        Objects.requireNonNull(request, "Mortality-weighted grid request is required.");
        Objects.requireNonNull(progressListener, "Progress listener is required.");

        List<SocialSecurityJointMortalityScenario> scenarios =
                HouseholdLongevityScenarioFactory.combine(
                        request.baseStrategy().primaryElection().birthDate(),
                        request.baseStrategy().spouseElection().birthDate(),
                        request.primaryMortality(),
                        request.spouseMortality());
        Map<Integer, LocalDate> primaryDates = claimDates(
                request.baseStrategy().primaryElection().birthDate(),
                request.primaryClaimAges());
        Map<Integer, LocalDate> spouseDates = claimDates(
                request.baseStrategy().spouseElection().birthDate(),
                request.spouseClaimAges());
        List<Coordinate> coordinates = coordinates(request);
        ProgressCounter progress = new ProgressCounter(progressListener, coordinates.size(), cancellation);
        List<SocialSecurityMortalityWeightedClaimingGridCell> cells =
                executionMode == ExecutionMode.SEQUENTIAL || coordinates.size() == 1
                        ? calculateSequential(
                                request,
                                scenarios,
                                primaryDates,
                                spouseDates,
                                coordinates,
                                progress)
                        : calculateParallel(
                                request,
                                scenarios,
                                primaryDates,
                                spouseDates,
                                coordinates,
                                progress);

        cancellation.throwIfCancellationRequested();
        return new SocialSecurityMortalityWeightedClaimingGridResult(
                request,
                request.primaryClaimAges(),
                request.spouseClaimAges(),
                scenarios,
                cells,
                rank(cells, SocialSecurityExpectedValueMeasure.EXPECTED_NOMINAL),
                rank(cells, SocialSecurityExpectedValueMeasure.EXPECTED_REAL),
                rank(cells, SocialSecurityExpectedValueMeasure.EXPECTED_PRESENT_VALUE),
                Math.multiplyExact(cells.size(), scenarios.size()));
    }

    private List<SocialSecurityMortalityWeightedClaimingGridCell> calculateSequential(
            SocialSecurityMortalityWeightedClaimingGridRequest request,
            List<SocialSecurityJointMortalityScenario> scenarios,
            Map<Integer, LocalDate> primaryDates,
            Map<Integer, LocalDate> spouseDates,
            List<Coordinate> coordinates,
            ProgressCounter progress) {
        List<SocialSecurityMortalityWeightedClaimingGridCell> cells = new ArrayList<>();
        for (Coordinate coordinate : coordinates) {
            progress.cancellation.throwIfCancellationRequested();
            cells.add(calculateCell(
                    request, scenarios, primaryDates, spouseDates, coordinate));
            progress.completedOne();
        }
        return List.copyOf(cells);
    }

    private List<SocialSecurityMortalityWeightedClaimingGridCell> calculateParallel(
            SocialSecurityMortalityWeightedClaimingGridRequest request,
            List<SocialSecurityJointMortalityScenario> scenarios,
            Map<Integer, LocalDate> primaryDates,
            Map<Integer, LocalDate> spouseDates,
            List<Coordinate> coordinates,
            ProgressCounter progress) {
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(threadCount, coordinates.size()));
        try {
            List<Future<SocialSecurityMortalityWeightedClaimingGridCell>> futures =
                    coordinates.stream()
                            .map(coordinate -> executor.submit(() -> {
                                progress.cancellation.throwIfCancellationRequested();
                                return calculateCell(
                                    request,
                                    scenarios,
                                    primaryDates,
                                    spouseDates,
                                    coordinate);
                            }))
                            .toList();
            List<SocialSecurityMortalityWeightedClaimingGridCell> cells =
                    new ArrayList<>(futures.size());
            for (Future<SocialSecurityMortalityWeightedClaimingGridCell> future : futures) {
                try {
                    cells.add(future.get());
                    progress.completedOne();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    futures.forEach(item -> item.cancel(true));
                    throw new IllegalStateException(
                            "Mortality-weighted claiming grid calculation was interrupted.",
                            exception);
                } catch (ExecutionException exception) {
                    futures.forEach(item -> item.cancel(true));
                    Throwable cause = exception.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException(
                            "Unable to calculate mortality-weighted claiming grid.",
                            cause);
                }
            }
            return List.copyOf(cells);
        } finally {
            executor.shutdown();
            executor.close();
        }
    }

    private SocialSecurityMortalityWeightedClaimingGridCell calculateCell(
            SocialSecurityMortalityWeightedClaimingGridRequest request,
            List<SocialSecurityJointMortalityScenario> scenarios,
            Map<Integer, LocalDate> primaryDates,
            Map<Integer, LocalDate> spouseDates,
            Coordinate coordinate) {
        LocalDate primaryDate = primaryDates.get(coordinate.primaryAge());
        LocalDate spouseDate = spouseDates.get(coordinate.spouseAge());
        try {
            SocialSecurityStrategyRequest strategy =
                    SocialSecurityClaimingGridCalculator.deriveStrategy(
                            request.baseStrategy(),
                            primaryDate,
                            spouseDate);
            SocialSecurityMortalityWeightedStrategyValue expectedValue =
                    new SocialSecurityMortalityWeightedStrategyCalculator().calculate(
                            strategy,
                            scenarios,
                            request.presentValueBaseDate(),
                            request.realDiscountRate());
            return new SocialSecurityMortalityWeightedClaimingGridCell(
                    coordinate.primaryAge(),
                    coordinate.spouseAge(),
                    primaryDate,
                    spouseDate,
                    expectedValue);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unable to evaluate mortality-weighted claiming strategy primary="
                            + coordinate.primaryAge()
                            + ", spouse="
                            + coordinate.spouseAge()
                            + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    private static Map<Integer, LocalDate> claimDates(
            LocalDate birthDate,
            List<Integer> ages) {
        Map<Integer, LocalDate> dates = new LinkedHashMap<>();
        for (int age : ages) {
            dates.put(age, SocialSecurityRetirementDateCalculator
                    .calculateRetirementClaimDate(birthDate, age));
        }
        return Map.copyOf(dates);
    }

    private static List<Coordinate> coordinates(
            SocialSecurityMortalityWeightedClaimingGridRequest request) {
        List<Coordinate> coordinates = new ArrayList<>();
        for (int primary : request.primaryClaimAges()) {
            for (int spouse : request.spouseClaimAges()) {
                coordinates.add(new Coordinate(primary, spouse));
            }
        }
        return List.copyOf(coordinates);
    }

    private static SocialSecurityMortalityWeightedClaimingGridRanking rank(
            List<SocialSecurityMortalityWeightedClaimingGridCell> cells,
            SocialSecurityExpectedValueMeasure measure) {
        BigDecimal maximum = cells.stream()
                .map(cell -> SocialSecurityMortalityWeightedClaimingGridRanking
                        .value(cell, measure))
                .max(BigDecimal::compareTo)
                .orElseThrow();
        List<SocialSecurityMortalityWeightedClaimingGridCell> ties = cells.stream()
                .filter(cell -> SocialSecurityMortalityWeightedClaimingGridRanking
                        .value(cell, measure).compareTo(maximum) == 0)
                .toList();
        return new SocialSecurityMortalityWeightedClaimingGridRanking(
                measure, maximum, ties);
    }

    private record Coordinate(int primaryAge, int spouseAge) {
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
                    AnalysisPhase.SOCIAL_SECURITY_RETIREMENT_GRID, 0, total));
        }

        private synchronized void completedOne() {
            cancellation.throwIfCancellationRequested();
            completed++;
            listener.onProgress(new AnalysisProgress(
                    AnalysisPhase.SOCIAL_SECURITY_RETIREMENT_GRID, completed, total));
        }
    }
}
