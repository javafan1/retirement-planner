package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import org.junit.jupiter.api.Test;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloFundingTest {
    static RetirementPlan plan(String balance, String expense, int years) {
        var p = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
        p.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1980, 12, 31));
        p.getHousehold().getSpouse().setBirthDate(LocalDate.of(1982, 6, 15));
        p.setPlanningAssumptions(new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, years, LocalDate.of(2027, 1, 1)));
        p.getAccountPortfolio().addAccount(new BrokerageAccount("Cash", AccountOwnership.JOINT, new BigDecimal(balance)));
        p.getHousehold().addExpense(new Expense("Spending", new BigDecimal(expense)));
        return p;
    }

    static ProjectionEconomicPath crash(int year, int last) {
        var map = new TreeMap<Integer, BigDecimal>();
        for (int y = 2027; y <= last; y++) {
            map.put(y, y == year ? BigDecimal.ONE.negate() : BigDecimal.ZERO);
        }
        return ProjectionEconomicPath.annual(map);
    }

    @Test
    void legacyTypeAndMessagePreservedWhileStructuredFailureHasExactAmountsAndPrefix() {
        var plan = plan("250", "100", 4);
        var engine = new ProjectionEngine();
        var exception = assertThrowsExactly(IllegalStateException.class, () -> engine.project(plan));
        assertEquals("Insufficient projected assets to satisfy withdrawal.", exception.getMessage());
        var failed = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, engine.projectWithOutcome(plan));
        assertEquals(ProjectionExecutionResult.Status.INSUFFICIENT_FUNDS, failed.status());
        assertEquals(2, failed.completedYears().size());
        var f = failed.fundingFailure();
        assertEquals(2029, f.calendarYear());
        assertEquals(2, f.projectionYearIndex());
        assertEquals(49, f.primaryAge().orElseThrow());
        assertEquals(47, f.spouseAge().orElseThrow());
        assertMoney("100", f.requiredAmount());
        assertMoney("50", f.availableAmount());
        assertMoney("50", f.shortfallAmount());
        assertEquals(FundingFailure.Stage.WITHDRAWAL_ALLOCATION, f.stage());
        assertThrows(UnsupportedOperationException.class, () -> failed.completedYears().clear());
    }

    @Test
    void completedStructuredProjectionEqualsLegacyAndZeroVolatility() {
        var plan = MonteCarloFixtures.household();
        var engine = new ProjectionEngine();
        var normal = engine.project(plan);
        var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectWithOutcome(plan));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        assertEquals(mapper.valueToTree(normal.getYears()), mapper.valueToTree(completed.completedYears()));
        var r = new MonteCarloAnalyzer().analyze(plan, MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO), normal);
        assertMoney("1", r.fundingProbability());
        assertMoney("0", r.fundingFailureProbability());
        assertTrue(r.fundingFailureStatistics().isEmpty());
        assertMoney(normal.getLastYear().getEndingInvestableAssets().toPlainString(), r.endingInvestableAssets().orElseThrow().p50());
    }

    @Test
    void sevenCompleteThreeFailUseAllTenInDenominatorAndAnnualPrefixes() {
        var p = plan("10000", "100", 10);
        var settings = MonteCarloSettings.forPlan(p, 10, 417, BigDecimal.ZERO);
        var analyzer = new MonteCarloAnalyzer();
        java.util.function.IntFunction<ProjectionEconomicPath> paths = i -> crash(i < 7 ? 2100 : (i == 9 ? 2033 : 2030), 2036);
        var r = analyzer.analyze(p, settings, null, paths);
        assertEquals(7, r.completedSimulationCount());
        assertEquals(3, r.fundingFailureCount());
        assertMoney("0.7", r.fundingProbability());
        assertMoney("0.3", r.fundingFailureProbability());
        assertEquals(7, r.endingInvestableAssets().orElseThrow().sampleCount());
        assertEquals(10, r.annualInvestableAssets().get(2029).orElseThrow().sampleCount());
        assertEquals(8, r.annualInvestableAssets().get(2030).orElseThrow().sampleCount());
        assertEquals(7, r.annualInvestableAssets().get(2033).orElseThrow().sampleCount());
        assertEquals(r, analyzer.analyze(p, settings, null, paths));
        var stats = r.fundingFailureStatistics().orElseThrow();
        assertEquals(Map.of(2030, 2L, 2033, 1L), stats.countByYear());
        assertMoney("0.2", stats.probabilityByYear().get(2030));
        assertMoney("100", stats.shortfallAmount().p50());
    }

    @Test
    void allFailuresRetainEarlierYearsAndFirstYearMedianUsesType7() {
        var p = plan("10000", "100", 10);
        var years = List.of(2030, 2030, 2033, 2035);
        var r = new MonteCarloAnalyzer().analyze(p, MonteCarloSettings.forPlan(p, 4, 1, BigDecimal.ZERO), null, i -> crash(years.get(i), 2036));
        assertEquals(0, r.completedCount());
        assertEquals(4, r.fundingFailureCount());
        assertMoney("0", r.fundingProbability());
        assertMoney("1", r.fundingFailureProbability());
        assertTrue(r.endingInvestableAssets().isEmpty());
        assertTrue(r.endingNetWorth().isEmpty());
        assertTrue(r.endingAfterTaxEstate().isEmpty());
        assertEquals(4, r.annualInvestableAssets().get(2029).orElseThrow().sampleCount());
        assertTrue(r.annualInvestableAssets().get(2035).isEmpty());
        var stats = r.fundingFailureStatistics().orElseThrow();
        assertMoney("2030", stats.firstShortfallYear().minimum());
        assertMoney("2035", stats.firstShortfallYear().maximum());
        assertMoney("2031.5", stats.firstShortfallYear().p50());
        assertMoney("0.5", stats.fractionOfFailuresByYear().get(2030));
    }

    @Test
    void unexpectedExceptionAbortsWithScenarioContextAndOriginalCause() {
        var p = plan("1000", "10", 2);
        var reference = new ProjectionEngine().project(p);
        var original = new ArithmeticException("Injected arithmetic failure");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                if (calls.getAndIncrement() == 1) {
                    throw original;
                }
                return super.projectWithOutcome(plan, context, path);
            }
        };
        var error = assertThrows(MonteCarloExecutionException.class, () ->
                new MonteCarloAnalyzer(engine).analyze(p, MonteCarloSettings.forPlan(p, 4, 417, BigDecimal.ZERO), reference));
        assertEquals(2, calls.get());
        assertEquals(MonteCarloExecutionException.Phase.SIMULATION, error.phase());
        assertEquals(1, error.scenarioIndex().orElseThrow());
        assertEquals(417, error.seed());
        assertEquals(MonteCarloScenarioGenerator.MODEL_VERSION, error.returnModel());
        assertSame(original, error.getCause());
        assertTrue(error.getMessage().contains(ArithmeticException.class.getName()));
        assertTrue(error.getMessage().contains(original.getMessage()));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().projectWithOutcome(p, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.annual(Map.of(2027, BigDecimal.ZERO))));
    }

    @Test
    void taxEstimateFailureIsNotMisrepresentedAsFinalAnnualTaxDeficit() {
        var p = plan("1", "0", 1);
        p.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Taxable pension", AccountOwnership.PRIMARY, LocalDate.of(2027, 1, 1), null, new BigDecimal("10000"), BigDecimal.ZERO));
        p.getHousehold().addExpense(new Expense("Spend all gross income", new BigDecimal("120000")));
        var result = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, new ProjectionEngine().projectWithOutcome(p));
        var f = result.fundingFailure();
        assertEquals(FundingFailure.Stage.WITHDRAWAL_ESTIMATE, f.stage());
        assertMoney("1", f.availableAmount());
        assertMoney(f.requiredAmount().subtract(BigDecimal.ONE).toPlainString(), f.shortfallAmount());
        assertTrue(f.requiredAmount().signum() > 0);
    }

    @Test
    void scenarioFailureBelongsToExecutionAndSeededClassificationsRepeat() {
        var poor = plan("1000", "100", 10);
        var wealthy = plan("100000", "100", 10);
        var path = ProjectionEconomicPath.constant(new BigDecimal("-0.2"));
        var e = new ProjectionEngine();
        assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, e.projectWithOutcome(poor, ProjectionEvaluationContext.empty(), path));
        assertInstanceOf(ProjectionExecutionResult.Completed.class, e.projectWithOutcome(wealthy, ProjectionEvaluationContext.empty(), path));
        var settings = new MonteCarloSettings(30, 417, BigDecimal.ZERO, new BigDecimal("0.3"));
        var analyzer = new MonteCarloAnalyzer();
        var first = analyzer.analyze(poor, settings);
        assertEquals(first, analyzer.analyze(poor, settings));
        assertEquals(30, first.completedCount() + first.fundingFailureCount());
    }

    @Test
    void rmdPoolAndAccountFailuresPreserveDistinctLegacyExceptions() {
        for (boolean employer : List.of(false, true)) {
            var p = plan("10000", "100", 13);
            p.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1963, 6, 4));
            p.getHousehold().getSpouse().setBirthDate(LocalDate.of(1965, 2, 28));
            p.getAccountPortfolio().addAccount(employer
                    ? new Traditional401K("Employer plan", AccountOwnership.PRIMARY, new BigDecimal("1000000"))
                    : new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
            Map<Integer, BigDecimal> values = new TreeMap<>();
            for (int y = 2027; y <= 2039; y++) {
                values.put(y, y == 2038 ? new BigDecimal("-0.99999") : BigDecimal.ZERO);
            }
            var path = ProjectionEconomicPath.annual(values);
            var engine = new ProjectionEngine();
            var legacy = assertThrows(RuntimeException.class, () -> engine.project(p, ProjectionEvaluationContext.empty(), path));
            assertEquals(employer ? IllegalArgumentException.class : IllegalStateException.class, legacy.getClass());
            assertEquals(employer ? "Withdrawal cannot exceed projected account balance." : "Insufficient projected IRA balance to satisfy RMD.", legacy.getMessage());
            var f = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, engine.projectWithOutcome(p, ProjectionEvaluationContext.empty(), path)).fundingFailure();
            assertEquals(employer ? FundingFailure.Stage.ACCOUNT_RMD : FundingFailure.Stage.IRA_RMD, f.stage());
            assertEquals(AccountOwnership.PRIMARY, f.owner().orElseThrow());
            assertEquals(2038, f.calendarYear());
            assertEquals(0, f.requiredAmount().subtract(f.availableAmount()).compareTo(f.shortfallAmount()));
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
