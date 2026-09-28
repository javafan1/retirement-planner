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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"fixed-deterministic", "fixed-stochastic", "longevity-stochastic"})
    void phaseThreePreviews(String kind) throws Exception {
        var finished = new CompletableFuture<MonteCarloRun>();
        var stage = new Stage[1];
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()));
            var scroll = new ScrollPane(created);
            scroll.setFitToWidth(true);
            var root = new BorderPane(scroll);
            root.setBottom(new Button("Close"));
            stage[0] = new Stage();
            stage[0].setScene(new Scene(root, 1900, 1040));
            stage[0].show();
            if (kind.startsWith("longevity")) MonteCarloMortalityViewTest.mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            if (kind.endsWith("stochastic")) MonteCarloInflationViewTest.inflationMode(created).setValue("Stochastic");
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.COMPLETED) finished.complete(created.session().result());
                if (created.session().state() == MonteCarloSession.State.FAILED) finished.completeExceptionally(created.session().failure());
            });
            button(created, "#mc-run").fire();
            return created;
        });
        try {
            var result = finished.get(120, TimeUnit.SECONDS);
            assertEquals(5000, result.settings().simulationCount());
            fx(() -> {
                var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                chart.requestFocus();
                key(chart, javafx.scene.input.KeyCode.HOME);
                for (int i = result.firstYear(); i < 2042; i++) key(chart, javafx.scene.input.KeyCode.RIGHT);
                return null;
            });
            awaitLayoutPulses(stage[0].getScene());
            fx(() -> {
                for (String id : new String[]{"#mc-run", "#mc-inflation-mode", "#mc-selected-year", "#mc-analysis-details"}) {
                    var node = view.lookup(id);
                    var bounds = node.localToScene(node.getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 1015, id + " " + bounds);
                    assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= 1900, id + " " + bounds);
                }
                assertFalse(((Label) view.lookup("#mc-selected-year")).isTextTruncated());
                snapshot(stage[0].getScene(), "target/inflation-" + kind + ".png");
                var details = (TitledPane) view.lookup("#mc-analysis-details");
                details.setAnimated(false);
                details.setExpanded(true);
                return null;
            });
            awaitLayoutPulses(stage[0].getScene());
            fx(() -> {
                ((ScrollPane) ((BorderPane) stage[0].getScene().getRoot()).getCenter()).setVvalue(1);
                return null;
            });
            awaitLayoutPulses(stage[0].getScene());
            fx(() -> {
                var details = (TitledPane) view.lookup("#mc-analysis-details");
                var bounds = details.getContent().localToScene(details.getContent().getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 1015, "Expanded details fully visible: " + bounds);
                snapshot(stage[0].getScene(), "target/inflation-" + kind + "-details.png");
                if (kind.startsWith("longevity")) {
                    details.setExpanded(false);
                    var scroll = (ScrollPane) ((BorderPane) stage[0].getScene().getRoot()).getCenter();
                    scroll.setVvalue(0);
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    key(chart, javafx.scene.input.KeyCode.HOME);
                    for (int y = result.firstYear(); y < 2070; y++) key(chart, javafx.scene.input.KeyCode.RIGHT);
                }
                return null;
            });
            if (kind.startsWith("longevity")) {
                awaitLayoutPulses(stage[0].getScene());
                fx(() -> {
                    var summary = (Label) view.lookup("#mc-selected-year");
                    assertTrue(summary.getText().contains("Small surviving sample"));
                    assertFalse(summary.isTextTruncated());
                    snapshot(stage[0].getScene(), "target/inflation-longevity-stochastic-tail.png");
                    return null;
                });
            }
        } finally {
            fx(() -> { view.close(); stage[0].close(); return null; });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void longevityAdjustedFiveThousandRunAndLateSurvivorScreenshot(boolean persistedAge) throws Exception {
        var plan = MonteCarloMortalityUiFixtures.plan();
        if (persistedAge) {
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                    a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                    new com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions(
                            com.daviddunn.retirementplanner.domain.model.DeathScenario.PRIMARY_DIES, 2030, 66),
                    a.getProjectionLengthYears(), a.getProjectionStartDate()));
        } else {
            MonteCarloMortalityUiFixtures.survivorPolicy(plan, null);
        }
        String previewKind = persistedAge ? "persisted-age" : "both-survive";
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        var finished = new CompletableFuture<MonteCarloRun>();
        var stage = new Stage[1];
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(plan));
            var scroll = new ScrollPane(created);
            scroll.setFitToWidth(true);
            var root = new BorderPane(scroll);
            root.setBottom(new Button("Close"));
            stage[0] = new Stage();
            stage[0].setScene(new Scene(root, 1900, 1040));
            stage[0].show();
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.COMPLETED) {
                    finished.complete(created.session().result());
                }
                if (created.session().state() == MonteCarloSession.State.FAILED) {
                    finished.completeExceptionally(created.session().failure());
                }
            });
            MonteCarloMortalityViewTest.mode(created).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            assertEquals(persistedAge ? "66" : "", field(created, "#mc-survivor-age").getText());
            if (!persistedAge) field(created, "#mc-survivor-age").setText("67");
            assertTrue(field(created, "#mc-survivor-age").getTooltip().getText().contains("saved retirement plan"));
            button(created, "#mc-run").fire();
            return created;
        });
        try {
            var completed = finished.get(120, TimeUnit.SECONDS);
            var result = completed.mortalityResult();
            assertEquals(5000, result.requestedSimulationCount());
            MonteCarloMortalityUiFixtures.assertPreviewTail(completed, !persistedAge);
            assertEquals(before, mapper.writeValueAsString(plan));
            int late = result.annualResults().values().stream()
                    .filter(year -> year.year() > 2050 && year.livingHouseholdCount() < 4000
                            && year.primaryOnlyAliveCount() > 0 && year.spouseOnlyAliveCount() > 0
                            && year.investableAssets().isPresent())
                    .findFirst().orElseThrow().year();
            var geometry = new StringBuilder();
            for (int year : new int[]{completed.firstYear(), late, 2067, 2068, 2069, 2070, completed.lastYear(), completed.firstYear(), late}) {
                fx(() -> {
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    chart.requestFocus();
                    key(chart, javafx.scene.input.KeyCode.HOME);
                    for (int y = completed.firstYear(); y < year; y++) key(chart, javafx.scene.input.KeyCode.RIGHT);
                    return null;
                });
                awaitLayoutPulses(stage[0].getScene());
                fx(() -> {
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    MonteCarloMortalityViewTest.assertMortalityGuide(chart, year);
                    var annual = result.annualResults().get(year).investableAssets().orElseThrow();
                    var expectedValues = java.util.List.of(annual.p10(), annual.p90(), annual.p50());
                    for (int series = 0; series < 3; series++) {
                        var point = chart.getData().get(series).getData().stream()
                                .filter(p -> p.getXValue().intValue() == year).findFirst().orElseThrow();
                        assertEquals(expectedValues.get(series), point.getYValue());
                    }
                    var summary = (Label) view.lookup("#mc-selected-year");
                    assertEquals(2, summary.getText().split("\n").length);
                    assertFalse(summary.isTextTruncated());
                    assertTrue(chart.getAccessibleText().contains(summary.getText()));
                    assertTrue(view.lookupAll(".timeline-claim-label").isEmpty());
                    assertTrue(view.lookupAll(".projection-period-label").isEmpty());
                    assertFalse(((TitledPane) view.lookup("#mc-analysis-details")).isExpanded());
                    for (String id : new String[]{"#mc-run", "#mc-cancel", "#mc-survivor-age", "#mc-selected-year", "#mc-lifetime-outcomes", "#mc-analysis-details"}) {
                        var node = view.lookup(id);
                        var bounds = node.localToScene(node.getBoundsInLocal());
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 1015, id + " vertical bounds " + bounds);
                        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= 1900, id + " horizontal bounds " + bounds);
                    }
                    var survivorLabel = view.lookupAll(".label").stream().filter(node -> node instanceof Label label
                            && "Survivor Social Security Claiming Age".equals(label.getText())).map(Label.class::cast)
                            .findFirst().orElseThrow();
                    assertFalse(survivorLabel.isTextTruncated());
                    geometry.append("Year ").append(year).append(" axis X: ")
                            .append(chart.getXAxis().getDisplayPosition(year)).append(" guide X: ")
                            .append(((javafx.scene.shape.Line) chart.lookup(".mc-active-year")).getStartX()).append('\n');
                    if (year == completed.firstYear() || year == late) {
                        snapshot(stage[0].getScene(), "target/monte-carlo-longevity-" + previewKind + "-"
                                + (year == late ? "late" : "opening") + "-preview.png");
                    }
                    if (year == 2069 || year == 2070) {
                        snapshot(stage[0].getScene(), "target/monte-carlo-longevity-" + previewKind
                                + "-" + year + "-preview.png");
                    }
                    return null;
                });
            }
            Files.writeString(Path.of("target/monte-carlo-longevity-" + previewKind + "-preview.txt"),
                    "Dimensions: 1900x1040\nSimulations: 5000\nCompleted: " + result.completedCount()
                            + "\nFunding failures: " + result.fundingFailureCount()
                            + "\nLate annual readout: " + result.annualResults().get(late)
                            + "\nReporting range: " + result.firstReportingYear() + "–" + result.lastReportingYear()
                            + "\n" + MonteCarloMortalityPresentation.terminalDates(result) + "\n" + geometry);
        } finally {
            fx(() -> { view.close(); stage[0].close(); return null; });
        }
    }

    private static void snapshot(Scene scene, String destination) throws Exception {
        var image = scene.getRoot().snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(png, "png", Path.of(destination).toFile());
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
