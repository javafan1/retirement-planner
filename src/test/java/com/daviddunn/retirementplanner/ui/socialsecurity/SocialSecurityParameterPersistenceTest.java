package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;

class SocialSecurityParameterPersistenceTest {
    @TempDir Path dir;
    @BeforeAll static void init() throws Exception {
        startFx(); CoupleSocialSecurityPersistenceTest.prepare(); ExhaustivePersistenceTest.fixture();
    }
    ApplicationController source(AnalysisKind kind, JsonRetirementPlanRepository repository) throws Exception {
        var source = new ApplicationController(repository, new JsonApplicationSettingsRepository(dir.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        fx(() -> {
            source.newPlan(kind.shape() == AnalysisKind.Shape.COUPLE ? ExhaustivePersistenceTest.plan() : SinglePersonMortalityAnalysisTest.plan());
            source.saveAs(dir.resolve("plan.json")); return null;
        });
        return source;
    }
    static final class Rig implements AutoCloseable {
        final SocialSecurityAnalyzerJobControllerTest.ManualExecutor worker = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
        final SocialSecurityAnalysisJobCoordinator coordinator = new SocialSecurityAnalysisJobCoordinator(worker);
        final SocialSecurityAnalyzerJobController jobs = new SocialSecurityAnalyzerJobController(coordinator, Platform::runLater);
        final SocialSecurityStrategyAnalyzerDialog dialog;
        final SinglePersonIntegratedView individual;
        WeightedResultPersistence originalSocialSecurityPersistence;
        final AtomicInteger calculations = new AtomicInteger();
        Rig(ApplicationController source) throws Exception {
            dialog = new SocialSecurityStrategyAnalyzerDialog(null, source, jobs);
            individual = field(dialog, "singlePersonView", SinglePersonIntegratedView.class);
            dialog.socialSecurityCalculation = request -> (p, c) -> { calculations.incrementAndGet(); return CoupleSocialSecurityPersistenceTest.result; };
            dialog.exhaustiveCalculation = request -> (p, c) -> { calculations.incrementAndGet(); return ExhaustivePersistenceTest.result; };
            dialog.weightedCalculation = request -> { calculations.incrementAndGet(); return SnapshotFixtures.couple().couple().restore(); };
            // The bounded SS result fixture has its own request. Test parameter handoff independently;
            // the existing snapshot tests enforce exact request/result fidelity, without an expensive optimizer run.
            if (individual == null) {
                var persistence = field(dialog, "socialSecurityPersistence", WeightedResultPersistence.class); persistence.close();
                originalSocialSecurityPersistence = persistence;
                set(dialog, "socialSecurityPersistence", null);
            }
        }
        void select(AnalysisKind kind) throws Exception {
            var mode = switch (kind) {
                case COUPLE_SOCIAL_SECURITY, INDIVIDUAL_SOCIAL_SECURITY -> SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY;
                case COUPLE_QUICK -> SocialSecurityAnalyzerJobController.Mode.QUICK;
                case COUPLE_EXHAUSTIVE, INDIVIDUAL_DETERMINISTIC -> SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE;
                default -> SocialSecurityAnalyzerJobController.Mode.WEIGHTED;
            };
            (individual == null ? field(dialog, "navigation", AnalyzerNavigationView.class) : individual.navigation).select(mode);
        }
        SinglePersonMortalityView mortality(AnalysisKind kind) { return kind == AnalysisKind.INDIVIDUAL_WEIGHTED ? individual.weightedView : individual.ssView; }
        TextField editedField(AnalysisKind kind) throws Exception {
            if (kind == AnalysisKind.COUPLE_QUICK) return field(dialog, "integratedCandidateCount", Spinner.class).getEditor();
            if (kind == AnalysisKind.COUPLE_EXHAUSTIVE) return field(dialog, "discountRate", TextField.class);
            if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) return individual.deathYear;
            return individual == null ? field(dialog, "primaryMortalityAdjustment", TextField.class) : mortality(kind).factor;
        }
        void configure(AnalysisKind kind) throws Exception {
            select(kind);
            if (kind == AnalysisKind.COUPLE_QUICK) field(dialog, "integratedCandidateCount", Spinner.class).getValueFactory().setValue(2);
            else if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) individual.deathYear.setText("2040");
            else if (kind != AnalysisKind.COUPLE_EXHAUSTIVE) {
                editedField(kind).setText("1.1500");
                if (individual == null) {
                    field(dialog, "spouseMortalityAdjustment", TextField.class).setText("0.9500");
                    field(dialog, "discountRate", TextField.class).setText("2.1250");
                    if (kind == AnalysisKind.COUPLE_WEIGHTED) {
                        var baseline = field(dialog, "baselineInputs", CurrentStrategyBaselineView.class);
                        baseline.primarySurvivor.setText("invalid optional"); baseline.spouseSurvivor.setText("67");
                    }
                } else mortality(kind).discount.setText("2.1250");
            }
        }
        void run(AnalysisKind kind) throws Exception {
            if (individual != null) {
                if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) individual.start(); else mortality(kind).start();
            } else ExhaustivePersistenceTest.invoke(dialog, switch (kind) {
                case COUPLE_SOCIAL_SECURITY -> "runAnalysis";
                case COUPLE_QUICK -> "runIntegratedAnalysis";
                case COUPLE_EXHAUSTIVE -> "runExhaustiveSearch";
                default -> "runWeightedAnalysis";
            });
        }
        SocialSecurityParameterBinding binding(AnalysisKind kind) throws Exception {
            if (individual != null) return kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC ? individual.parameters : mortality(kind).parameters;
            return ((Map<AnalysisKind, SocialSecurityParameterBinding>) field(dialog, "parameterBindings", Map.class)).get(kind);
        }
        void execute() throws Exception {
            assertFalse(worker.queue.isEmpty()); worker.run();
            fx(() -> { List.copyOf(javafx.stage.Window.getWindows()).stream()
                    .filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane).forEach(javafx.stage.Window::hide); return null; });
            fx(() -> null);
        }
        void fail(AnalysisKind kind) {
            if (individual != null) {
                if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) individual.calculation = r -> (p, c) -> { throw new IllegalStateException("Injected failure"); };
                else mortality(kind).calculation = r -> (p, c) -> { throw new IllegalStateException("Injected failure"); };
            } else switch (kind) {
                case COUPLE_SOCIAL_SECURITY -> dialog.socialSecurityCalculation = r -> (p, c) -> { throw new IllegalStateException("Injected failure"); };
                case COUPLE_EXHAUSTIVE -> dialog.exhaustiveCalculation = r -> (p, c) -> { throw new IllegalStateException("Injected failure"); };
                case COUPLE_QUICK -> dialog.quickCalculation = r -> (p, c) -> { throw new IllegalStateException("Injected failure"); };
                default -> dialog.weightedCalculation = r -> { throw new IllegalStateException("Injected failure"); };
            }
        }
        @Override public void close() throws Exception { fx(() -> { ExhaustivePersistenceTest.invoke(dialog, "close"); return null; }); coordinator.close(); }
    }
    static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    static void stop(ApplicationController source) throws Exception {
        source.getAnalysisParameters().drain(Duration.ofSeconds(10)); source.getAnalysisParameters().shutdown();
        WeightedRestorationTest.stop(source.getAnalysisResults());
    }
    void prerequisite(Rig rig, ApplicationController source) throws Exception {
        fx(() -> { rig.select(AnalysisKind.COUPLE_SOCIAL_SECURITY); rig.run(AnalysisKind.COUPLE_SOCIAL_SECURITY); return null; });
        rig.execute(); await(rig.binding(AnalysisKind.COUPLE_SOCIAL_SECURITY).status, "SAVED");
    }

    @Test void neutralOpeningStillRestoresHiddenAcceptedSsParametersForQuickCompatibility() throws Exception {
        var kind = AnalysisKind.COUPLE_SOCIAL_SECURITY;
        var source = source(kind, new JsonRetirementPlanRepository());
        try {
            try (var rig = fx(() -> new Rig(source))) {
                fx(() -> { rig.configure(kind); rig.run(kind); return null; });
                rig.execute(); await(rig.binding(kind).status, "SAVED");
            }
            var restarted = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("restart.json")),
                    new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("restart-results"))));
            fx(() -> { restarted.open(dir.resolve("plan.json")); return null; });
            long revision = restarted.getSourcePlanRevision(); boolean dirty = restarted.isModified();
            try (var reopened = fx(() -> new Rig(restarted))) {
                fx(() -> {
                    var navigation = field(reopened.dialog, "navigation", AnalyzerNavigationView.class);
                    assertNull(navigation.selected());
                    assertEquals("1.1500", reopened.editedField(kind).getText());
                    assertEquals(0, reopened.calculations.get());
                    assertEquals(0, reopened.jobs.generation());
                    assertEquals(revision, restarted.getSourcePlanRevision()); assertEquals(dirty, restarted.isModified());
                    return null;
                });
            } finally { stop(restarted); }
        } finally { stop(source); }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_NONE)
    void acceptedSettingsSaveAndRestoreFromNewControllerWithoutFinancialWork(AnalysisKind kind) throws Exception {
        var source = source(kind, new JsonRetirementPlanRepository());
        AnalysisParameterConfiguration accepted;
        try (var rig = fx(() -> new Rig(source))) {
            if (kind == AnalysisKind.COUPLE_QUICK) prerequisite(rig, source);
            fx(() -> { rig.configure(kind); rig.run(kind); return null; }); rig.execute();
            await(rig.binding(kind).status, "SAVED");
            accepted = source.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow();
            assertEquals(accepted, new JsonRetirementPlanRepository().load(source.getCurrentFile()).getAnalyzerConfigurations().get(kind).orElseThrow());
            assertFalse(source.isModified());
            if (accepted.parameters() instanceof AnalysisParameterConfiguration.Mortality m) {
                assertEquals(new BigDecimal("1.1500"), m.primaryFactor()); assertEquals(new BigDecimal("0.021250"), m.realDiscountRate());
            }
            if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) assertEquals(2040, ((AnalysisParameterConfiguration.Search) accepted.parameters()).primaryDeathYear());
            if (kind == AnalysisKind.COUPLE_WEIGHTED) {
                var m = (AnalysisParameterConfiguration.Mortality) accepted.parameters();
                assertEquals(AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE, m.baseline().primary().state());
                assertEquals("invalid optional", m.baseline().primary().originalText());
            }
        } finally { stop(source); }
        var reopened = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("restart.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        fx(() -> { reopened.open(dir.resolve("plan.json")); return null; });
        try (var rig = fx(() -> new Rig(reopened))) {
            fx(() -> {
                rig.select(kind); assertEquals(0, rig.jobs.generation()); assertTrue(rig.worker.queue.isEmpty());
                assertEquals(accepted, reopened.getAnalysisParameters().lastAccepted(reopened.getCurrentPlan(), kind).orElseThrow());
                if (kind == AnalysisKind.COUPLE_QUICK) {
                    assertEquals(2, field(rig.dialog, "integratedCandidateCount", Spinner.class).getValue());
                    assertTrue(field(rig.dialog, "integratedRunButton", Button.class).isDisabled(), "Quick still needs a current SS result");
                } else if (accepted.parameters() instanceof AnalysisParameterConfiguration.Mortality) {
                    assertEquals("1.1500", rig.editedField(kind).getText());
                    if (rig.individual == null) assertEquals("2.1250", field(rig.dialog, "discountRate", TextField.class).getText());
                    else assertEquals("2.1250", rig.mortality(kind).discount.getText());
                } else if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) assertEquals("2040", rig.individual.deathYear.getText());
                return null;
            });
            assertEquals(0, rig.calculations.get()); assertFalse(reopened.isModified());
        } finally { stop(reopened); }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_SOCIAL_SECURITY", "COUPLE_WEIGHTED", "INDIVIDUAL_SOCIAL_SECURITY", "INDIVIDUAL_WEIGHTED", "INDIVIDUAL_DETERMINISTIC"})
    void cancellationRollsBackButValidationAndLaterEditsAreProtected(AnalysisKind kind) throws Exception {
        var source = source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new Rig(source))) {
            fx(() -> { rig.configure(kind); rig.run(kind); return null; }); rig.execute(); await(rig.binding(kind).status, "SAVED");
            var accepted = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow();
            String original = fx(() -> rig.editedField(kind).getText());
            fx(() -> { rig.editedField(kind).setText(kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC ? "2041" : "1.25"); rig.run(kind); rig.jobs.cancel(); return null; });
            rig.execute(); fx(() -> { assertEquals(original, rig.editedField(kind).getText());
                rig.editedField(kind).setText("invalid");
                if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY) dismissError();
                rig.run(kind); assertEquals("invalid", rig.editedField(kind).getText()); assertTrue(rig.worker.queue.isEmpty());
                rig.editedField(kind).setText(kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC ? "2041" : "1.25"); rig.run(kind); rig.jobs.cancel();
                rig.editedField(kind).setText("newer draft"); return null; });
            rig.execute(); fx(() -> { assertEquals("newer draft", rig.editedField(kind).getText()); return null; });
            assertEquals(accepted, source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow());
        } finally { stop(source); }
    }
    static void dismissError() {
        Platform.runLater(() -> List.copyOf(javafx.stage.Window.getWindows()).stream()
                .filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane).forEach(javafx.stage.Window::hide));
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_NONE)
    void fatalFailureAndPlanSwitchCannotReplaceAcceptedSettings(AnalysisKind kind) throws Exception {
        var source = source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new Rig(source))) {
            if (kind == AnalysisKind.COUPLE_QUICK) prerequisite(rig, source);
            fx(() -> { rig.configure(kind); rig.run(kind); return null; }); rig.execute(); await(rig.binding(kind).status, "SAVED");
            var accepted = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow();
            fx(() -> {
                if (kind == AnalysisKind.COUPLE_QUICK) field(rig.dialog, "integratedCandidateCount", Spinner.class).getValueFactory().setValue(3);
                else if (kind != AnalysisKind.COUPLE_EXHAUSTIVE) rig.editedField(kind).setText(kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC ? "2041" : "1.25");
                rig.fail(kind); rig.run(kind); return null;
            });
            rig.execute(); fx(() -> {
                assertEquals(accepted, source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow());
                if (kind == AnalysisKind.COUPLE_QUICK) assertEquals(2, field(rig.dialog, "integratedCandidateCount", Spinner.class).getValue());
                else if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) assertEquals("2040", rig.individual.deathYear.getText());
                else if (kind != AnalysisKind.COUPLE_EXHAUSTIVE) assertEquals("1.1500", rig.editedField(kind).getText());
                rig.run(kind);
                if (rig.individual == null) set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence);
                source.newPlan(SinglePersonMortalityAnalysisTest.plan()); return null;
            });
            rig.execute(); fx(() -> { assertNull(source.getCurrentPlan().getAnalyzerConfigurations()); return null; });
            assertEquals(accepted, new JsonRetirementPlanRepository().load(dir.resolve("plan.json")).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { stop(source); }
    }

    @Test void sharedCoupleControlsAndIndependentIndividualControlsRestoreTheirOwnConfigurations() throws Exception {
        for (boolean couple : List.of(true, false)) {
            var kindA = couple ? AnalysisKind.COUPLE_SOCIAL_SECURITY : AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
            var kindB = couple ? AnalysisKind.COUPLE_WEIGHTED : AnalysisKind.INDIVIDUAL_WEIGHTED;
            var source = source(kindA, new JsonRetirementPlanRepository());
            try (var rig = fx(() -> new Rig(source))) {
                fx(() -> { rig.configure(kindA); rig.run(kindA); return null; }); rig.execute(); await(rig.binding(kindA).status, "SAVED");
                TextField original = fx(() -> rig.editedField(kindA));
                fx(() -> { rig.configure(kindB); rig.editedField(kindB).setText("0.8500"); rig.run(kindB); return null; });
                rig.execute(); await(rig.binding(kindB).status, "SAVED");
                fx(() -> {
                    rig.select(kindA); assertEquals("1.1500", rig.editedField(kindA).getText());
                    rig.select(kindB); assertEquals("0.8500", rig.editedField(kindB).getText());
                    if (couple) assertSame(original, rig.editedField(kindB)); else assertNotSame(original, rig.editedField(kindB));
                    return null;
                });
            } finally { stop(source); }
        }
    }

    @Test void optionalBaselineStatesAndBoundedUnavailableTextRemainDistinct() throws Exception {
        fx(() -> {
            var view = new CurrentStrategyBaselineView(ExhaustivePersistenceTest.plan());
            for (var state : AnalysisParameterConfiguration.OptionalElection.State.values()) {
                var election = new AnalysisParameterConfiguration.OptionalElection(state,
                        state == AnalysisParameterConfiguration.OptionalElection.State.VALUE ? " 67 "
                                : state == AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE ? "unavailable original" : null,
                        "Analyzer original", state == AnalysisParameterConfiguration.OptionalElection.State.VALUE ? 67 : null);
                var baseline = new AnalysisParameterConfiguration.Baseline(election, election);
                view.restoreParameters(baseline); assertEquals(baseline, view.parameters(view.snapshot()));
            }
            view.primarySurvivor.setText("67" + " ".repeat(300) + "invalid");
            var bounded = view.parameters(view.snapshot()).primary();
            assertEquals(AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE, bounded.state());
            assertTrue(bounded.originalText().length() <= 256); assertTrue(bounded.originalText().contains("unavailable"));
            return null;
        });
    }

    @Test void retryDoesNotCalculateOrSaveDirtyFinancesAndSaveAsCopiesIndependentConfigurations() throws Exception {
        var failed = new AtomicBoolean(true);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID identity, AnalysisParameterConfiguration configuration) throws java.io.IOException {
                if (failed.get()) throw new java.io.IOException("Injected failure"); super.saveParameters(path, identity, configuration);
            }
        };
        var kind = AnalysisKind.COUPLE_EXHAUSTIVE; var source = source(kind, repository);
        try (var rig = fx(() -> new Rig(source))) {
            var balance = source.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().getCurrentBalance();
            fx(() -> {
                set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence);
                source.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(balance.add(BigDecimal.ONE)); source.markModified();
                rig.configure(kind); rig.run(kind); return null;
            }); rig.execute(); await(rig.binding(kind).status, "SAVE FAILED");
            var accepted = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow();
            int calls = rig.calculations.get(); long revision = source.getSourcePlanRevision();
            failed.set(false); fx(() -> { rig.binding(kind).retry.fire(); return null; }); await(rig.binding(kind).status, "SAVED");
            assertEquals(calls, rig.calculations.get()); assertEquals(revision, source.getSourcePlanRevision()); assertTrue(source.isModified());
            assertEquals(balance, new JsonRetirementPlanRepository().load(dir.resolve("plan.json")).getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
            var originalId = source.getCurrentPlan().getPlanId();
            fx(() -> { set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence); source.saveAs(dir.resolve("copy.json")); return null; });
            var copy = new JsonRetirementPlanRepository().load(dir.resolve("copy.json"));
            assertNotEquals(originalId, copy.getPlanId()); assertEquals(accepted, copy.getAnalyzerConfigurations().get(kind).orElseThrow());
            assertEquals(accepted, new JsonRetirementPlanRepository().load(dir.resolve("plan.json")).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { stop(source); }
    }

    @Test void individualFactorBelowCoupleBoundsAndInvalidDateEditorRestoreExactly() throws Exception {
        var kind = AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY; var source = source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new Rig(source))) {
            fx(() -> { rig.configure(kind); rig.mortality(kind).factor.setText("0.2500"); rig.run(kind); return null; });
            rig.execute(); await(rig.binding(kind).status, "SAVED");
            fx(() -> {
                var view = rig.mortality(kind); var date = view.valuation.getValue();
                view.valuation.getEditor().setText("invalid date draft");
                rig.binding(kind).restore();
                assertEquals(date, WeightedResultPersistence.date(view.valuation)); assertEquals("0.2500", view.factor.getText());
                assertTrue(view.canExportPdf(), "Restored prior accepted inputs use the existing financial fingerprint");
                return null;
            });
        } finally { stop(source); }
    }

    @Test void pendingWriteSurvivesDialogCloseOrdinarySaveAndIndependentSaveAs() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration configuration) throws java.io.IOException {
                entered.countDown();
                try { if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Injected timeout"); }
                catch (InterruptedException e) { throw new java.io.IOException(e); }
                super.saveParameters(path, id, configuration);
            }
        };
        var kind = AnalysisKind.COUPLE_EXHAUSTIVE; var source = source(kind, repository);
        var rig = fx(() -> new Rig(source));
        try {
            fx(() -> { rig.configure(kind); rig.run(kind); return null; }); rig.execute();
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var originalId = source.getCurrentPlan().getPlanId();
            fx(() -> { set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence); source.save(); source.saveAs(dir.resolve("independent.json")); return null; });
            rig.close(); release.countDown(); assertTrue(source.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            var original = new JsonRetirementPlanRepository().load(dir.resolve("plan.json"));
            var copy = new JsonRetirementPlanRepository().load(dir.resolve("independent.json"));
            assertEquals(originalId, original.getPlanId()); assertNotEquals(originalId, copy.getPlanId());
            assertEquals(original.getAnalyzerConfigurations().get(kind), copy.getAnalyzerConfigurations().get(kind));
        } finally { release.countDown(); rig.close(); stop(source); }
    }

    @Test void identicalNewIndividualPlanCannotInheritPriorPlansCurrentResult() throws Exception {
        var kind = AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY; var source = source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new Rig(source))) {
            fx(() -> {
                rig.select(kind); rig.mortality(kind).factor.setText("1.00"); rig.mortality(kind).discount.setText("1.0");
                rig.run(kind); return null;
            }); rig.execute(); await(rig.binding(kind).status, "SAVED");
            fx(() -> {
                assertTrue(rig.mortality(kind).canExportPdf());
                long generation = rig.jobs.generation();
                source.newPlan(SinglePersonMortalityAnalysisTest.plan());
                assertNull(source.getCurrentPlan().getAnalyzerConfigurations());
                assertFalse(rig.mortality(kind).canExportPdf(), "A different plan must not inherit current memory-only results");
                assertEquals(generation, rig.jobs.generation()); assertTrue(rig.worker.queue.isEmpty());
                return null;
            });
        } finally { stop(source); }
    }

    @Test void neverSavedPlanKeepsParametersInMemoryUntilExplicitSave() throws Exception {
        var kind = AnalysisKind.COUPLE_EXHAUSTIVE; var source = source(kind, new JsonRetirementPlanRepository());
        fx(() -> { source.newPlan(ExhaustivePersistenceTest.plan()); return null; });
        var identity = source.getCurrentPlan().getPlanId();
        try (var rig = fx(() -> new Rig(source))) {
            fx(() -> { rig.configure(kind); rig.run(kind); return null; }); rig.execute(); await(rig.binding(kind).status, "MEMORY ONLY");
            assertNull(source.getCurrentFile()); assertNotNull(identity); assertEquals(identity, source.getCurrentPlan().getPlanId());
            var accepted = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow();
            fx(() -> { set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence); source.saveAs(dir.resolve("first-save.json")); return null; });
            assertEquals(accepted, new JsonRetirementPlanRepository().load(dir.resolve("first-save.json")).getAnalyzerConfigurations().get(kind).orElseThrow());
            assertEquals(1, rig.calculations.get());
        } finally { stop(source); }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_SOCIAL_SECURITY", "INDIVIDUAL_SOCIAL_SECURITY"})
    void switchingHouseholdShapeClosesOldOwnerAndRejectsInFlightCompletion(AnalysisKind kind) throws Exception {
        var source = source(kind, new JsonRetirementPlanRepository());
        try (var rig = fx(() -> new Rig(source))) {
            fx(() -> {
                rig.configure(kind); rig.run(kind);
                if (rig.individual == null) set(rig.dialog, "socialSecurityPersistence", rig.originalSocialSecurityPersistence);
                source.newPlan(kind.shape() == AnalysisKind.Shape.COUPLE ? SinglePersonMortalityAnalysisTest.plan() : ExhaustivePersistenceTest.plan());
                assertEquals(SocialSecurityAnalyzerJobController.State.CLOSED, rig.jobs.state());
                return null;
            }); rig.execute();
            assertNull(source.getCurrentPlan().getAnalyzerConfigurations()); assertEquals(0, rig.calculations.get());
            assertNull(new JsonRetirementPlanRepository().load(dir.resolve("plan.json")).getAnalyzerConfigurations());
        } finally { stop(source); }
    }
}
