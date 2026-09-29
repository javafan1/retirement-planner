package com.daviddunn.retirementplanner.ui.montecarlo;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;

@EnabledIfSystemProperty(named = "montecarlo.comparison.preview", matches = "true")
class MonteCarloComparisonPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void actualFiveThousandWorldPreview(boolean longevity) throws Exception {
        var finished = new CompletableFuture<MonteCarloStrategyComparisonRun>();
        var stage = new Stage[1];
        var view = fx(() -> {
            var v = new MonteCarloStrategyComparisonView(new Controller(MonteCarloComparisonFixtures.plan()));
            var scroll = new ScrollPane(v); scroll.setFitToWidth(true);
            var root = new BorderPane(scroll); root.setBottom(new Button("Close"));
            stage[0] = new Stage(); stage[0].setScene(new Scene(root, 1900, 1040)); stage[0].show();
            if (longevity) ((ComboBox<MonteCarloMode>) v.lookup("#mcc-mode")).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            ((ComboBox<String>) v.lookup("#mcc-inflation")).setValue("Stochastic");
            ((Label) v.lookup("#mcc-status")).textProperty().addListener((o, old, text) -> {
                if (v.session().state() == MonteCarloStrategyComparisonSession.State.COMPLETED) finished.complete(v.session().result());
                if (v.session().state() == MonteCarloStrategyComparisonSession.State.FAILED) finished.completeExceptionally(v.session().failure());
            });
            ((Button) v.lookup("#mcc-run")).fire(); return v;
        });
        try {
            var result = finished.get(180, TimeUnit.SECONDS);
            assertEquals(5000, result.result().requestedPairedCount());
            String kind = longevity ? "longevity" : "fixed";
            fx(() -> {
                var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
                chart.select(Math.min(15, chart.years().size() - 1)); chart.requestFocus(); return null;
            });
            awaitLayoutPulses(stage[0].getScene());
            fx(() -> {
                snapshot(stage[0].getScene(), "target/phase4c-" + kind + ".png");
                geometry(view, "target/phase4c-" + kind + "-geometry.txt");
                assertFalse(((TitledPane) view.lookup("#mcc-details")).isExpanded());
                assertFalse(((Label) view.lookup("#mcc-selected-year")).isTextTruncated());
                if (longevity) assertTrue(((Label) view.lookup("#mcc-terminal-notice")).getText().contains("not discounted"));
                return null;
            });
            if (longevity) {
                fx(() -> {
                    var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
                    int lastSmall = -1;
                    for (int i = 0; i < chart.years().size(); i++) {
                        if (MonteCarloStrategyComparisonPresentation.smallSample(chart.years().get(i))) { lastSmall = i; break; }
                    }
                    assertTrue(lastSmall >= 0); chart.select(lastSmall); return null;
                });
                awaitLayoutPulses(stage[0].getScene());
                fx(() -> {
                    snapshot(stage[0].getScene(), "target/phase4c-longevity-late.png");
                    geometry(view, "target/phase4c-longevity-late-geometry.txt");
                    assertTrue(view.lookup("#mcc-small-sample").isVisible()); return null;
                });
                fx(() -> {
                    var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
                    for (int i = 0; i < chart.years().size(); i++) if (chart.years().get(i).year() == 2034) chart.select(i);
                    return null;
                });
                awaitLayoutPulses(stage[0].getScene());
                fx(() -> {
                    snapshot(stage[0].getScene(), "target/phase4c-longevity-2034.png");
                    geometry(view, "target/phase4c-longevity-2034-geometry.txt");
                    return null;
                });
                var dialog = fx(() -> {
                    ((ScrollPane) ((BorderPane) stage[0].getScene().getRoot()).getCenter()).setContent(null);
                    var d = new MonteCarloStrategyComparisonDialog(stage[0], view); d.show(); return d;
                });
                try {
                    awaitLayoutPulses(fx(() -> view.getScene()));
                    fx(() -> {
                        snapshot(view.getScene(), "target/phase4c-real-dialog-2034.png");
                        geometry(view, "target/phase4c-real-dialog-2034-geometry.txt");
                        return null;
                    });
                    fx(() -> {
                        var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
                        for (int i = 0; i < chart.years().size(); i++) {
                            if (MonteCarloStrategyComparisonPresentation.smallSample(chart.years().get(i))) { chart.select(i); break; }
                        }
                        return null;
                    });
                    awaitLayoutPulses(fx(() -> view.getScene()));
                    fx(() -> {
                        snapshot(view.getScene(), "target/phase4c-real-dialog-late.png");
                        geometry(view, "target/phase4c-real-dialog-late-geometry.txt");
                        assertTrue(view.lookup("#mcc-small-sample").isVisible()); return null;
                    });
                } finally { fx(() -> { dialog.close(); return null; }); }
            }
            Files.writeString(Path.of("target/phase4c-" + kind + "-performance.txt"),
                    "5000 paired worlds; UI worker including backend and cached summary: " + result.elapsedNanos() / 1e9 + " seconds\n");
        } finally { fx(() -> { view.close(); stage[0].close(); return null; }); }
    }

    private static void geometry(MonteCarloStrategyComparisonView view, String path) throws Exception {
        var viewport = view.getScene().lookup(".viewport");
        var visible = viewport.localToScene(viewport.getBoundsInLocal());
        var text = new StringBuilder("Scene: " + view.getScene().getWidth() + " x " + view.getScene().getHeight() + "\nViewport: " + visible + "\n");
        for (String id : new String[]{"mcc-run", "mcc-funding", "mcc-funding-a", "mcc-funding-b", "mcc-funding-difference", "mcc-metrics", "mcc-terminal-notice", "mc-comparison-chart", "mcc-selected-year", "mcc-small-sample", "mcc-details"}) {
            var n = view.lookup("#" + id);
            var bounds = n.localToScene(n.getBoundsInLocal());
            text.append(id).append(": ").append(bounds).append('\n');
            if (n.isVisible()) {
                assertTrue(bounds.getMinX() >= visible.getMinX() && bounds.getMaxX() <= visible.getMaxX(), id + " horizontal: " + bounds);
                assertTrue(bounds.getMinY() >= visible.getMinY() && bounds.getMaxY() <= visible.getMaxY(), id + " vertical: " + bounds + " viewport: " + visible);
            }
        }
        var selected = (Label) view.lookup("#mcc-selected-year");
        assertEquals(2, selected.getText().split("\n").length);
        assertFalse(selected.isTextTruncated(), "Both selected-year lines must be fully rendered");
        var selectedText = selected.lookup(".text");
        assertTrue(selectedText.localToScene(selectedText.getBoundsInLocal()).getMaxY() <= visible.getMaxY(), "Population line bottom inside viewport");
        var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
        var axisTitle = chart.valueAxis().lookup(".axis-label");
        var titleBounds = axisTitle.localToScene(axisTitle.getBoundsInLocal());
        var chartBounds = chart.localToScene(chart.getBoundsInLocal());
        assertTrue(chartBounds.contains(titleBounds), "Full Y-axis title inside chart: " + titleBounds);
        assertFalse(((Label) axisTitle).isTextTruncated());
        var zero = (javafx.scene.shape.Line) chart.lookup("#mc-comparison-zero");
        assertEquals(chart.valueAxis().localToScene(0, chart.valueAxis().getDisplayPosition(0)).getY(),
                zero.localToScene(0, zero.getStartY()).getY(), 0.01);
        assertTrue(zero.getStrokeWidth() > 2); assertFalse(zero.getStrokeDashArray().isEmpty());
        var table = (TableView<?>) view.lookup("#mcc-metrics");
        assertEquals("P(Current > Baseline)", table.getColumns().get(3).getText());
        assertTrue(table.getAccessibleText().contains("Probability Current Plan exceeds Saved Baseline"));
        for (var node : view.getScene().getRoot().lookupAll(".scroll-bar")) {
            var bar = (ScrollBar) node;
            if (bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) assertFalse(bar.isVisible(), "No horizontal scrolling");
        }
        var a = view.lookup("#mcc-funding-a").getParent();
        var b = view.lookup("#mcc-funding-b").getParent();
        var difference = view.lookup("#mcc-funding-difference").getParent();
        assertTrue(a.localToScene(a.getBoundsInLocal()).getMaxX() < b.localToScene(b.getBoundsInLocal()).getMinX());
        assertTrue(b.localToScene(b.getBoundsInLocal()).getMaxX() < difference.localToScene(difference.getBoundsInLocal()).getMinX());
        assertFalse(((TitledPane) view.lookup("#mcc-details")).isExpanded());
        text.append("Both selected-year lines, full axis title, zero pixel, explicit header and collapsed details: PASS\n");
        Files.writeString(Path.of(path), text);
    }

    private static void snapshot(Scene scene, String destination) throws Exception {
        var image = scene.getRoot().snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", Path.of(destination).toFile());
    }
}
