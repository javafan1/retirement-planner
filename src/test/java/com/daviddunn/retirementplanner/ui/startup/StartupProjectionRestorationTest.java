package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupProjectionRestorationTest {
    @TempDir Path directory;
    private final java.util.List<ApplicationController> controllers = new java.util.ArrayList<>();
    private ApplicationController controller(Path settingsPath) {
        var repository = new JsonApplicationSettingsRepository(settingsPath);
        var c = new ApplicationController(new JsonRetirementPlanRepository(), repository,
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analysis-" + controllers.size()))),
                new ApplicationSettingsService(repository));
        controllers.add(c); return c;
    }
    @AfterEach void cleanup() throws Exception {
        for (var c : controllers) {
            c.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertTrue(c.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete()); c.getAnalysisParameters().shutdown();
            assertTrue(c.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); c.getAnalysisResults().shutdownAfterSuccessfulDrain();
            c.getSettingsService().close();
        }
    }
    static ProjectionEngine forbidden() {
        return new ProjectionEngine() {
            @Override public Projection project(RetirementPlan plan) { throw new AssertionError("Startup must restore, not recalculate"); }
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) { throw new AssertionError("Startup must restore, not recalculate"); }
        };
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void exclusiveStartupRestoresOriginalIdentityAndFinancialValuesWithoutCalculation(boolean couple) throws Exception {
        var settings = directory.resolve("settings.json");
        var source = controller(settings); source.newPlan(BaselineDetailsTest.plan(couple));
        source.getCurrentProjection(); source.saveAs(directory.resolve("plan.json"));
        source.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
        var accepted = source.getAcceptedProjectionSnapshot();
        var reopened = controller(settings);
        BaselineDetailsTest.set(reopened, "projectionEngine", forbidden());
        assertNull(CompletableFuture.supplyAsync(reopened::prepareStartup).get(15, TimeUnit.SECONDS));
        reopened.getProjectionRestoration().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertTrue(reopened.isRestoredProjectionMode());
        assertEquals(accepted.id(), reopened.getAcceptedProjectionSnapshot().id());
        assertEquals(accepted.inputsDocument(), reopened.getAcceptedProjectionSnapshot().inputsDocument());
        assertEquals(accepted.annual(), reopened.getAcceptedProjectionSnapshot().annual());
        assertEquals(accepted.summary(), reopened.getAcceptedProjectionSnapshot().summary());
        assertTrue(reopened.currentProjectionInputsMatch()); assertFalse(reopened.isModified());
        assertThrows(IllegalStateException.class, reopened::prepareStartup);
    }
    @Test void damagedLastPlanIsRecoverableAndNeitherFileIsOverwritten() throws Exception {
        var path = directory.resolve("plan.json"); Files.writeString(path, "damaged");
        var settings = directory.resolve("settings.json"); var preferences = new ApplicationSettings();
        preferences.setLastOpenedPlan(path.toString()); new JsonApplicationSettingsRepository(settings).save(preferences);
        var originalSettings = Files.readAllBytes(settings);
        var reopened = controller(settings); BaselineDetailsTest.set(reopened, "projectionEngine", forbidden());
        assertNull(CompletableFuture.supplyAsync(reopened::prepareStartup).get(10, TimeUnit.SECONDS));
        assertFalse(reopened.hasCurrentFile()); assertNotNull(reopened.getFailedOpenPath());
        assertEquals("damaged", Files.readString(path)); assertArrayEquals(originalSettings, Files.readAllBytes(settings));
        assertFalse(reopened.isModified());
    }
    @Test void subscribedControllerCannotBeHandedToStartupWorker() {
        var c = controller(directory.resolve("settings.json")); c.addSourcePlanRevisionListener(() -> {});
        assertThrows(IllegalStateException.class, c::prepareStartup);
    }
}
