package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;

/**
 * Explicit real-work desktop preview; normal regression tests use bounded deterministic fixtures.
 */
@EnabledIfSystemProperty(named = "montecarlo.preview", matches = "true")
class MonteCarloPreviewTest {
    @BeforeAll
    static void init() throws Exception {
        startFx();
    }

    @Test
    void representativeFiveThousandRunAndDesktopScreenshot() throws Exception {
        var plan = MonteCarloFixtures.household();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        var finished = new CompletableFuture<MonteCarloRun>();
        var updates = new AtomicInteger();
        var stage = new Stage[1];
        var started = new long[1];
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(plan));
            var scroll = new ScrollPane(created);
            scroll.setFitToWidth(true);
            var close = new Button("Close");
            close.setOnAction(event -> stage[0].close());
            var root = new BorderPane(scroll);
            root.setBottom(close);
            stage[0] = new Stage();
            stage[0].setTitle("Monte Carlo Retirement Analysis");
            stage[0].setScene(new Scene(root, 1900, 1040));
            stage[0].show();
            ((ProgressBar) created.lookup("#mc-progress")).progressProperty().addListener((o, a, b) -> updates.incrementAndGet());
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.COMPLETED) {
                    finished.complete(created.session().result());
                }
                if (created.session().state() == MonteCarloSession.State.FAILED) {
                    finished.completeExceptionally(created.session().failure());
                }
            });
            started[0] = System.nanoTime();
            button(created, "#mc-run").fire();
            assertTrue(created.session().busy());
            return created;
        });
        try {
            var result = finished.get(120, TimeUnit.SECONDS);
            assertEquals(5000, result.result().settings().simulationCount());
            assertTrue(updates.get() > 2);
            assertEquals(before, mapper.writeValueAsString(plan));
            awaitLayoutPulses(stage[0].getScene());
            var geometry = new StringBuilder();
            // Inspect boundaries as well as an unmistakable interior year; capture only the latter.
            for (int year : new int[]{2027, 2028, 2056, 2042}) {
                fx(() -> {
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    chart.requestFocus();
                    key(chart, javafx.scene.input.KeyCode.HOME);
                    for (int index = 2027; index < year; index++) {
                        key(chart, javafx.scene.input.KeyCode.RIGHT);
                    }
                    return null;
                });
                awaitLayoutPulses(stage[0].getScene());
                fx(() -> {
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    assertGuideMatchesYear(chart, year);
                    var guide = (javafx.scene.shape.Line) chart.lookup(".mc-active-year");
                    geometry.append("Year ").append(year).append(" axis X: ")
                            .append(chart.getXAxis().getDisplayPosition(year))
                            .append(" guide X: ").append(guide.getStartX()).append("\n");
                    return null;
                });
            }
            fx(() -> {
                var root = stage[0].getScene().getRoot();
                root.applyCss();
                root.layout();
                var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                assertGuideMatchesYear(chart, 2042);
                assertTrue(chart.isFocused());
                assertTrue(view.lookup(".mc-active-year").isVisible());
                assertNotNull(view.lookup(".mc-outer-band"));
                assertNotNull(view.lookup(".mc-inner-band"));
                assertFalse(view.lookupAll(".timeline-claim-label").isEmpty());
                assertFalse(view.lookupAll(".projection-period-label").isEmpty());
                var image = root.snapshot(null, null);
                var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) {
                    for (int x = 0; x < png.getWidth(); x++) {
                        png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    }
                }
                javax.imageio.ImageIO.write(png, "png", Path.of("target/monte-carlo-phase2-preview.png").toFile());
                Files.writeString(Path.of("target/monte-carlo-phase2-preview.txt"),
                        "Simulations: 5000\nWorker seconds: " + result.elapsedNanos() / 1_000_000_000.0
                                + "\nRun-to-render seconds: " + (System.nanoTime() - started[0]) / 1_000_000_000.0
                                + "\nFX progress changes: " + updates.get()
                                + "\nCompleted: " + result.result().completedCount()
                                + "\nFunding failures: " + result.result().fundingFailureCount() + "\n"
                                + geometry);
                return null;
            });
        } finally {
            fx(() -> {
                view.close();
                stage[0].close();
                return null;
            });
        }
    }
}
