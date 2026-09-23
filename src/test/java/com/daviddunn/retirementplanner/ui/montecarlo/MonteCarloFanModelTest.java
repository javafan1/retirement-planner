package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.charts.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloFanModelTest {
    @Test
    void mixedFundingMapsExactPercentilesSamplesAndDeterministicValues() {
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        var reference = new ProjectionEngine().project(plan);
        var result = new MonteCarloAnalyzer().analyze(plan,
                MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO), reference,
                index -> ProjectionEconomicPath.annual(Map.of(
                        2027, BigDecimal.ZERO, 2028, index == 2 ? BigDecimal.ONE.negate() : BigDecimal.ZERO,
                        2029, BigDecimal.ZERO)));
        var model = MonteCarloFanModel.from(result, ProjectionChartModel.empty());
        assertEquals(3, model.requested());
        assertEquals(3, model.years().getFirst().percentiles().orElseThrow().sampleCount());
        assertEquals(2, model.years().getLast().percentiles().orElseThrow().sampleCount());
        for (var point : model.years()) {
            assertSame(result.annualInvestableAssets().get(point.calendarYear()).orElseThrow(),
                    point.percentiles().orElseThrow());
            assertEquals(result.deterministicReference().annualInvestableAssets().get(point.calendarYear()),
                    point.deterministic().orElseThrow());
        }
        assertEquals(1, result.fundingFailureCount());
        assertTrue(MonteCarloPresentation.failureDetail(result).contains("Earliest 2028"));
        assertTrue(MonteCarloPresentation.tooltip(model, model.years().getLast()).contains("2 of 3"));
        assertEquals(4, MonteCarloPresentation.outcomes(result).size());
        assertTrue(MonteCarloPresentation.CONDITIONAL_NOTICE.contains("only simulations"));
    }

    @Test
    void allFailedRetainsEarlyAnnualValuesAndNoFakeTerminalValues() {
        var run = MonteCarloUiFixtures.run("150");
        assertEquals(0, run.result().completedCount());
        assertEquals("0.0%", MonteCarloPresentation.fundingPercent(run.result()));
        assertTrue(run.fan().years().getFirst().percentiles().isPresent());
        assertTrue(run.fan().years().get(1).percentiles().isEmpty());
        assertTrue(run.fan().years().getLast().deterministic().isEmpty());
        assertTrue(MonteCarloPresentation.outcomes(run.result()).stream().allMatch(row -> row.percentiles().isEmpty()));
        assertTrue(run.referenceIncomplete());
        assertTrue(MonteCarloPresentation.failureDetail(run.result()).contains("Median 2028"));
    }

    @Test
    void allCompleteHasConstantSamplesAndNoFailureDetails() {
        var run = MonteCarloUiFixtures.run("1000");
        assertEquals("100.0%", MonteCarloPresentation.fundingPercent(run.result()));
        assertTrue(MonteCarloPresentation.fundingDetail(run.result()).startsWith("All 3"));
        assertEquals("", MonteCarloPresentation.failureDetail(run.result()));
        assertTrue(run.fan().years().stream().allMatch(year -> year.percentiles().orElseThrow().sampleCount() == 3));
    }

    @Test
    void claimingMarkersAndTransactionPeriodsReuseAuthoritativeProjectionModel() {
        var plan = MonteCarloFixtures.household();
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                true, 2027, new BigDecimal("75000"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var reference = new ProjectionEngine().project(plan);
        var expected = ProjectionChartModel.from(reference.getYears(), List.of(),
                BreakEvenPlanSummary.from(plan.getHousehold()), plan.getAccountPortfolio().getAccounts());
        var run = new MonteCarloRunService().run(plan, MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO),
                reference, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(expected.claims(), run.fan().context().claims());
        assertEquals(expected.rothPeriods(), run.fan().context().rothPeriods());
        assertEquals(expected.rmdPeriods(), run.fan().context().rmdPeriods());
        assertEquals(List.of(2027, 2033), run.fan().context().claims().stream().map(ProjectionChartModel.Claim::year).toList());
        assertFalse(expected.rothPeriods().isEmpty());
        assertFalse(expected.rmdPeriods().isEmpty());
        assertTrue(expected.rothPeriods().stream().anyMatch(roth -> expected.rmdPeriods().stream()
                .anyMatch(rmd -> roth.firstYear() <= rmd.lastYear() && rmd.firstYear() <= roth.lastYear())));
        assertEquals(0, reference.getLastYear().getEndingInvestableAssets().compareTo(
                run.fan().years().getLast().deterministic().orElseThrow()));
    }

    @Test
    void fundingDisplayDoesNotRoundSmallPositiveCountsToZeroOrFailuresToOneHundred() {
        var completed = MonteCarloUiFixtures.run("1000").result();
        var failed = MonteCarloUiFixtures.run("150").result();
        for (int failures : new int[]{1, 9999}) {
            var outcomes = new java.util.ArrayList<MonteCarloAnalysisResult.RunOutcome>();
            for (int index = 0; index < 10000; index++) {
                var source = (index < failures ? failed : completed).outcomes().getFirst();
                outcomes.add(new MonteCarloAnalysisResult.RunOutcome(index, source.metrics(),
                        source.firstNonpositiveYearEnd(), source.fundingFailure()));
            }
            var settings = new MonteCarloSettings(10000, 417, BigDecimal.ZERO, BigDecimal.ZERO);
            var result = new MonteCarloAnalysisResult(settings, completed.returnModel(), outcomes,
                    Map.of(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                    java.util.Optional.empty(), completed.deterministicReference());
            assertEquals(failures == 1 ? "99.99%" : "0.01%", MonteCarloPresentation.fundingPercent(result));
        }
    }
}
