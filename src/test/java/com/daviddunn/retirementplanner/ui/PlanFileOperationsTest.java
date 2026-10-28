package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanFileOperationsTest {
    @TempDir Path directory;
    final List<ApplicationController> controllers = new ArrayList<>();
    @BeforeAll static void startFx() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable action = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(action); } catch (IllegalStateException started) { Platform.runLater(action); }
        ready.get(20, TimeUnit.SECONDS);
    }
    static <T> T fx(Callable<T> action) throws Exception {
        var result = new FutureTask<>(action); Platform.runLater(result); return result.get(30, TimeUnit.SECONDS);
    }
    static class BlockingRepository extends JsonRetirementPlanRepository {
        volatile boolean armed, fail, loading;
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        void block() throws IOException {
            assertFalse(Platform.isFxApplicationThread(), "File work must be detached from FX");
            entered.countDown();
            try { if (!release.await(20, TimeUnit.SECONDS)) throw new IOException("Test timed out"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            if (fail) throw new IOException("Injected disk failure");
        }
        @Override public void saveWithIdentity(RetirementPlan plan, Path path, UUID identity, Runnable fence) throws IOException {
            if (armed && !loading) block(); super.saveWithIdentity(plan, path, identity, fence);
        }
        @Override public PreparedLoad prepareLoad(Path path) throws IOException {
            if (armed && loading) block(); return super.prepareLoad(path);
        }
    }
    ApplicationController controller(JsonRetirementPlanRepository repository) throws Exception {
        var c = new ApplicationController(repository, new JsonApplicationSettingsRepository(directory.resolve("settings-" + controllers.size())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("slots-" + controllers.size()))));
        c.newPlan(BaselineDetailsTest.plan(true)); c.getCurrentProjection(); controllers.add(c); return c;
    }
    @AfterEach void stop() throws Exception {
        for (var c : controllers) {
            c.getPlanPreferenceWrite().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getAnalysisParameters().drain(Duration.ofSeconds(10)); c.getAnalysisParameters().shutdown();
            c.getAnalysisResults().drain(Duration.ofSeconds(10)); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
            c.getSettingsService().close();
        }
    }
    @Test void saveIsDetachedResponsiveAndDoesNotClearLaterEdits() throws Exception {
        var repo = new BlockingRepository(); var c = controller(repo); var file = directory.resolve("plan.json"); c.saveAs(file);
        var savedInputs = CompleteProjectionSnapshot.freeze(c.getCurrentPlan());
        repo.armed = true;
        var work = fx(() -> c.preparePlanSave(file, false));
        assertTrue(repo.entered.await(10, TimeUnit.SECONDS));
        fx(() -> {
            assertTrue(c.isPlanFileBusy()); assertThrows(IllegalStateException.class, () -> c.preparePlanOpen(file));
            c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("7654321.123400"));
            c.markModified(); return null;
        }); // FX heartbeat runs while disk work is deliberately blocked.
        repo.release.countDown(); var receipt = work.toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(receipt); return null; });
        assertTrue(c.isModified()); assertFalse(c.isPlanFileBusy());
        repo.armed = false;
        assertEquals(savedInputs, CompleteProjectionSnapshot.freeze(repo.load(file)));
        assertEquals(new BigDecimal("7654321.123400"), c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
    }
    @Test void failedSavePreservesDirtyStateAndDurableFileAndAllowsRetry() throws Exception {
        var repo = new BlockingRepository(); var c = controller(repo); var file = directory.resolve("plan.json"); c.saveAs(file);
        var before = Files.readAllBytes(file); c.markModified(); repo.armed = true; repo.fail = true;
        var work = fx(() -> c.preparePlanSave(file, false)); assertTrue(repo.entered.await(10, TimeUnit.SECONDS)); repo.release.countDown();
        assertThrows(ExecutionException.class, () -> work.toCompletableFuture().get(20, TimeUnit.SECONDS));
        assertFalse(c.isPlanFileBusy()); assertTrue(c.isModified()); assertArrayEquals(before, Files.readAllBytes(file));
        repo.armed = false;
        var receipt = fx(() -> c.preparePlanSave(file, false)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(receipt); return null; }); assertFalse(c.isModified());
    }
    @Test void saveAsIsIndependentAndRestoresExactInputsAndProjectionWithoutCalculation() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var original = directory.resolve("a.json"); var copy = directory.resolve("b.json");
        c.saveAs(original); var bytes = Files.readAllBytes(original); var owner = c.getAnalysisSessionId(); var projection = c.getAcceptedProjectionSnapshot();
        var receipt = fx(() -> c.preparePlanSave(copy, true)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(receipt); return null; });
        assertNotEquals(owner, c.getAnalysisSessionId()); assertEquals(projection.id(), c.getAcceptedProjectionSnapshot().id());
        assertEquals(projection.annual(), c.getAcceptedProjectionSnapshot().annual()); assertArrayEquals(bytes, Files.readAllBytes(original));
        BaselineDetailsTest.set(c, "projectionEngine", new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine() {
            @Override public com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) {
                throw new AssertionError("Opening accepted results must not calculate");
            }
            @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(RetirementPlan plan) {
                throw new AssertionError("Opening accepted results must not calculate");
            }
        });
        var opening = fx(() -> c.preparePlanOpen(original)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanOpen(opening); return null; }); c.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(owner, c.getAnalysisSessionId()); assertEquals(projection, c.getAcceptedProjectionSnapshot());
        assertNotNull(c.getCurrentProjection());
    }
    @Test void loadDoesNotPublishPartialStateAndRejectsChangedOwner() throws Exception {
        var repo = new BlockingRepository(); var c = controller(repo); var file = directory.resolve("a.json"); c.saveAs(file);
        var original = c.getCurrentPlan(); repo.armed = true; repo.loading = true;
        var work = fx(() -> c.preparePlanOpen(file)); assertTrue(repo.entered.await(10, TimeUnit.SECONDS));
        fx(() -> { assertSame(original, c.getCurrentPlan()); c.newPlan(BaselineDetailsTest.plan(false)); return null; });
        var newer = c.getCurrentPlan(); repo.release.countDown(); var opened = work.toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { assertThrows(IllegalStateException.class, () -> c.finishPlanOpen(opened)); return null; });
        assertSame(newer, c.getCurrentPlan()); assertFalse(c.isPlanFileBusy());
    }
    @Test void staleSaveReceiptCannotPublishIntoAnotherPlan() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var file = directory.resolve("a.json");
        var receipt = fx(() -> c.preparePlanSave(file, true)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.newPlan(BaselineDetailsTest.plan(false)); assertThrows(IllegalStateException.class, () -> c.finishPlanSave(receipt)); return null; });
        assertFalse(c.getCurrentPlan().getHousehold().hasSpouse()); assertNull(c.getCurrentFile()); assertTrue(Files.exists(file));
    }
    @Test void olderSaveCannotOverwriteNewAcceptedParameters() throws Exception {
        var repo = new BlockingRepository(); var c = controller(repo); var file = directory.resolve("a.json"); c.saveAs(file);
        repo.armed = true; var work = fx(() -> c.preparePlanSave(file, false)); assertTrue(repo.entered.await(10, TimeUnit.SECONDS));
        var kind = AnalysisKind.COUPLE_QUICK;
        var configuration = new AnalysisParameterConfiguration(1, kind, kind.shape(), UUID.randomUUID(), java.time.Instant.now(),
                new AnalysisParameterConfiguration.Quick(4, "a".repeat(64)), Map.of());
        fx(() -> {
            var parameters = c.getAnalysisParameters();
            assertTrue(parameters.accept(new AcceptedAnalysisParameters(parameters.bind(c.getCurrentPlan(), file), parameters.nextAcceptanceSequence(), configuration)));
            return null;
        });
        assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
        repo.release.countDown(); assertThrows(ExecutionException.class, () -> work.toCompletableFuture().get(20, TimeUnit.SECONDS));
        repo.armed = false;
        assertEquals(configuration, repo.load(file).getAnalyzerConfigurations().get(kind).orElseThrow());
        assertEquals(configuration, c.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow());
    }
    @Test void acceptanceBetweenInputCaptureAndFenceCaptureIsAlsoRejected() throws Exception {
        var repository = new JsonRetirementPlanRepository(); var file = directory.resolve("capture-race.json");
        var kind = AnalysisKind.COUPLE_QUICK;
        var configuration = new AnalysisParameterConfiguration(1, kind, kind.shape(), UUID.randomUUID(), java.time.Instant.now(),
                new AnalysisParameterConfiguration.Quick(4, "a".repeat(64)), Map.of());
        repository.registerParameters(file, configuration);
        assertThrows(IllegalStateException.class, () -> repository.parameterWriteFence(file, null, true).run());
        repository.parameterWriteFence(file, AnalysisParameterConfigurations.empty().with(configuration), true).run();
    }
    @Test void replayedReceiptCannotReleaseOrReplaceNewOperation() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var file = directory.resolve("a.json");
        var first = fx(() -> c.preparePlanSave(file, true)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(first); return null; });
        var newer = fx(() -> c.preparePlanOpen(file)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { assertThrows(IllegalStateException.class, () -> c.finishPlanSave(first)); assertTrue(c.isPlanFileBusy()); c.finishPlanOpen(newer); return null; });
    }
    @Test void loadRejectsNewAcceptedConfigurationWithoutReplacingTheSession() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var file = directory.resolve("a.json"); c.saveAs(file);
        var original = c.getCurrentPlan();
        var prepared = fx(() -> c.preparePlanOpen(file)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        var kind = AnalysisKind.COUPLE_QUICK;
        var configuration = new AnalysisParameterConfiguration(1, kind, kind.shape(), UUID.randomUUID(), java.time.Instant.now(),
                new AnalysisParameterConfiguration.Quick(3, "a".repeat(64)), Map.of());
        fx(() -> {
            original.setAnalyzerConfigurations(AnalysisParameterConfigurations.empty().with(configuration));
            assertThrows(IllegalStateException.class, () -> c.finishPlanOpen(prepared)); return null;
        });
        assertSame(original, c.getCurrentPlan()); assertEquals(configuration, original.getAnalyzerConfigurations().get(kind).orElseThrow());
    }
    @Test void firstSaveCannotRebindNewerUnsavedAssociationsOrSettingsToAnotherOwner() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var file = directory.resolve("first.json");
        var legacy = BaselineDetailsTest.plan(true);
        c.newPlan(new RetirementPlan(legacy.getHousehold(), legacy.getAccountPortfolio(), legacy.getPlanningAssumptions(),
                legacy.getRothConversionRequest(), legacy.getNonInvestableAssets(), null));
        c.getCurrentProjection();
        assertNull(c.getCurrentPlan().getPlanId()); var owner = c.getAnalysisSessionId();
        var manifest = c.getCurrentPlan().getProjectionState();
        var receipt = fx(() -> c.preparePlanSave(file, true)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        var kind = AnalysisKind.COUPLE_QUICK;
        var configuration = new AnalysisParameterConfiguration(1, kind, kind.shape(), UUID.randomUUID(), java.time.Instant.now(),
                new AnalysisParameterConfiguration.Quick(5, "a".repeat(64)), Map.of());
        fx(() -> {
            c.getCurrentPlan().setAnalyzerConfigurations(AnalysisParameterConfigurations.empty().with(configuration));
            assertThrows(IllegalStateException.class, () -> c.finishPlanSave(receipt)); return null;
        });
        assertNull(c.getCurrentFile()); assertNull(c.getCurrentPlan().getPlanId()); assertEquals(owner, c.getAnalysisSessionId());
        assertSame(manifest, c.getCurrentPlan().getProjectionState()); assertTrue(c.isModified());
        assertEquals(configuration, c.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow());
        assertNotNull(new JsonRetirementPlanRepository().load(file).getPlanId(), "The earlier captured copy really committed");
    }
    @Test void failedLoadRetainsPreviousPlanPathDirtyStateAndProjection() throws Exception {
        var c = controller(new JsonRetirementPlanRepository()); var file = directory.resolve("a.json"); c.saveAs(file); c.markModified();
        var before = c.getCurrentPlan(); var projection = c.peekCurrentProjection();
        var work = fx(() -> c.preparePlanOpen(directory.resolve("missing.json")));
        assertThrows(ExecutionException.class, () -> work.toCompletableFuture().get(20, TimeUnit.SECONDS));
        assertSame(before, c.getCurrentPlan()); assertSame(projection, c.peekCurrentProjection()); assertEquals(file, c.getCurrentFile()); assertTrue(c.isModified());
    }
    @Test void preferenceFailureDoesNotTurnDurableFinancialSaveIntoFailure() throws Exception {
        var settings = new JsonApplicationSettingsRepository(directory.resolve("unwritable-settings.json")) {
            @Override public void save(com.daviddunn.retirementplanner.application.settings.ApplicationSettings value) {
                throw new IllegalStateException("Injected preference failure");
            }
        };
        var c = new ApplicationController(new JsonRetirementPlanRepository(), settings,
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("preference-slots"))));
        controllers.add(c); c.newPlan(BaselineDetailsTest.plan(true)); c.getCurrentProjection();
        var file = directory.resolve("financial-save.json");
        var receipt = fx(() -> c.preparePlanSave(file, true)).toCompletableFuture().get(20, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(receipt); return null; });
        c.getPlanPreferenceWrite().toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertFalse(c.isModified()); assertEquals(file, c.getCurrentFile());
        assertNotNull(new JsonRetirementPlanRepository().load(file));
        assertTrue(c.takePersistenceWarnings().stream().anyMatch(message -> message.contains("settings could not be saved")));
    }
}
