package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloFinalizationTest {

    @Test
    void lifetimeTaxQuantilesIncludeOnlyCompletedRunsAndSumAllYears() {
        var plan = MonteCarloFundingTest.plan("10000", "100", 2);
        var reference = new ProjectionEngine().project(plan);
        var calls = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(
                    RetirementPlan source,
                    ProjectionEvaluationContext context,
                    ProjectionEconomicPath path) {
                int index = calls.getAndIncrement();
                if (index == 2) {
                    return new ProjectionExecutionResult.InsufficientFunds(
                            List.of(yearWithTax(2027, new BigDecimal("9999"))),
                            failure("100"));
                }
                int completedIndex = index < 2 ? index : index - 1;
                BigDecimal annualTax = BigDecimal.valueOf((completedIndex + 1) * 50L);
                return new ProjectionExecutionResult.Completed(List.of(
                        yearWithTax(2027, annualTax),
                        yearWithTax(2028, annualTax)));
            }
        };

        var result = new MonteCarloAnalyzer(engine).analyze(
                plan, MonteCarloSettings.forPlan(plan, 6, 417, BigDecimal.ZERO), reference);

        assertEquals(5, result.completedSimulationCount());
        assertEquals(1, result.fundingFailureCount());
        assertEquals(List.of(new BigDecimal("100"), new BigDecimal("200"),
                        new BigDecimal("300"), new BigDecimal("400"), new BigDecimal("500")),
                result.outcomes().stream().flatMap(outcome -> outcome.metrics().stream())
                        .map(metrics -> metrics.totalTaxes()).toList());
        assertQuantiles(result.lifetimeTaxes().orElseThrow(), 5,
                "100", "140", "200", "300", "400", "460", "500");
        assertEquals(6, result.annualInvestableAssets().get(2027).orElseThrow().sampleCount());
        assertEquals(5, result.annualInvestableAssets().get(2028).orElseThrow().sampleCount());
        assertAccounting(result);
    }

    @Test
    void allFundingFailuresHaveNoLifetimeTaxDistribution() {
        var plan = MonteCarloFundingTest.plan("150", "100", 2);
        var result = new MonteCarloAnalyzer().analyze(
                plan, MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO));

        assertTrue(result.lifetimeTaxes().isEmpty());
        assertTrue(result.deterministicReference().fundingFailure().isPresent());
        assertEquals(1, result.deterministicReference().annualInvestableAssets().size());
        assertEquals(3, result.fundingFailureCount());
        assertAccounting(result);
    }

    @Test
    void pathGenerationErrorAbortsAfterExpectedFundingFailure() {
        var plan = MonteCarloFundingTest.plan("150", "100", 2);
        var original = new IllegalArgumentException("Unsupported caller path");
        var calls = new AtomicInteger();

        var error = assertThrows(MonteCarloExecutionException.class, () ->
                new MonteCarloAnalyzer().analyze(
                        plan, MonteCarloSettings.forPlan(plan, 4, 918, BigDecimal.ZERO), null,
                        index -> {
                            calls.incrementAndGet();
                            if (index == 1) {
                                throw original;
                            }
                            return ProjectionEconomicPath.constant(BigDecimal.ZERO);
                        }));

        assertEquals(2, calls.get());
        assertEquals(1, error.scenarioIndex().orElseThrow());
        assertEquals(918, error.seed());
        assertEquals("CALLER_SUPPLIED_PATHS", error.returnModel());
        assertSame(original, error.getCause());
    }

    @Test
    void unexpectedReferenceErrorAbortsBeforeAnyScenario() {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var original = new IllegalStateException("Unexpected reference failure");
        var paths = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan source) {
                throw original;
            }
        };

        var error = assertThrows(MonteCarloExecutionException.class, () ->
                new MonteCarloAnalyzer(engine).analyze(
                        plan, MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO), null,
                        index -> {
                            paths.incrementAndGet();
                            return ProjectionEconomicPath.constant(BigDecimal.ZERO);
                        }));

        assertEquals(0, paths.get());
        assertEquals(MonteCarloExecutionException.Phase.DETERMINISTIC_REFERENCE, error.phase());
        assertTrue(error.scenarioIndex().isEmpty());
        assertSame(original, error.getCause());
    }

    @Test
    void returnedAnalysesReconcileCountsAndDecimal128Probabilities() {
        var plan = MonteCarloFundingTest.plan("10000", "100", 2);
        var reference = new ProjectionEngine().project(plan);
        for (int count : List.of(1, 3, 7, 10, 19)) {
            for (int failures = 0; failures <= count; failures++) {
                int failedCount = failures;
                var result = new MonteCarloAnalyzer().analyze(
                        plan, MonteCarloSettings.forPlan(plan, count, 417, BigDecimal.ZERO),
                        reference, index -> ProjectionEconomicPath.constant(
                                index < failedCount ? BigDecimal.ONE.negate() : BigDecimal.ZERO));
                assertEquals(failures, result.fundingFailureCount());
                assertAccounting(result);
            }
        }
    }

    @Test
    void unavailableFailureAgesAreEmptyAndShortfallQuantilesUseType7() {
        var failures = List.of(failure("10"), failure("20"), failure("30"),
                failure("40"), failure("50"));
        assertTrue(failures.getFirst().primaryAge().isEmpty());
        assertTrue(failures.getFirst().spouseAge().isEmpty());

        var statistics = FundingFailureStatistics.from(failures, 10).orElseThrow();
        assertQuantiles(statistics.shortfallAmount(), 5,
                "10", "14", "20", "30", "40", "46", "50");
        assertEquals(0, new BigDecimal("0.5").compareTo(statistics.probabilityByYear().get(2028)));
    }

    @Test
    void externallyConstructedResultsRejectMissingDuplicateAndOutOfRangeIndices() {
        var plan = MonteCarloFundingTest.plan("150", "100", 2);
        var result = new MonteCarloAnalyzer().analyze(
                plan, MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> copyWithOutcomes(
                result, result.outcomes().subList(0, 2)));

        var duplicate = new ArrayList<>(result.outcomes());
        duplicate.set(2, duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> copyWithOutcomes(result, duplicate));

        for (int index : List.of(-1, 3)) {
            var invalid = new ArrayList<>(result.outcomes());
            invalid.set(2, new MonteCarloAnalysisResult.RunOutcome(
                    index, Optional.empty(), Optional.empty(), Optional.of(failure("10"))));
            assertThrows(IllegalArgumentException.class, () -> copyWithOutcomes(result, invalid));
        }
    }

    private static MonteCarloAnalysisResult copyWithOutcomes(
            MonteCarloAnalysisResult result,
            List<MonteCarloAnalysisResult.RunOutcome> outcomes) {
        return new MonteCarloAnalysisResult(
                result.settings(), result.returnModel(), outcomes, result.annualInvestableAssets(),
                result.endingInvestableAssets(), result.endingNetWorth(), result.endingAfterTaxEstate(),
                result.lifetimeTaxes(), result.deterministicReference());
    }

    private static FundingFailure failure(String amount) {
        return new FundingFailure(
                2028, 1, Optional.empty(), Optional.empty(), FundingFailure.Stage.WITHDRAWAL_ESTIMATE,
                Optional.empty(), new BigDecimal(amount), BigDecimal.ZERO, new BigDecimal(amount));
    }

    private static ProjectionYear yearWithTax(int year, BigDecimal tax) {
        // Known scalar outcomes test aggregation, without changing any tax calculation rules.
        return new ProjectionYear(
                year - 2026, year, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 47) {
            @Override
            public BigDecimal getTotalIncomeTax() {
                return tax;
            }

            @Override
            public BigDecimal getAnnualMedicarePremium() {
                return BigDecimal.ZERO;
            }
        };
    }

    private static void assertAccounting(MonteCarloAnalysisResult result) {
        assertEquals(result.settings().simulationCount(),
                result.completedSimulationCount() + result.fundingFailureCount());
        assertEquals(0, BigDecimal.ONE.compareTo(
                result.fundingProbability().add(result.fundingFailureProbability(), MathContext.DECIMAL128)));
        assertEquals(0, BigDecimal.valueOf(result.completedSimulationCount())
                .divide(BigDecimal.valueOf(result.settings().simulationCount()), MathContext.DECIMAL128)
                .compareTo(result.fundingProbability()));
        assertEquals(0, BigDecimal.valueOf(result.fundingFailureCount())
                .divide(BigDecimal.valueOf(result.settings().simulationCount()), MathContext.DECIMAL128)
                .compareTo(result.fundingFailureProbability()));
    }

    private static void assertQuantiles(
            MonteCarloPercentiles actual, int count, String... expected) {
        assertEquals(count, actual.sampleCount());
        var values = List.of(actual.minimum(), actual.p10(), actual.p25(), actual.p50(),
                actual.p75(), actual.p90(), actual.maximum());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(0, new BigDecimal(expected[index]).compareTo(values.get(index)));
        }
    }
}
