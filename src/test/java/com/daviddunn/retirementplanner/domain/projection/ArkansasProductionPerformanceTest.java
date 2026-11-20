package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.couple;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.selectArkansas;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasProductionPerformanceTest {
    @Test void jointRetirementWithRmdAndTargetedRothRunsUnderMarketVariation() {
        var plan = representativePlan();

        var engine = ProjectionEngine.forArkansasProduction();
        var cancellation = AnalysisCancellationToken.none();
        var times = new ArrayList<Long>();
        ProjectionExecutionResult reference = null;
        for (int sample = 0; sample < 10; sample++) {
            long began = System.nanoTime();
            reference = engine.projectArkansasWithOutcome(plan, cancellation, ignored -> { });
            times.add(System.nanoTime() - began);
            assertInstanceOf(ProjectionExecutionResult.Completed.class, reference);
        }
        var accepted = reference;
        assertEquals(25, accepted.completedYears().size());
        assertTrue(accepted.completedYears().stream().anyMatch(year -> year.arkansasValidationTax().orElseThrow().targetMetrics().isPresent()));
        times.sort(Long::compareTo);
        System.out.printf("Arkansas 25y deterministic n=10 median-ms=%.2f p95-ms=%.2f min-ms=%.2f%n",
                (times.get(4) + times.get(5)) / 2_000_000.0, times.getLast() / 1_000_000.0, times.getFirst() / 1_000_000.0);

        var before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long began = System.nanoTime();
        var result = engine.runArkansasAnalysis(cancellation, () -> new MonteCarloAnalyzer(engine).analyzeWithReferenceOutcome(plan,
                new MonteCarloSettings(64, 417, new BigDecimal("0.045"), new BigDecimal("0.10")),
                accepted, AnalysisProgressListener.none(), cancellation));
        var elapsed = System.nanoTime() - began;
        var after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        assertEquals(64, result.settings().simulationCount());
        System.out.printf("Arkansas 25y fixed Monte Carlo n=64 elapsed-ms=%.2f funding-failures=%d proof-exhaustions=0 heap-change-MiB=%.2f%n",
                elapsed / 1_000_000.0, result.fundingFailureCount(), (after - before) / 1048576.0);
    }

    @Test void retainedHistorySharesTheBoundedQueueWhileTheStochasticWorkerRuns(@TempDir Path folder) throws Exception {
        var store = new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore(
                new com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository(folder.resolve("analysis")));
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(
                new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(),
                new com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository(folder.resolve("settings.json")), store);
        var plan = representativePlan();
        controller.newPlan(plan);
        var release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var initial = controller.beginArkansasProjection(ignored -> { });
            initial.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(com.daviddunn.retirementplanner.ui.controller.ApplicationController.ControlledProjectionStatus.ACCEPTED,
                    controller.finishControlledArkansasProjection(initial));
            var file = folder.resolve("representative.json");
            controller.saveAs(file);
            controller.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            var current = controller.getAcceptedProjectionSnapshot();
            var manifest = controller.getCurrentPlan().getProjectionState();
            var detached = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
            var reference = new ProjectionExecutionResult.Completed(controller.peekCurrentProjection().getYears());
            var engine = ProjectionEngine.forArkansasProduction();
            var started = new CountDownLatch(1);
            var analysis = worker.submit(() -> {
                started.countDown();
                var cancellation = AnalysisCancellationToken.none();
                return engine.runArkansasAnalysis(cancellation, () -> new MonteCarloAnalyzer(engine).analyzeWithReferenceOutcome(detached,
                        new MonteCarloSettings(64, 1237, new BigDecimal("0.045"), new BigDecimal("0.10")), reference,
                        AnalysisProgressListener.none(), cancellation));
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            long submitted = System.nanoTime();
            var historyReads = new ArrayList<java.util.concurrent.CompletionStage<?>>();
            var components = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
            var overlappingReads = new java.util.concurrent.atomic.AtomicInteger();
            for (int index = 0; index < 6; index++) {
                historyReads.add(store.background(() -> {
                    var restored = components.load(file, manifest).get(current.id());
                    if (!analysis.isDone()) overlappingReads.incrementAndGet();
                    return restored;
                }));
            }
            var entered = new CountDownLatch(1);
            var firstYear = new AtomicLong();
            var request = controller.beginArkansasProjection(year -> {
                if (year != 1) return;
                firstYear.set(System.nanoTime());
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Cancellation probe timed out.");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            });
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            for (var read : historyReads) assertNotNull(read.toCompletableFuture().get(15, TimeUnit.SECONDS));
            var waitingRead = store.background(() -> components.load(file, manifest).get(current.id()));
            assertFalse(waitingRead.toCompletableFuture().isDone());
            long cancelStarted = System.nanoTime();
            request.cancel();
            assertEquals(com.daviddunn.retirementplanner.ui.controller.ApplicationController.ControlledProjectionStatus.OBSOLETE,
                    controller.finishControlledArkansasProjection(request));
            long revoked = System.nanoTime();
            release.countDown();
            var cancelled = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> request.completion().toCompletableFuture().get(15, TimeUnit.SECONDS));
            assertInstanceOf(com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException.class, cancelled.getCause());
            long stopped = System.nanoTime();
            assertEquals(current.id(), controller.getAcceptedProjectionSnapshot().id());
            assertNotNull(waitingRead.toCompletableFuture().get(15, TimeUnit.SECONDS));
            assertEquals(64, analysis.get(30, TimeUnit.SECONDS).settings().simulationCount());
            System.out.printf("Arkansas queue: 6 retained reads (%d overlapping 64 worlds), queue+first-year-ms=%.2f owner-revoke-ms=%.3f worker-stopped-ms=%.3f%n",
                    overlappingReads.get(), (firstYear.get() - submitted) / 1_000_000.0,
                    (revoked - cancelStarted) / 1_000_000.0, (stopped - cancelStarted) / 1_000_000.0);
        } finally {
            release.countDown();
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
            controller.getSettingsService().close();
        }
    }

    private static RetirementPlan representativePlan() {
        var start = LocalDate.of(2026, 1, 1);
        var plan = selectArkansas(couple(start, 25, DeathScenario.BOTH_SURVIVE));
        var initial = plan.getPlanningAssumptions();
        var economy = new PlanningAssumptions(new BigDecimal("0.045"), new BigDecimal("0.02"), 25, start);
        plan.setPlanningAssumptions(new PlanningAssumptions(economy.getEconomicAssumptions(), initial.getTaxAssumptions(),
                initial.getWithdrawalAssumptions(), initial.getDeathScenarioAssumptions(), 25, start, initial.getStateTaxAssumptions()));
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1956, 1, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1959, 1, 1));
        for (var owner : java.util.List.of(PRIMARY, SPOUSE)) {
            var pension = new Pension(owner + " pension", owner, start, null, new BigDecimal("4000"), new BigDecimal("0.02"));
            pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            plan.getHousehold().peopleByOwner().get(owner).addIncomeSource(pension);
        }
        for (var account : plan.getAccountPortfolio().getAccounts()) account.setCurrentBalance(new BigDecimal("650000"));
        plan.getAccountPortfolio().addAccount(new RothIRA("Primary Roth", PRIMARY, new BigDecimal("90000")));
        plan.getHousehold().addExpense(new Expense("Annual household spending", new BigDecimal("76000"),
                GrowthCategory.GENERAL, start, null, ExpenseType.RECURRING));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ONE_TIME));
        return plan;
    }
}
