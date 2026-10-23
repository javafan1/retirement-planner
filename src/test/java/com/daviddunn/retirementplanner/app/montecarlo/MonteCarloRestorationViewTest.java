package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloRestorationViewTest {
    @TempDir Path dir;
    @BeforeAll static void init() throws Exception {
        MonteCarloResultLifecycleTest.calculateIndependentFixturesOnce();
        var started = new CompletableFuture<Void>();
        Runnable initialize = () -> { Platform.setImplicitExit(false); started.complete(null); };
        try { Platform.startup(initialize); } catch (IllegalStateException running) { Platform.runLater(initialize); }
        started.get(20, TimeUnit.SECONDS);
    }
    static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<>(call); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    static class Source extends ApplicationController {
        private final RetirementPlan plan;
        private final Path file;
        private final AnalysisResultStore store;
        Source(RetirementPlan plan, Path file, AnalysisResultStore store) { this.plan = plan; this.file = file; this.store = store; }
        @Override public RetirementPlan getCurrentPlan() { return plan; }
        @Override public Path getCurrentFile() { return file; }
        @Override public UUID getAnalysisSessionId() { return UUID.fromString(plan.getPlanId()); }
        @Override public boolean canPersistAnalysisResults() { return true; }
        @Override public AnalysisResultStore getAnalysisResults() { return store; }
        @Override public Projection peekCurrentProjection() { throw new AssertionError("Restore must not request a financial reference"); }
        @Override public Projection getCurrentProjection() { throw new AssertionError("Restore must not project"); }
    }
    @SuppressWarnings("unchecked")
    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void actualViewsRestoreWithoutWorkAndKeepPendingControlsIndependent(AnalysisKind kind) throws Exception {
        var payload = MonteCarloResultLifecycleTest.FIXTURES.get(kind);
        var plan = MonteCarloPlanDocument.read(payload.request().planA());
        var id = UUID.randomUUID(); plan.assignPlanId(id);
        boolean paired = payload.paired() != null;
        if (paired) plan.setBaseline(ProjectionBaselineFactory.create(MonteCarloPlanDocument.read(payload.request().planB()), "Frozen baseline"));
        var file = dir.resolve("plan.json"); new JsonRetirementPlanRepository().save(plan, file);
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        repository.claimPlan(id, file);
        repository.save(MonteCarloResultLifecycle.completion(new MonteCarloResultLifecycle.Binding(id, file, kind), payload)
                .presentationSnapshot(new AnalysisSnapshotCodec()));
        var store = new AnalysisResultStore(repository);
        var source = new Source(plan, file, store);
        var view = fx(() -> {
            VBox v = paired ? new MonteCarloStrategyComparisonView(source, r -> fail("Restoration must not schedule simulation"),
                    (p, progress, cancellation) -> { throw new AssertionError("No paired calculation allowed"); })
                    : new MonteCarloAnalysisView(source, r -> fail("Restoration must not schedule simulation"),
                    (p, s, reference, progress, cancellation) -> { throw new AssertionError("No fixed calculation allowed"); },
                    (p, m, progress, cancellation) -> { throw new AssertionError("No mortality calculation allowed"); });
            new Scene(v, 1180, 820);
            String prefix = paired ? "#mcc-" : "#mc-";
            ((ComboBox<Integer>) v.lookup(prefix + (paired ? "count" : "simulations"))).setValue(payload.settings().trials());
            ((TextField) v.lookup(prefix + "return")).setText(payload.settings().expectedReturn().movePointRight(2).toPlainString());
            ((TextField) v.lookup(prefix + "volatility")).setText(payload.settings().volatility().movePointRight(2).toPlainString());
            ((ComboBox<MonteCarloMode>) v.lookup(prefix + "mode")).setValue(payload.request().mortality() == null
                    ? MonteCarloMode.FIXED_LIFESPAN : MonteCarloMode.LONGEVITY_ADJUSTED);
            return v;
        });
        try {
            var completed = new CompletableFuture<Void>();
            fx(() -> {
                var label = (Label) view.lookup(paired ? "#mcc-saved-status" : "#mc-saved-status");
                label.textProperty().addListener((o, old, text) -> { if (text.startsWith("Current Results")) completed.complete(null); });
                if (label.getText().startsWith("Current Results")) completed.complete(null);
                return null;
            });
            completed.get(20, TimeUnit.SECONDS);
            var formatting = new CompletableFuture<Void>();
            fx(() -> {
                var label = (Label) view.lookup(paired ? "#mcc-saved-status" : "#mc-saved-status");
                label.textProperty().addListener((o, old, text) -> { if (text.startsWith("Current Results")) formatting.complete(null); });
                var field = (TextField) view.lookup(paired ? "#mcc-return" : "#mc-return");
                var value = new java.math.BigDecimal(field.getText());
                field.setText(value.setScale(value.scale() + 1).toPlainString());
                return null;
            });
            formatting.get(20, TimeUnit.SECONDS);
            fx(() -> {
                var status = ((Label) view.lookup(paired ? "#mcc-status" : "#mc-status")).getText();
                assertFalse(status.contains("STALE") || status.contains("Inputs or plan changed"),
                        "A matching restored result must not retain a contradictory session banner");
                return null;
            });
            if (Boolean.getBoolean("mc.lifecycle.preview")) fx(() -> { preview(view, kind, "current", 1180); return null; });
            var originalReport = fx(() -> paired ? ((MonteCarloStrategyComparisonView) view).preparePdfReport() : ((MonteCarloAnalysisView) view).preparePdfReport());
            assertEquals("Current Results", originalReport.context().getFirst());
            var outdated = new CompletableFuture<Void>();
            fx(() -> {
                var label = (Label) view.lookup(paired ? "#mcc-saved-status" : "#mc-saved-status");
                label.textProperty().addListener((o, old, text) -> { if (text.contains("Previous Results — Outdated")) outdated.complete(null); });
                ((TextField) view.lookup(paired ? "#mcc-seed" : "#mc-seed")).setText("418");
                return null;
            });
            outdated.get(20, TimeUnit.SECONDS);
            if (Boolean.getBoolean("mc.lifecycle.preview")) fx(() -> { preview(view, kind, "outdated", 900); return null; });
            fx(() -> {
                var historical = paired ? ((MonteCarloStrategyComparisonView) view).preparePdfReport() : ((MonteCarloAnalysisView) view).preparePdfReport();
                assertTrue(historical.context().getFirst().startsWith("Historical Analysis"));
                assertEquals(originalReport.chart(), historical.chart()); assertEquals(originalReport.sections(), historical.sections());
                // Selection handlers must use retained data; every calculation seam above throws.
                var chart = view.lookup(paired ? "#mc-comparison-chart" : "#monte-carlo-fan");
                assertNotNull(chart);
                for (var key : List.of(javafx.scene.input.KeyCode.END, javafx.scene.input.KeyCode.HOME,
                        javafx.scene.input.KeyCode.RIGHT, javafx.scene.input.KeyCode.LEFT))
                    chart.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                            "", "", key, false, false, false, false));
                if (paired) ((TableView<?>) view.lookup("#mcc-metrics")).getSelectionModel().selectLast();
                var selectedReport = paired ? ((MonteCarloStrategyComparisonView) view).preparePdfReport()
                        : ((MonteCarloAnalysisView) view).preparePdfReport();
                assertEquals(originalReport.sections(), selectedReport.sections());
                assertEquals(originalReport.chart(), selectedReport.chart());
                assertFalse(((Button) view.lookup(paired ? "#mcc-run" : "#mc-run")).isDisabled());
                assertEquals("418", ((TextField) view.lookup(paired ? "#mcc-seed" : "#mc-seed")).getText(), "Historical restore must not overwrite next-run inputs");
                if (paired) {
                    var restored = ((MonteCarloStrategyComparisonView) view).session().result();
                    assertEquals(payload.paired().outcomes().size(), restored.result().outcomes().size());
                    assertFalse(((TableView<?>) view.lookup("#mcc-metrics")).getItems().isEmpty());
                } else {
                    var restored = ((MonteCarloAnalysisView) view).session().result();
                    assertEquals(payload.settings().trials(), restored.settings().simulationCount());
                    assertFalse(restored.fan().years().isEmpty());
                }
                ((TextField) view.lookup(paired ? "#mcc-seed" : "#mc-seed")).setText("invalid pending seed");
                var label = (Label) view.lookup(paired ? "#mcc-saved-status" : "#mc-saved-status");
                assertTrue(label.getText().contains("Inputs Need Attention"));
                var invalidReport = paired ? ((MonteCarloStrategyComparisonView) view).preparePdfReport()
                        : ((MonteCarloAnalysisView) view).preparePdfReport();
                assertEquals(originalReport.sections(), invalidReport.sections());
                assertTrue(invalidReport.context().stream().anyMatch(t -> t.contains("inputs need attention")));
                assertNotNull(paired ? ((MonteCarloStrategyComparisonView) view).session().result() : ((MonteCarloAnalysisView) view).session().result());
                return null;
            });
        } finally {
            fx(() -> { ((AutoCloseable) view).close(); return null; });
            MonteCarloResultLifecycleTest.close(store);
        }
    }

    private static void preview(VBox view, AnalysisKind kind, String state, int width) throws Exception {
        var oldScene = view.getScene();
        if (view.getParent() instanceof ScrollPane oldScroll) oldScroll.setContent(null);
        else if (oldScene != null && oldScene.getRoot() == view) oldScene.setRoot(new javafx.scene.layout.Pane());
        var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
        var stage = new javafx.stage.Stage(); stage.setScene(new Scene(scroll, width, 820)); stage.show();
        try {
            scroll.applyCss(); scroll.layout();
            var image = stage.getScene().snapshot(null);
            var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            javax.imageio.ImageIO.write(png, "png", Path.of("target/mc-2c-" + kind.name().toLowerCase(Locale.ROOT) + "-" + state + ".png").toFile());
        } finally { stage.hide(); scroll.setContent(null); }
    }
}
