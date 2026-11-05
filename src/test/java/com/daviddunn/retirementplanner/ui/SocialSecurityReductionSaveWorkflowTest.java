package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.PlanSaveDiagnostics;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the Save/Save As owner-thread capture and detached persistence path with isolated files. */
class SocialSecurityReductionSaveWorkflowTest {
    @TempDir Path directory;
    private final List<ApplicationController> controllers = new ArrayList<>();

    @BeforeAll
    static void startFx() throws Exception {
        var ready = new CompletableFuture<Void>();
        Runnable start = () -> { Platform.setImplicitExit(false); ready.complete(null); };
        try { Platform.startup(start); } catch (IllegalStateException started) { Platform.runLater(start); }
        ready.get(20, TimeUnit.SECONDS);
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private ApplicationController controller(JsonRetirementPlanRepository plans, String suffix) throws Exception {
        var c = new ApplicationController(plans,
                new JsonApplicationSettingsRepository(directory.resolve("settings-" + suffix)),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results-" + suffix))));
        c.newPlan(BaselineDetailsTest.plan(true));
        c.getCurrentProjection();
        controllers.add(c);
        return c;
    }

    @AfterEach
    void close() throws Exception {
        for (var c : controllers) {
            c.getPlanPreferenceWrite().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getSnapshotCopyCompletion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            c.getAnalysisParameters().drain(java.time.Duration.ofSeconds(10));
            c.getAnalysisParameters().shutdown();
            c.getAnalysisResults().drain(java.time.Duration.ofSeconds(10));
            c.getAnalysisResults().shutdownAfterSuccessfulDrain();
            c.getSettingsService().close();
        }
        controllers.clear();
    }

    @Test
    void applicationSaveOrchestrationRoundTripsZeroAndNonzeroReductionSettings() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var c = controller(repository, "matrix");
        Path original = directory.resolve("existing.json");
        Path copy = directory.resolve("save-as.json");
        c.saveAs(original);
        c.saveCurrentAsBaseline("Accepted baseline for isolated save reproduction");

        save(c, original, "0", 2032, false);
        assertValues(repository.load(original), "0", 2032);
        save(c, original, "22", 2032, false);
        assertValues(repository.load(original), "22", 2032);
        save(c, copy, "22", 2032, true);
        assertValues(repository.load(copy), "22", 2032);
        save(c, copy, "0", 2032, false);
        assertValues(repository.load(copy), "0", 2032);
        save(c, copy, "17", 2034, false);
        assertValues(repository.load(copy), "17", 2034);
        save(c, directory.resolve("different-year.json"), "22", 2034, true);

        var newPlan = controller(new JsonRetirementPlanRepository(), "new-plan");
        setReduction(newPlan, "22", 2032);
        Path newPlanFile = directory.resolve("new-plan-save-as.json");
        var newReceipt = fx(() -> newPlan.preparePlanSave(newPlanFile, true)).toCompletableFuture().get(30, TimeUnit.SECONDS);
        fx(() -> { newPlan.finishPlanSave(newReceipt); return null; });
        assertValues(new JsonRetirementPlanRepository().load(newPlanFile), "22", 2032);

        Path legacy = directory.resolve("legacy.json");
        var legacyRepository = new JsonRetirementPlanRepository();
        legacyRepository.save(RetirementPlanFactory.createEmptyPlan(), legacy);
        var jackson = new com.fasterxml.jackson.databind.ObjectMapper();
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) jackson.readTree(legacy.toFile());
        var economics = (com.fasterxml.jackson.databind.node.ObjectNode)
                document.path("planningAssumptions").path("economicAssumptions");
        economics.remove(List.of("socialSecurityBenefitReductionPercent", "socialSecurityBenefitReductionEffectiveYear"));
        java.nio.file.Files.write(legacy, jackson.writeValueAsBytes(document));
        var legacyController = controller(new JsonRetirementPlanRepository(), "legacy");
        fx(() -> { legacyController.open(legacy); return null; });
        save(legacyController, legacy, "0", 2032, false);
        assertValues(legacyRepository.load(legacy), "0", 2032);
        assertNotNull(repository.load(original).getProjectionState(), "Captured projection associations remain saveable.");
        assertNotNull(repository.load(original).getBaseline(), "Captured baseline remains saveable.");
    }

    @Test
    void workerFailureRetainsCauseAndStageWithoutExposingExceptionMessageInDiagnostics() throws Exception {
        var failingRepository = new JsonRetirementPlanRepository() {
            @Override public void saveWithIdentity(RetirementPlan plan, Path path, java.util.UUID identity,
                    Runnable fence) throws java.io.IOException {
                throw new java.io.IOException("C:\\Users\\PrivateName\\Plan.json balance 123456");
            }
        };
        var c = controller(failingRepository, "diagnostic-failure");
        var destination = directory.resolve("failure.json");
        var work = fx(() -> c.preparePlanSave(destination, true));
        var wrapped = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> work.toCompletableFuture().get(30, TimeUnit.SECONDS));
        assertInstanceOf(java.io.IOException.class, wrapped.getCause());
        var context = c.getPlanSaveDiagnostics();
        assertEquals(PlanSaveDiagnostics.Operation.SAVE_AS, context.operation());
        assertEquals(PlanSaveDiagnostics.Stage.PLAN_COPY_RECONSTRUCTION, context.stage());
        assertFalse(context.fileWritingStarted());
        assertFalse(context.atomicReplacementAttempted());
        assertFalse(java.nio.file.Files.exists(destination));
    }

    private void save(ApplicationController c, Path destination, String percent, int year, boolean saveAs)
            throws Exception {
        setReduction(c, percent, year);
        var receipt = fx(() -> c.preparePlanSave(destination, saveAs)).toCompletableFuture().get(30, TimeUnit.SECONDS);
        fx(() -> { c.finishPlanSave(receipt); return null; });
    }

    private void setReduction(ApplicationController c, String percent, int year) throws Exception {
        fx(() -> {
            var plan = c.getCurrentPlan();
            var old = plan.getPlanningAssumptions();
            var e = old.getEconomicAssumptions();
            var next = new EconomicAssumptions(e.getExpectedAnnualInvestmentReturn(),
                    e.getGeneralInflationRate(), e.getHealthcareInflationRate(), e.getSocialSecurityColaRate(),
                    new BigDecimal(percent), year);
            plan.setPlanningAssumptions(new PlanningAssumptions(next, old.getTaxAssumptions(),
                    old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(),
                    old.getProjectionLengthYears(), old.getProjectionStartDate(), old.getStateTaxAssumptions()));
            c.markModified();
            return null;
        });
    }

    private static void assertValues(RetirementPlan plan, String percent, int year) {
        var economic = plan.getPlanningAssumptions().getEconomicAssumptions();
        assertEquals(0, new BigDecimal(percent).compareTo(economic.getSocialSecurityBenefitReductionPercent()));
        assertEquals(year, economic.getSocialSecurityBenefitReductionEffectiveYear());
    }
}
