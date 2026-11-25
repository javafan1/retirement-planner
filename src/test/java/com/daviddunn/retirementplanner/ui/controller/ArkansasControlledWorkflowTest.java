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
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;
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

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
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

    @Test void lowIncomeDiagnosticOnTheControlledWorkerPreservesTheAcceptedProjection() throws Exception {
        var c = controller();
        var date = LocalDate.of(2028, 1, 1);
        var plan = com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest
                .fixture(date, 1, DeathScenario.PRIMARY_DIES, 2028, "0");
        var pension = new Pension("Synthetic employer pension", PRIMARY, date, null, new BigDecimal("5000"),
                BigDecimal.ZERO, new BigDecimal("5000"));
        pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        plan.setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest
                .policy(PersonReference.PRIMARY, "100000", null, null)));
        c.newPlan(plan);
        var accepted = c.beginArkansasProjection(ignored -> { });
        awaitWork(accepted);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(accepted));
        var before = c.getAcceptedProjectionSnapshot();

        var smaller = new Pension("Synthetic employer pension", PRIMARY, date, null, new BigDecimal("5000"),
                BigDecimal.ZERO, new BigDecimal("2500"));
        smaller.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        plan.getHousehold().getPrimaryPerson().setIncomeSources(List.of(smaller));
        c.markModified();
        var traces = new java.util.ArrayList<com.daviddunn.retirementplanner.domain.projection.ArkansasLowIncomeDiagnostic>();
        var localFile = ArkansasLowIncomeDiagnosticFile.forRun();
        var ownerThread = Thread.currentThread();
        var failed = c.beginControlledArkansasProjection(ProjectionEngine.forArkansasProduction(),
                count -> fail("No completed year"), java.util.Optional.of(trace -> {
                    assertNotSame(ownerThread, Thread.currentThread());
                    traces.add(trace);
                    localFile.ifPresent(sink -> sink.accept(trace));
                }));
        awaitFailure(failed);
        var refusal = assertThrows(ArkansasProjectionUnavailableException.class, () -> c.finishControlledArkansasProjection(failed));
        assertEquals(ArkansasTaxIssue.Kind.LOW_INCOME, refusal.issue().kind());
        assertEquals(1, traces.size());
        assertTrue(traces.getFirst().lifePrincipalRecognized());
        assertSame(before, c.getAcceptedProjectionSnapshot());
        assertTrue(c.getCurrentFundingFailure().isEmpty());
    }

    @Test
    void scopedBenefitSnapshotsSurviveSaveAsOpenSwapAndAnUnsupportedAttempt() throws Exception {
        var c = controller();
        var date = LocalDate.of(2026, 1, 1);
        var plan = com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest
                .fixture(date, 2, DeathScenario.PRIMARY_DIES, 2026, "0");
        var original = com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest
                .policy(PersonReference.PRIMARY, "100000", null, null);
        plan.setConditionalBenefits(List.of(original));
        c.newPlan(plan);
        var run = c.beginArkansasProjection(ignored -> { });
        awaitWork(run);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(run));
        var first = c.getAcceptedProjectionSnapshot();
        assertTrue(first.calculationSemanticsCurrent());
        assertTrue(first.annual().arkansas().hasLifePrincipalPolicy());
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class, c::requireArkansasProjectionForAnalysis);
        assertNull(c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_MONTE_CARLO_FIXED, "test fingerprint"));
        c.saveAs(folder.resolve("life-original.json"));
        c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
        c.saveCurrentAsBaseline("Original life projection");

        var revised = com.daviddunn.retirementplanner.domain.projection.ArkansasLifePrincipalProjectionTest
                .policy(PersonReference.PRIMARY, "90000", null, null);
        c.getCurrentPlan().setConditionalBenefits(List.of(revised));
        c.markModified();
        var rerun = c.beginArkansasProjection(ignored -> { });
        awaitWork(rerun);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(rerun));
        var second = c.getAcceptedProjectionSnapshot();
        c.save();
        var copy = folder.resolve("life-copy.json");
        c.saveAs(copy);
        c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
        c.open(copy);
        c.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertOriginalAnnualEvidence(second, c.getAcceptedProjectionSnapshot());
        assertEquals(List.of(revised), c.getCurrentPlan().getConditionalBenefits());

        assertTrue(c.commitProjectionSwap(c.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        var reopened = c.getAcceptedProjectionSnapshot();
        assertOriginalAnnualEvidence(first, reopened);
        assertEquals(List.of(original), c.getCurrentPlan().getConditionalBenefits());
        assertEquals(second.id(), c.getCurrentPlan().getProjectionState().baseline().id());
        assertTrue(reopened.annual().arkansas().hasLifePrincipalPolicy());

        c.getCurrentPlan().setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        c.markModified();
        var refused = c.beginArkansasProjection(ignored -> fail("Unsupported Advanced inputs produced an annual result."));
        awaitFailure(refused);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class,
                () -> c.finishControlledArkansasProjection(refused));
        assertEquals(ArkansasTaxIssue.Kind.NIIT, failure.issue().kind());
        assertSame(reopened, c.getAcceptedProjectionSnapshot());
    }

    private static void assertOriginalAnnualEvidence(CompleteProjectionSnapshot original, CompleteProjectionSnapshot read)
            throws Exception {
        assertEquals(original.id(), read.id());
        assertEquals(original.inputDigest(), read.inputDigest());
        assertEquals(original.inputsDocument(), read.inputsDocument());
        assertEquals(original.provenance(), read.provenance());
        assertEquals(ArkansasValidationTax.LIFE_PRINCIPAL_POLICY,
                read.annual().arkansas().versions().get(ArkansasValidationTax.LIFE_PRINCIPAL_POLICY_KEY));
        var codec = new RetainedProjectionCodec();
        assertArrayEquals(codec.encode(original.annual()).bytes(), codec.encode(read.annual()).bytes());
    }

    @Test void regularTaxPolicyAndDisclosureBelongToTheFrozenRoles() throws Exception {
        var c = controller();
        var strictPolicy = com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.STRICT;
        var regularPolicy = com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED;
        var plan = com.daviddunn.retirementplanner.domain.projection.ArkansasRegularTaxOnlyTest.survivorPlan(false, false, "2500", "0", 1);
        c.newPlan(plan);
        var strictRun = c.beginArkansasProjection(ignored -> { });
        awaitWork(strictRun);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(strictRun));
        var strict = c.getAcceptedProjectionSnapshot();
        assertFalse(strict.inputsDocument().contains("arkansasLowIncomePolicy"));
        assertEquals(ArkansasValidationTax.reviewedVersions(), strict.annual().arkansas().versions());
        c.saveAs(folder.resolve("strict-original.json"));
        c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
        c.saveCurrentAsBaseline("Synthetic Strict projection");

        com.daviddunn.retirementplanner.domain.projection.ArkansasRegularTaxOnlyTest.selectPolicy(c.getCurrentPlan(), regularPolicy);
        c.markModified();
        assertFalse(c.currentProjectionDeterministicallyApplicable());
        assertEquals(strict.id(), c.getAcceptedProjectionSnapshot().id());
        var regularRun = c.beginArkansasProjection(ignored -> { });
        awaitWork(regularRun);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(regularRun));
        var regular = c.getAcceptedProjectionSnapshot();
        assertTrue(regular.calculationSemanticsCurrent());
        assertTrue(regular.annual().arkansas().hasRegularTaxOnlyPolicy());
        assertNotEquals(strict.inputDigest(), regular.inputDigest());
        var pensionStamp = com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.PROVENANCE;
        var rebound = com.daviddunn.retirementplanner.app.projection.ConditionalBenefitExecutionSemantics.completed(
                c.getCurrentPlan(), strict.inputsDocument(), regular.annual(),
                strict.provenance().substring(strict.provenance().indexOf(pensionStamp)));
        var mismatch = assertThrows(IllegalArgumentException.class, () -> new CompleteProjectionSnapshot(regular.version(), regular.id(),
                regular.owner(), regular.sourcePlanId(), regular.acceptedAt(), strict.inputsDocument(), strict.inputDigest(),
                rebound, regular.annual(), regular.nonInvestable(), regular.summary(), regular.people()));
        assertTrue(mismatch.getMessage().contains("low-income modeling policy"), mismatch::getMessage);
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class, c::requireArkansasProjectionForAnalysis);
        assertNull(c.prepareAnalyzerAssociation(AnalysisKind.COUPLE_MONTE_CARLO_FIXED, "synthetic fingerprint"));
        c.save();

        var copy = folder.resolve("regular-copy.json");
        c.saveAs(copy);
        c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
        c.open(copy);
        c.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(regularPolicy, c.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().arkansasLowIncomePolicy());
        var reopened = c.getAcceptedProjectionSnapshot();
        assertEquals(regular.id(), reopened.id());
        assertEquals(regular.inputDigest(), reopened.inputDigest());
        var codec = new RetainedProjectionCodec();
        assertArrayEquals(codec.encode(regular.annual()).bytes(), codec.encode(reopened.annual()).bytes());

        assertTrue(c.commitProjectionSwap(c.prepareProjectionSwap().toCompletableFuture().get(15, TimeUnit.SECONDS)));
        var swapped = c.getAcceptedProjectionSnapshot();
        assertEquals(strict.id(), swapped.id());
        assertEquals(strictPolicy, c.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().arkansasLowIncomePolicy());
        assertArrayEquals(codec.encode(strict.annual()).bytes(), codec.encode(swapped.annual()).bytes());
        assertEquals(regular.id(), c.getCurrentPlan().getProjectionState().baseline().id());

        var pdf = folder.resolve("synthetic-regular-history.pdf");
        com.daviddunn.retirementplanner.app.export.ArkansasValidationReport.captureAccepted(reopened.annual().restoreProjection(), false)
                .writePdf(pdf);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            var contents = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(contents.contains("Arkansas retained history"), contents);
            assertTrue(contents.contains(com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy.REGULAR_TAX_DISCLOSURE), contents);
        }
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
        verifyAssociationPreservation(false);
    }

    @Test void unsupportedConditionalIncomeRetainsTheAcceptedArkansasProjectionAndAnalyzerArchive() throws Exception {
        verifyAssociationPreservation(true);
    }

    private void verifyAssociationPreservation(boolean production) throws Exception {
        var c = controller();
        var plan = surplusPlan("1000000", 1);
        c.newPlan(production ? selectArkansas(plan) : plan);
        c.saveAs(folder.resolve("associated.json"));
        var acceptedRun = production ? c.beginArkansasProjection(ignored -> { })
                : c.beginControlledArkansasProjection(validationEngine(), ignored -> { });
        awaitWork(acceptedRun);
        assertEquals(ACCEPTED, c.finishControlledArkansasProjection(acceptedRun));
        var projection = c.getAcceptedProjectionSnapshot();
        assertTrue(projection.arkansasHistory());
        assertEquals(projection.id(), c.getCurrentPlan().getProjectionState().current().id());
        assertTrue(projection.matches(c.getCurrentPlan()));
        var engine = production ? ProjectionEngine.forArkansasProduction() : validationEngine();
        var frozen = new RetirementPlanScenarioCopyService().copy(c.getCurrentPlan());
        var settings = new MonteCarloSettings(2, 42L, BigDecimal.ZERO, BigDecimal.ZERO);
        var kind = AnalysisKind.INDIVIDUAL_MONTE_CARLO_FIXED;
        var start = Instant.parse("2026-11-19T12:00:00Z");
        var finish = start.plusSeconds(1);
        long started = System.nanoTime();
        java.util.function.Supplier<ArkansasAnalysisProvenance.Captured> capture =
                () -> ArkansasAnalysisProvenance.capture(engine, kind, List.of(projection), () -> {
            var run = new MonteCarloAnalyzer(engine).analyze(frozen, settings, c.peekCurrentProjection(),
                    i -> ProjectionEconomicPath.constant(new BigDecimal(i == 0 ? ".04" : "-.02")));
            return MonteCarloSnapshotMapping.fixed(run, frozen,
                    new MonteCarloSnapshot.Display(com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(frozen),
                            "Primary", null, List.of(),
                            new MonteCarloSnapshot.Chart(List.of(), List.of(), List.of(), List.of()), 2026, 2026, false, 0, "Two matched paths."),
                    start, finish, "a".repeat(64));
        });
        var captured = production ? engine.runArkansasAnalysis(() -> false, capture) : capture.get();
        System.out.println("Arkansas controlled two-path analysis and provenance us=" + (System.nanoTime() - started) / 1000);
        var payload = (MonteCarloSnapshot) captured.payload();
        var fingerprints = new MonteCarloFingerprintService().storedFingerprint(payload);
        if (!production) assertNull(c.prepareAnalyzerAssociation(kind, fingerprints.fingerprint()));
        var admission = c.prepareControlledArkansasAnalyzerAssociation(kind, fingerprints.fingerprint());
        assertNotNull(admission);
        var unverified = new AcceptedAnalysisCompletion(c.getAnalysisSessionId(), c.getCurrentFile(), kind,
                admission.ticket(), finish, fingerprints.fingerprint(), fingerprints.contextFingerprint(),
                AnalysisVersions.calculatedAlgorithms(kind, payload), AnalysisVersions.references(kind), payload,
                new SnapshotContext("Single", "Primary", null, start, List.of(), List.of(), List.of(), Map.of()));
        var completion = unverified.withArkansasExecution(captured);
        assertFalse(c.associateAnalyzer(admission, production ? unverified : completion));
        assertFalse(c.associateControlledArkansasAnalyzer(admission, completion.forRun(UUID.randomUUID())));
        assertTrue(c.associateControlledArkansasAnalyzer(admission, completion));
        var association = c.getAnalyzerAssociations(projection.id()).getFirst();
        assertEquals(projection.id(), association.projectionId());
        assertEquals(projection.inputDigest(), association.projectionInputDigest());

        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Supplier<?> cancelAnalysis = () -> new MonteCarloAnalyzer(engine).analyze(frozen, settings,
                c.peekCurrentProjection(), i -> ProjectionEconomicPath.constant(BigDecimal.ZERO),
                progress -> { if (progress.completedWork() == 1) cancelled.set(true); }, cancelled::get);
        assertThrows(AnalysisCancelledException.class,
                () -> { if (production) engine.runArkansasAnalysis(cancelled::get, cancelAnalysis); else cancelAnalysis.get(); });
        assertEquals(List.of(association), c.getAnalyzerAssociations(projection.id()));
        c.save();
        var saved = c.getAnalyzerAssociations(projection.id()).getFirst();
        var restored = c.loadAssociatedAnalyzer(saved).toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertEquals(completion.runId(), restored.runId());
        assertEquals(payload, restored.payload());
        restored.arkansasExecution().validateDependency(projection);

        if (!production) return;
        var definition = new ConditionalBenefitDefinition(
                UUID.randomUUID(), "Synthetic unsupported income", Category.CONDITIONAL_INCOME,
                new Trigger(TriggerType.FIXED_DATE, LocalDate.of(2026, 1, 1), null, null),
                PersonReference.PRIMARY, PaymentPattern.MONTHLY_RECURRING, BigDecimal.ONE,
                null, null, null, null, BigDecimal.ZERO, EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR,
                TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION, ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH);
        c.getCurrentPlan().setConditionalBenefits(List.of(definition));
        c.markModified();
        var failed = c.beginArkansasProjection(ignored -> fail("Rejected inputs cannot produce an annual row."));
        awaitFailure(failed);
        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> c.finishControlledArkansasProjection(failed));
        assertInstanceOf(ConditionalBenefitAdmission.UnsupportedExecutionException.class, failure.getCause());
        assertEquals(projection, c.getAcceptedProjectionSnapshot());
        assertEquals(List.of(saved), c.getAnalyzerAssociations(projection.id()));
        assertEquals(restored, c.loadAssociatedAnalyzer(saved).toCompletableFuture().get(15, TimeUnit.SECONDS));
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
