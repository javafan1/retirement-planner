package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.AnalyzerLongevityAssumptions;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/** A finished comparison, possibly with explicit failures. Cancellation throws and never creates this result. */
public record LongevityWeightedIntegratedStrategyComparisonResult(
        LongevityWeightedComparisonObjective objective,
        Metadata metadata,
        List<LongevityWeightedIntegratedStrategyComparisonEntry> orderedEntries,
        List<LongevityWeightedIntegratedStrategyComparisonEntry> rankedSuccessfulEntries,
        Optional<LongevityWeightedIntegratedStrategyComparisonEntry> baseline,
        Work work,
        Duration elapsedTime,
        CompletionStatus status,
        Optional<LongevityWeightedStrategyEquivalencePlanner.Plan> equivalencePlan,
        long evaluationsAvoided) {
    public LongevityWeightedIntegratedStrategyComparisonResult {
        Objects.requireNonNull(objective);
        Objects.requireNonNull(metadata);
        orderedEntries = List.copyOf(orderedEntries);
        rankedSuccessfulEntries = List.copyOf(rankedSuccessfulEntries);
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(work);
        Objects.requireNonNull(elapsedTime);
        Objects.requireNonNull(status);
        Objects.requireNonNull(equivalencePlan);
    }

    public LongevityWeightedIntegratedStrategyComparisonResult(
            LongevityWeightedComparisonObjective objective, Metadata metadata,
            List<LongevityWeightedIntegratedStrategyComparisonEntry> orderedEntries,
            List<LongevityWeightedIntegratedStrategyComparisonEntry> rankedSuccessfulEntries,
            Optional<LongevityWeightedIntegratedStrategyComparisonEntry> baseline, Work work,
            Duration elapsedTime, CompletionStatus status) {
        this(objective, metadata, orderedEntries, rankedSuccessfulEntries, baseline, work,
                elapsedTime, status, Optional.empty(), 0);
    }

    /** Details are shared only after a successful complete proof; no re-evaluation is needed. */
    public int detailReevaluations() { return 0; }

    public int inputStrategyCount() { return orderedEntries.size(); }
    /** Successfully evaluated candidates; baseline is reported separately. */
    public int completedStrategyCount() { return rankedSuccessfulEntries.size(); }
    public int failedStrategyCount() { return inputStrategyCount() - completedStrategyCount(); }
    public int processedStrategyCount() { return inputStrategyCount(); }

    /** Includes an unsuccessful requested baseline, whose inputOrder is zero. */
    public List<LongevityWeightedIntegratedStrategyComparisonEntry> failures() {
        return Stream.concat(baseline.stream(), orderedEntries.stream()).filter(entry -> !entry.successful()).toList();
    }

    public long retainedDetailedScenarioOutcomeCount() {
        return Stream.concat(baseline.stream(), orderedEntries.stream())
                .mapToLong(entry -> entry.scenarioDetails().map(List::size).orElse(0)).sum();
    }

    public enum CompletionStatus {
        COMPLETED,
        COMPLETED_WITH_FAILURES
    }

    /** Actual job work, including baseline and failed attempts; never inferred from strategy counts. */
    public record Work(long stageFourEvaluations, long scenarioEvaluations, long completedScenarioEvaluations,
            long projectionEngineRuns, long completedProjectionEngineRuns) { }

    /** Shared once per job. Entries and retained scenario lists contain no mortality/methodology copies. */
    public record Metadata(String methodologyVersion, String methodologyReference,
            AnalyzerLongevityAssumptions longevityAssumptions, LocalDate valuationDate,
            BigDecimal generalInflationRate, BigDecimal realDiscountRate,
            int originalScenarioCount, BigDecimal originalScenarioProbability,
            List<String> financialLimitations) {
        public Metadata {
            Objects.requireNonNull(methodologyVersion);
            Objects.requireNonNull(methodologyReference);
            Objects.requireNonNull(longevityAssumptions);
            Objects.requireNonNull(valuationDate);
            Objects.requireNonNull(generalInflationRate);
            Objects.requireNonNull(realDiscountRate);
            Objects.requireNonNull(originalScenarioProbability);
            financialLimitations = List.copyOf(financialLimitations);
        }
    }
}
