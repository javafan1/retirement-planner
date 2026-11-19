package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFingerprintService;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshotMapping;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisVersions;
import com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisProvenance;
import com.daviddunn.retirementplanner.app.socialsecurity.results.SnapshotContext;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.*;
import static com.daviddunn.retirementplanner.ui.controller.ApplicationController.ControlledProjectionStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasControlledWorkflowTest {
    @TempDir Path folder;
    private ApplicationController controller;

    private ApplicationController controller() {
        controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("analyses"))));
        return controller;
    }

    @AfterEach void close() throws Exception {
        if (controller == null) return;
        assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
        controller.getAnalysisParameters().shutdown();
        assertTrue(controller.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit());
        controller.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }

    @Test void ordinaryEngineCannotAcquireTheControlledCapability() throws Exception {
        var c = controller();
        c.newPlan(surplusPlan("1000000", 1));
        var run = c.beginControlledArkansasProjection(new ProjectionEngine(), year -> fail("Unexpected progress"));
        awaitFailure(run);
        assertNull(c.getAcceptedProjectionSnapshot());
        var failure = assertThrows(java.util.concurrent.CompletionException.class, () -> c.finishControlledArkansasProjection(run));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test void cancellationBeforeExecutionAndDuringAnnualWorkPreservesAcceptedHistoryAcrossSaveAs() throws Exception {
        var c = controller();
        c.newPlan(surplusPlan("1000000", 2));
        var original = accept(c);
        c.saveAs(folder.resolve("original.json"));
        var queue = new Checkpoint();
        var held = c.getAnalysisResults().background(() -> { queue.block(); return true; });
        queue.awaitEntered();
        var queued = c.beginControlledArkansasProjection(validationEngine(), year -> fail("Cancelled before the first year"));
        queued.cancel();
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(queued));
        queue.release();
        held.toCompletableFuture().get(15, TimeUnit.SECONDS);
        awaitFailure(queued);

        var yearly = new Checkpoint();
        Thread acceptingThread = Thread.currentThread();
        var running = c.beginControlledArkansasProjection(validationEngine(), completed -> {
            if (completed == 1) yearly.block();
        });
        yearly.awaitEntered();
        assertNotSame(acceptingThread, yearly.worker);
        running.cancel();
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(running));
        c.saveAs(folder.resolve("cancelled-copy.json"));
        yearly.release();
        awaitFailure(running);
        var previous = c.getAcceptedProjectionSnapshot();
        assertEquals(original.id(), previous.id());
        assertEquals(original.annual(), previous.annual());
        assertNotEquals(original.owner(), previous.owner());
        assertNotEquals(original.id(), accept(c).id());
    }

    @Test void editsSupersessionAndPlanReplacementCannotCommitLateResults() throws Exception {
        var c = controller();
        var plan = surplusPlan("1000000", 2);
        c.newPlan(plan);
        var original = accept(c);
        var firstYear = new Checkpoint();
        var older = c.beginControlledArkansasProjection(validationEngine(), completed -> {
            if (completed == 1) firstYear.block();
        });
        firstYear.awaitEntered();
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1100000"));
        firstYear.release();
        awaitWork(older);
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(older));
        assertEquals(original.id(), c.getAcceptedProjectionSnapshot().id());

        var blocker = new Checkpoint();
        var first = c.beginControlledArkansasProjection(validationEngine(), completed -> {
            if (completed == 1) blocker.block();
        });
        blocker.awaitEntered();
        var second = c.beginControlledArkansasProjection(validationEngine(), completed -> { });
        blocker.release();
        awaitFailure(first);
        awaitWork(second);
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(first));
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(second));
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(second));
        assertTrue(c.getAcceptedProjectionSnapshot().matches(plan));

        var replaced = c.beginControlledArkansasProjection(validationEngine(), completed -> { });
        awaitWork(replaced);
        c.newPlan(surplusPlan("2000000", 1));
        assertEquals(OBSOLETE, c.finishControlledArkansasProjection(replaced));
        assertNull(c.getAcceptedProjectionSnapshot());
    }

    @Test void failureReturnsOnlyStableInputTargetsAndKeepsPriorAcceptedTax() throws Exception {
        var c = controller();
        var opening = LocalDate.of(2026, 1, 1);
        var plan = single(LocalDate.of(1965, 7, 1), opening, 1, TAXABLE_FIRST, "500000");
        var pension = new Pension("Company pension", PRIMARY, opening, null, new BigDecimal("5000"), BigDecimal.ZERO);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        c.newPlan(plan);
        var original = accept(c);
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.UNKNOWN);
        c.markModified();
        var run = c.beginControlledArkansasProjection(validationEngine(), completed -> { });
        awaitFailure(run);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> c.finishControlledArkansasProjection(run));
        assertEquals(ArkansasTaxIssue.Kind.PENSION_ORIGIN, failure.issue().kind());
        assertEquals(2026, failure.year());
        assertEquals(original.id(), c.getAcceptedProjectionSnapshot().id());
        assertEquals(original.annual(), c.getAcceptedProjectionSnapshot().annual());
        assertTrue(c.getCurrentFundingFailure().isEmpty());
        var target = failure.pensionsToReview().getFirst();
        assertSame(pension, c.resolveArkansasPensionReview(failure, target).orElseThrow());
        assertSame(pension, c.resolveArkansasPensionReview(failure, target).orElseThrow());
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        assertTrue(c.resolveArkansasPensionReview(failure, target).isEmpty());
        assertEquals(original.annual(), accept(c).annual());
    }

    @Test void provedShortageDoesNotAcceptItsPrefix() throws Exception {
        var c = controller();
        c.newPlan(surplusPlan("103170.99", 1));
        var run = c.beginControlledArkansasProjection(validationEngine(), completed -> { });
        awaitWork(run);
        assertEquals(INSUFFICIENT_FUNDS, c.finishControlledArkansasProjection(run));
        assertEquals(2026, c.getCurrentFundingFailure().orElseThrow().calendarYear());
        assertNull(c.getAcceptedProjectionSnapshot());
        assertNull(c.peekCurrentProjection());
    }

    @Test void genuineProjectionStartedOnFxLeavesItResponsiveForCancellation() throws Exception {
        var c = controller();
        c.newPlan(surplusPlan("1000000", 2));
        var initial = new java.util.concurrent.FutureTask<Void>(() -> { javafx.application.Platform.setImplicitExit(false); return null; });
        try {
            javafx.application.Platform.startup(initial);
        } catch (IllegalStateException running) {
            javafx.application.Platform.runLater(initial);
        }
        initial.get(15, TimeUnit.SECONDS);
        var boundary = new Checkpoint();
        var operation = new java.util.concurrent.FutureTask<>(() ->
                c.beginControlledArkansasProjection(validationEngine(), completed -> {
                    if (completed == 1) boundary.block();
                }));
        javafx.application.Platform.runLater(operation);
        var attempt = operation.get(15, TimeUnit.SECONDS);
        try {
            boundary.awaitEntered();
            long beforeCancel = System.nanoTime();
            var cancel = new java.util.concurrent.FutureTask<>(() -> {
                assertNotSame(Thread.currentThread(), boundary.worker);
                attempt.cancel();
                return c.finishControlledArkansasProjection(attempt);
            });
            javafx.application.Platform.runLater(cancel);
            assertEquals(OBSOLETE, cancel.get(15, TimeUnit.SECONDS));
            System.out.println("Arkansas controlled FX cancel and revoke us=" + (System.nanoTime() - beforeCancel) / 1000
                    + " worker=" + boundary.worker.getName());
        } finally {
            boundary.release();
        }
        awaitFailure(attempt);
        assertNull(c.getAcceptedProjectionSnapshot());
    }

    @Test void associatesObservedAnalysisOnlyWithTheExactControlledProjectionAndRetainsItOnCancellation() throws Exception {
        var c = controller();
        c.newPlan(surplusPlan("1000000", 1));
        c.saveAs(folder.resolve("associated.json"));
        var projection = accept(c);
        var engine = validationEngine();
        var frozen = new RetirementPlanScenarioCopyService().copy(c.getCurrentPlan());
        var settings = new MonteCarloSettings(2, 42L, BigDecimal.ZERO, BigDecimal.ZERO);
        var kind = AnalysisKind.INDIVIDUAL_MONTE_CARLO_FIXED;
        var start = Instant.parse("2026-11-19T12:00:00Z");
        var finish = start.plusSeconds(1);
        long started = System.nanoTime();
        var captured = ArkansasAnalysisProvenance.capture(engine, kind, List.of(projection), () -> {
            var run = new MonteCarloAnalyzer(engine).analyze(frozen, settings, c.peekCurrentProjection(),
                    i -> ProjectionEconomicPath.constant(new BigDecimal(i == 0 ? ".04" : "-.02")));
            return MonteCarloSnapshotMapping.fixed(run, frozen,
                    new MonteCarloSnapshot.Display(com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(frozen),
                            "Primary", null, List.of(),
                            new MonteCarloSnapshot.Chart(List.of(), List.of(), List.of(), List.of()), 2026, 2026, false, 0, "Two matched paths."),
                    start, finish, "a".repeat(64));
        });
        System.out.println("Arkansas controlled two-path analysis and provenance us=" + (System.nanoTime() - started) / 1000);
        var payload = (MonteCarloSnapshot) captured.payload();
        var fingerprints = new MonteCarloFingerprintService().storedFingerprint(payload);
        assertNull(c.prepareAnalyzerAssociation(kind, fingerprints.fingerprint()));
        var admission = c.prepareControlledArkansasAnalyzerAssociation(kind, fingerprints.fingerprint());
        assertNotNull(admission);
        var completion = new AcceptedAnalysisCompletion(c.getAnalysisSessionId(), c.getCurrentFile(), kind,
                admission.ticket(), finish, fingerprints.fingerprint(), fingerprints.contextFingerprint(),
                AnalysisVersions.calculatedAlgorithms(kind, payload), AnalysisVersions.references(kind), payload,
                new SnapshotContext("Single", "Primary", null, start, List.of(), List.of(), List.of(), Map.of()))
                .withArkansasExecution(captured);
        assertFalse(c.associateAnalyzer(admission, completion));
        assertFalse(c.associateControlledArkansasAnalyzer(admission, completion.forRun(UUID.randomUUID())));
        assertTrue(c.associateControlledArkansasAnalyzer(admission, completion));
        var association = c.getAnalyzerAssociations(projection.id()).getFirst();
        assertEquals(projection.id(), association.projectionId());
        assertEquals(projection.inputDigest(), association.projectionInputDigest());

        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloAnalyzer(engine).analyze(frozen, settings,
                c.peekCurrentProjection(), i -> ProjectionEconomicPath.constant(BigDecimal.ZERO),
                progress -> { if (progress.completedWork() == 1) cancelled.set(true); }, cancelled::get));
        assertEquals(List.of(association), c.getAnalyzerAssociations(projection.id()));
        c.save();
        var saved = c.getAnalyzerAssociations(projection.id()).getFirst();
        var restored = c.loadAssociatedAnalyzer(saved).toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(completion.runId(), restored.runId());
        assertEquals(payload, restored.payload());
        restored.arkansasExecution().validateDependency(projection);
    }

    private static CompleteProjectionSnapshot accept(ApplicationController c) throws Exception {
        var run = c.beginControlledArkansasProjection(validationEngine(), completed -> { });
        awaitWork(run);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(run));
        var result = c.getAcceptedProjectionSnapshot();
        assertTrue(result.arkansasHistory());
        assertEquals(result.id(), c.getCurrentPlan().getProjectionState().current().id());
        assertTrue(result.matches(c.getCurrentPlan()));
        return result;
    }

    private static void awaitWork(ApplicationController.ControlledProjectionRun run) throws Exception {
        run.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static void awaitFailure(ApplicationController.ControlledProjectionRun run) {
        assertThrows(ExecutionException.class, () -> awaitWork(run));
    }

    private static final class Checkpoint {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        volatile Thread worker;

        void block() {
            worker = Thread.currentThread();
            entered.countDown();
            await(released);
        }
        void awaitEntered() { await(entered); }
        void release() { released.countDown(); }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Checkpoint timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
