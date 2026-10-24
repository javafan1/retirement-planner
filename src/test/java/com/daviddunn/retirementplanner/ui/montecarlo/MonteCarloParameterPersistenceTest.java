package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;

class MonteCarloParameterPersistenceTest {
    @TempDir Path dir;
    @BeforeAll static void init() throws Exception { startFx(); }
    ApplicationController source(AnalysisKind kind, String name, JsonRetirementPlanRepository repository) throws Exception {
        var source = new ApplicationController(repository, new JsonApplicationSettingsRepository(dir.resolve(name + "-settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        fx(() -> {
            var plan = kind.shape() == AnalysisKind.Shape.COUPLE ? MonteCarloMortalityUiFixtures.plan() : SinglePersonMortalityAnalysisTest.plan();
            plan.setBaseline(ProjectionBaselineFactory.create(new RetirementPlanScenarioCopyService().copy(plan), "Baseline"));
            source.newPlan(plan); source.saveAs(dir.resolve(name + ".json")); return null;
        });
        return source;
    }
    static final class View implements AutoCloseable {
        final VBox node; final String prefix; final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        final AtomicInteger calls = new AtomicInteger(); final AtomicBoolean fail = new AtomicBoolean();
        View(ApplicationController source, AnalysisKind kind, boolean throwing) {
            boolean paired = kind.name().contains("PAIRED"); prefix = paired ? "#mcc-" : "#mc-";
            node = paired ? new MonteCarloStrategyComparisonView(source, tasks::add, (request, progress, cancel) -> {
                calls.incrementAndGet(); if (throwing) throw new AssertionError("Restore cannot simulate"); if (fail.get()) throw new IllegalStateException("Injected failure");
                return new MonteCarloStrategyComparisonRunService().run(request, progress, cancel);
            }) : new MonteCarloAnalysisView(source, tasks::add, (plan, settings, reference, progress, cancel) -> {
                calls.incrementAndGet(); if (throwing) throw new AssertionError("Restore cannot project"); if (fail.get()) throw new IllegalStateException("Injected failure");
                return new MonteCarloRunService().run(plan, settings, reference, progress, cancel);
            }, (plan, request, progress, cancel) -> {
                calls.incrementAndGet(); if (throwing) throw new AssertionError("Restore cannot simulate longevity"); if (fail.get()) throw new IllegalStateException("Injected failure");
                return new MonteCarloRunService().runMortality(plan, request, progress, cancel);
            });
            new Scene(node);
        }
        @SuppressWarnings("unchecked") void mode(boolean longevity) { ((ComboBox<MonteCarloMode>) node.lookup(prefix + "mode")).setValue(longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN); }
        @SuppressWarnings("unchecked") void configure(AnalysisKind kind) {
            mode(kind.name().endsWith("LONGEVITY"));
            ((ComboBox<Integer>) node.lookup(prefix + (prefix.equals("#mcc-") ? "count" : "simulations"))).setValue(2);
            text("return").setText("5.1250"); text("volatility").setText("12.2500"); text("seed").setText(Integer.toString(417 + kind.ordinal()));
            ((ComboBox<String>) node.lookup(prefix + (prefix.equals("#mcc-") ? "inflation" : "inflation-mode"))).setValue("Stochastic");
            text("inflation-mean").setText("2.7500"); text("inflation-volatility").setText("1.7500"); text("inflation-floor").setText("-2.0000");
            if (kind.name().endsWith("LONGEVITY")) {
                text(prefix.equals("#mcc-") ? "primary" : "primary-adjustment").setText("1.0000");
                if (kind.shape() == AnalysisKind.Shape.COUPLE) {
                    text(prefix.equals("#mcc-") ? "spouse" : "spouse-adjustment").setText("0.9500");
                    text(prefix.equals("#mcc-") ? "survivor-a" : "survivor-age").setText("67");
                    if (prefix.equals("#mcc-")) text("survivor-b").setText("68");
                }
            }
        }
        TextField text(String id) { return (TextField) node.lookup(prefix + id); }
        Button button(String id) { return (Button) node.lookup(prefix + id); }
        Label label(String id) { return (Label) node.lookup(prefix + id); }
        Object result() { return node instanceof MonteCarloAnalysisView v ? v.session().result() : ((MonteCarloStrategyComparisonView) node).session().result(); }
        void execute() { Objects.requireNonNull(tasks.poll(), "Expected one admitted analysis").run(); }
        @Override public void close() throws Exception { ((AutoCloseable) node).close(); }
    }
    static void await(Label label, String text) throws Exception {
        var done = new CompletableFuture<Void>();
        fx(() -> { if (label.getText().contains(text)) done.complete(null);
            else label.textProperty().addListener((o, a, b) -> { if (b.contains(text)) done.complete(null); }); return null; });
        done.get(20, TimeUnit.SECONDS);
    }
    static void stop(ApplicationController source) throws Exception {
        source.getAnalysisParameters().drain(Duration.ofSeconds(10)); source.getAnalysisParameters().shutdown();
        assertTrue(source.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit()); source.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }
    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void acceptedRequestAutomaticallyPersistsAndNewControllerRestoresExactly(AnalysisKind kind) throws Exception {
        var source = source(kind, "original", new JsonRetirementPlanRepository());
        View view = fx(() -> new View(source, kind, false));
        AnalysisParameterConfiguration accepted;
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); return null; }); view.execute();
            await(view.label("parameters-status"), "SAVED");
            accepted = source.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow();
            var p = (AnalysisParameterConfiguration.MonteCarlo) accepted.parameters();
            assertEquals(new BigDecimal("0.051250"), p.expectedReturnRate()); assertEquals(new BigDecimal("-0.020000"), p.inflation().floorRate());
            assertEquals(2, p.simulations()); assertEquals(417 + kind.ordinal(), p.seed());
            if (kind.name().endsWith("LONGEVITY")) assertEquals(new BigDecimal("1.0000"), p.primaryMortalityFactor());
            if (kind == AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY) assertEquals(68, p.baselineSurvivorClaimingAge());
            assertFalse(source.isModified());
            assertEquals(accepted, new JsonRetirementPlanRepository().load(source.getCurrentFile()).getAnalyzerConfigurations().get(kind).orElseThrow());
            var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
            assertEquals(accepted.acceptedRunId(), source.getAnalysisResults().inMemory(slot).orElseThrow().runId());
            assertEquals(accepted.acceptedAt(), source.getAnalysisResults().inMemory(slot).orElseThrow().completedAt());
            assertEquals(1, view.calls.get());
        } finally { fx(() -> { view.close(); return null; }); stop(source); }
        var restored = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("restart-settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        fx(() -> { restored.open(dir.resolve("original.json")); return null; });
        var reopened = fx(() -> { var v = new View(restored, kind, true); v.mode(kind.name().endsWith("LONGEVITY")); return v; });
        try {
            assertEquals(accepted, restored.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow());
            fx(() -> {
                assertEquals("5.1250", reopened.text("return").getText()); assertEquals("-2.0000", reopened.text("inflation-floor").getText());
                assertEquals(Integer.toString(417 + kind.ordinal()), reopened.text("seed").getText());
                assertTrue(reopened.tasks.isEmpty()); return null;
            });
            await(reopened.label("saved-status"), "Current Results");
            if (Boolean.getBoolean("parameters.preview")) fx(() -> { preview(reopened, kind); return null; });
            fx(() -> { reopened.text("inflation-floor").setText("-2.00000"); return null; });
            await(reopened.label("saved-status"), "Current Results");
            fx(() -> { reopened.text("inflation-floor").setText("-1.99"); return null; });
            await(reopened.label("saved-status"), "Outdated");
            fx(() -> { reopened.text("inflation-floor").setText("-2.0000"); return null; });
            await(reopened.label("saved-status"), "Current Results"); assertEquals(0, reopened.calls.get());
        } finally { fx(() -> { reopened.close(); return null; }); stop(restored); }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void cancelFailureAndLaterEditsKeepPriorAcceptedConfiguration(AnalysisKind kind) throws Exception {
        var source = source(kind, "plan", new JsonRetirementPlanRepository()); var view = fx(() -> new View(source, kind, false));
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); return null; }); view.execute(); await(view.label("parameters-status"), "SAVED");
            var accepted = source.getCurrentPlan().getAnalyzerConfigurations().get(kind).orElseThrow(); var result = fx(view::result);
            fx(() -> { view.text("seed").setText("999"); view.button("run").fire(); view.button("cancel").fire(); return null; });
            view.execute(); await(view.label("status"), "cancelled");
            fx(() -> { assertEquals(Long.toString(((AnalysisParameterConfiguration.MonteCarlo) accepted.parameters()).seed()), view.text("seed").getText()); assertSame(result, view.result()); return null; });
            view.fail.set(true);
            fx(() -> { view.text("seed").setText("1000"); view.button("run").fire(); return null; }); view.execute(); await(view.label("status"), "failed");
            fx(() -> { assertEquals(Long.toString(((AnalysisParameterConfiguration.MonteCarlo) accepted.parameters()).seed()), view.text("seed").getText()); assertSame(result, view.result());
                view.text("seed").setText("invalid"); view.button("run").fire(); assertEquals("invalid", view.text("seed").getText()); assertTrue(view.tasks.isEmpty());
                view.text("seed").setText("1001"); view.button("run").fire(); view.button("cancel").fire(); view.text("seed").setText("1002"); return null; });
            view.execute(); waitForFx(); fx(() -> { assertEquals("1002", view.text("seed").getText()); return null; });
            assertEquals(accepted, new JsonRetirementPlanRepository().load(source.getCurrentFile()).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { fx(() -> { view.close(); return null; }); stop(source); }
    }

    @Test void fourCoupleModesHaveIndependentAcceptedSettingsAndDiscardUnacceptedDrafts() throws Exception {
        var kinds = List.of(AnalysisKind.COUPLE_MONTE_CARLO_FIXED, AnalysisKind.COUPLE_MONTE_CARLO_LONGEVITY,
                AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_FIXED, AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY);
        var source = source(kinds.getFirst(), "modes", new JsonRetirementPlanRepository());
        try {
            for (var kind : kinds) {
                var view = fx(() -> new View(source, kind, false));
                try {
                    fx(() -> { view.configure(kind); view.button("run").fire(); return null; }); view.execute();
                    await(view.label("parameters-status"), "SAVED");
                    fx(() -> {
                        view.text("seed").setText("invalid draft"); view.mode(!kind.name().endsWith("LONGEVITY"));
                        view.mode(kind.name().endsWith("LONGEVITY")); assertEquals(Integer.toString(417 + kind.ordinal()), view.text("seed").getText());
                        assertTrue(view.tasks.isEmpty()); return null;
                    });
                } finally { fx(() -> { view.close(); return null; }); }
            }
            var loaded = new JsonRetirementPlanRepository().load(source.getCurrentFile());
            for (var kind : kinds) assertEquals(417 + kind.ordinal(), ((AnalysisParameterConfiguration.MonteCarlo) loaded.getAnalyzerConfigurations().get(kind).orElseThrow().parameters()).seed());
        } finally { stop(source); }
    }

    @Test void failedParameterWriteRetriesWithoutSimulationAndPreservesDirtyFinancesAcrossReopen() throws Exception {
        var failing = new AtomicBoolean(true);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path file, UUID id, AnalysisParameterConfiguration configuration) throws java.io.IOException {
                if (failing.get()) throw new java.io.IOException("Injected parameter write failure"); super.saveParameters(file, id, configuration);
            }
        };
        var kind = AnalysisKind.COUPLE_MONTE_CARLO_FIXED; var source = source(kind, "retry", repository);
        var view = fx(() -> new View(source, kind, false));
        try {
            var originalBalance = source.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().getCurrentBalance();
            fx(() -> {
                source.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(originalBalance.add(BigDecimal.ONE)); source.markModified();
                view.configure(kind); view.button("run").fire(); return null;
            });
            long revision = source.getSourcePlanRevision(); view.execute(); await(view.label("parameters-status"), "SAVE FAILED");
            var accepted = source.getAnalysisParameters().lastAccepted(source.getCurrentPlan(), kind).orElseThrow();
            assertTrue(source.isModified()); assertEquals(revision, source.getSourcePlanRevision());
            assertEquals(originalBalance, new JsonRetirementPlanRepository().load(source.getCurrentFile()).getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
            Path original = source.getCurrentFile();
            fx(() -> { source.newPlan(); source.open(original); return null; }); waitForFx();
            fx(() -> { assertEquals(Long.toString(((AnalysisParameterConfiguration.MonteCarlo) accepted.parameters()).seed()), view.text("seed").getText()); return null; });
            failing.set(false); fx(() -> { view.button("retry-parameters-save").fire(); return null; }); await(view.label("parameters-status"), "SAVED");
            assertEquals(1, view.calls.get());
            assertEquals(accepted, new JsonRetirementPlanRepository().load(original).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { fx(() -> { view.close(); return null; }); stop(source); }
    }

    @Test void acceptedWriteSurvivesDialogCloseAndPendingSaveAsWithoutRedirect() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration configuration) throws java.io.IOException {
                entered.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("Timed out"); }
                catch (InterruptedException e) { throw new java.io.IOException(e); } super.saveParameters(path, id, configuration);
            }
        };
        var kind = AnalysisKind.COUPLE_MONTE_CARLO_FIXED; var source = source(kind, "pending", repository);
        var view = fx(() -> new View(source, kind, false));
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); return null; }); view.execute(); assertTrue(entered.await(10, TimeUnit.SECONDS));
            var original = source.getCurrentFile(); var originalId = source.getCurrentPlan().getPlanId();
            fx(() -> { source.save(); source.saveAs(dir.resolve("independent.json")); view.close(); return null; });
            assertNotEquals(originalId, source.getCurrentPlan().getPlanId());
            release.countDown(); assertTrue(source.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            var originalPlan = new JsonRetirementPlanRepository().load(original);
            var copy = new JsonRetirementPlanRepository().load(source.getCurrentFile());
            assertEquals(originalId, originalPlan.getPlanId());
            assertEquals(originalPlan.getAnalyzerConfigurations().get(kind), copy.getAnalyzerConfigurations().get(kind));
            assertEquals(1, view.calls.get());
        } finally { release.countDown(); fx(() -> { view.close(); return null; }); stop(source); }
    }

    @Test void obsoletePlanCompletionAndClosedDialogCannotAcceptOrRollbackParameters() throws Exception {
        var kind = AnalysisKind.COUPLE_MONTE_CARLO_FIXED; var source = source(kind, "obsolete", new JsonRetirementPlanRepository());
        var view = fx(() -> new View(source, kind, false));
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); source.newPlan(); return null; });
            view.execute(); waitForFx();
            assertNull(source.getCurrentPlan().getAnalyzerConfigurations());
            fx(() -> { assertEquals("417", view.text("seed").getText()); view.close(); return null; });
            assertNull(new JsonRetirementPlanRepository().load(dir.resolve("obsolete.json")).getAnalyzerConfigurations());
        } finally { fx(() -> { view.close(); return null; }); stop(source); }
    }
    static void preview(View view, AnalysisKind kind) throws Exception {
        var original = view.node.getScene(); original.setRoot(new javafx.scene.layout.Pane());
        var scroll = new ScrollPane(view.node); scroll.setFitToWidth(true);
        var stage = new javafx.stage.Stage(); stage.setScene(new Scene(scroll, 900, 650)); stage.show();
        try {
            scroll.applyCss(); scroll.layout(); var image = stage.getScene().snapshot(null);
            var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Path output = Path.of("target", "phase10c-previews", kind.name() + ".png"); Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(pixels, "png", output.toFile());
        } finally { stage.close(); scroll.setContent(null); original.setRoot(view.node); }
    }
}
