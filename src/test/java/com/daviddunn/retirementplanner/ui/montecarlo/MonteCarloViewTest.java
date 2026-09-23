package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloViewTest {
    static class Controller extends ApplicationController {
        private final RetirementPlan plan;

        Controller(RetirementPlan plan) {
            this.plan = plan;
        }

        @Override
        public RetirementPlan getCurrentPlan() {
            return plan;
        }

        @Override
        public Projection peekCurrentProjection() {
            return null;
        }
    }

    @BeforeAll
    static void startFx() throws Exception {
        var startup = new FutureTask<Void>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(startup);
        } catch (IllegalStateException running) {
            Platform.runLater(startup);
        }
        startup.get(30, TimeUnit.SECONDS);
    }

    static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<>(call);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    static Button button(MonteCarloAnalysisView view, String id) {
        return (Button) view.lookup(id);
    }

    static TextField field(MonteCarloAnalysisView view, String id) {
        return (TextField) view.lookup(id);
    }

    static void waitForFx() throws Exception {
        fx(() -> null);
    }

    @Test
    void defaultsValidationAndInputsRemainSessionOnly() throws Exception {
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        fx(() -> {
            var queued = new ArrayDeque<Runnable>();
            var view = new MonteCarloAnalysisView(new Controller(plan), queued::add, (p, s, r, updates, cancel) -> {
                fail("Invalid request should never run");
                return null;
            });
            try {
                new Scene(view);
                assertFalse(((TitledPane) view.lookup("#mc-analysis-details")).isExpanded());
                assertTrue(field(view, "#mc-return").getTooltip().getText().contains("arithmetic mean annual return"));
                assertTrue(field(view, "#mc-return").getTooltip().getText().contains("median compounded outcome"));
                assertEquals(5000, ((ComboBox<?>) view.lookup("#mc-simulations")).getValue());
                assertEquals("12.00", field(view, "#mc-volatility").getText());
                assertEquals("417", field(view, "#mc-seed").getText());
                assertFalse(button(view, "#mc-run").isDisabled());
                assertTrue(button(view, "#mc-cancel").isDisabled());
                field(view, "#mc-return").setText("NaN");
                button(view, "#mc-run").fire();
                assertTrue(queued.isEmpty());
                assertEquals(MonteCarloSession.State.IDLE, view.session().state());
                field(view, "#mc-return").setText("6.25");
                field(view, "#mc-volatility").setText("18");
                assertEquals(before, mapper.writeValueAsString(plan));
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void realWorkerRunsOffFxWhileControlsRemainResponsiveAndCancelPublishesNothing() throws Exception {
        var plan = MonteCarloUiFixtures.plan("1000", 3);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ended = new CompletableFuture<Void>();
        var executor = Executors.newSingleThreadExecutor();
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(plan), executor, (snapshot, settings, reference, progress, cancellation) -> {
                assertFalse(Platform.isFxApplicationThread());
                assertNotSame(plan, snapshot);
                entered.countDown();
                try {
                    assertTrue(release.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                cancellation.throwIfCancellationRequested();
                fail("Cancellation must stop this request");
                return null;
            });
            new Scene(created);
            ((Label) created.lookup("#mc-status")).textProperty().addListener((o, a, b) -> {
                if (created.session().state() == MonteCarloSession.State.CANCELLED) {
                    ended.complete(null);
                }
            });
            button(created, "#mc-run").fire();
            return created;
        });
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            fx(() -> {
                assertTrue(view.session().busy());
                assertTrue(button(view, "#mc-run").isDisabled());
                assertTrue(field(view, "#mc-volatility").isDisabled());
                assertFalse(button(view, "#mc-cancel").isDisabled());
                button(view, "#mc-cancel").fire();
                return null;
            });
            release.countDown();
            ended.get(30, TimeUnit.SECONDS);
            fx(() -> {
                assertFalse(view.session().busy());
                assertNull(view.session().result());
                assertFalse(button(view, "#mc-run").isDisabled());
                return null;
            });
            assertEquals(before, mapper.writeValueAsString(plan));
        } finally {
            release.countDown();
            fx(() -> {
                view.close();
                return null;
            });
            executor.shutdownNow();
        }
    }

    @Test
    void completedAllFailedAndUnexpectedErrorRenderWithoutInventingValues() throws Exception {
        for (String balance : new String[]{"1000", "150"}) {
            var completed = MonteCarloUiFixtures.run(balance);
            var queue = new ArrayDeque<Runnable>();
            var controller = new Controller(MonteCarloUiFixtures.plan(balance, 3));
            var view = fx(() -> {
                var created = new MonteCarloAnalysisView(controller, queue::add, (p, s, r, progress, cancel) -> completed);
                new Scene(created);
                button(created, "#mc-run").fire();
                return created;
            });
            queue.remove().run();
            waitForFx();
            try {
                fx(() -> {
                    assertEquals(MonteCarloSession.State.COMPLETED, view.session().state());
                    var chart = (MonteCarloFanChart) view.lookup("#monte-carlo-fan");
                    assertTrue(chart.isFocusTraversable());
                    assertSelection(chart, completed.fan(), 0);
                    key(chart, KeyCode.LEFT);
                    assertSelection(chart, completed.fan(), 0);
                    key(chart, KeyCode.RIGHT);
                    assertSelection(chart, completed.fan(), 1);
                    key(chart, KeyCode.END);
                    assertSelection(chart, completed.fan(), completed.fan().years().size() - 1);
                    key(chart, KeyCode.RIGHT);
                    assertSelection(chart, completed.fan(), completed.fan().years().size() - 1);
                    key(chart, KeyCode.HOME);
                    assertSelection(chart, completed.fan(), 0);
                    assertEquals(chart.selectedDetailProperty().get().replace("\n", " · "),
                            ((Label) view.lookup("#mc-selected-year")).getText());
                    assertTrue(queue.isEmpty(), "Navigation must not submit work");
                    assertSame(completed, view.session().result());
                    assertFalse(view.session().stale());
                    var table = (TableView<?>) view.lookup("#mc-outcomes");
                    assertEquals(4, table.getItems().size());
                    assertTrue(view.lookup("#mc-conditional-notice").isVisible());
                    if (balance.equals("150")) {
                        assertEquals("0.0%", ((Label) view.lookup("#mc-funding")).getText());
                        assertEquals("Unavailable", table.getColumns().get(1).getCellData(0));
                        assertTrue(((Label) view.lookup("#mc-conditional-notice")).getText().contains("No simulations"));
                    } else {
                        assertEquals("100.0%", ((Label) view.lookup("#mc-funding")).getText());
                    }
                    field(view, "#mc-volatility").setText("15");
                    assertTrue(view.session().stale());
                    assertTrue(((Label) view.lookup("#mc-status")).getText().contains("changed"));
                    controller.markModified();
                    assertTrue(view.session().stale());
                    return null;
                });
            } finally {
                fx(() -> {
                    view.close();
                    return null;
                });
            }
        }
        var queue = new ArrayDeque<Runnable>();
        var error = new IllegalStateException("Scenario diagnostics");
        var view = fx(() -> {
            var created = new MonteCarloAnalysisView(new Controller(MonteCarloUiFixtures.plan("1000", 3)),
                    queue::add, (p, s, r, updates, cancel) -> {
                throw error;
            });
            new Scene(created);
            button(created, "#mc-run").fire();
            return created;
        });
        queue.remove().run();
        waitForFx();
        fx(() -> {
            try {
                assertEquals(MonteCarloSession.State.FAILED, view.session().state());
                assertSame(error, view.session().failure());
                assertNull(view.session().result());
                assertTrue(((Label) view.lookup("#mc-status")).getText().contains("Scenario diagnostics"));
                assertFalse(button(view, "#mc-run").isDisabled());
            } finally {
                view.close();
            }
            return null;
        });
    }

    @Test
    void analysisMenuContainsIndependentSiblingItem() throws Exception {
        fx(() -> {
            var window = new MainWindow();
            var scene = window.createScene();
            var menu = (MenuBar) scene.lookup(".menu-bar");
            var analysis = menu.getMenus().stream().filter(value -> value.getText().equals("Analysis")).findFirst().orElseThrow();
            assertTrue(analysis.getItems().stream().anyMatch(item -> item.getText().startsWith("Social Security")));
            var monteCarlo = analysis.getItems().stream().filter(item -> "monte-carlo-analysis-menu".equals(item.getId()))
                    .findFirst().orElseThrow();
            assertEquals("Monte Carlo Retirement Analysis...", monteCarlo.getText());
            assertFalse(monteCarlo instanceof Menu);
            return null;
        });
    }

    static void key(MonteCarloFanChart chart, KeyCode code) {
        chart.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /** Wait for actual layout pulses, not just another FX event-queue callback. */
    static void awaitLayoutPulses(Scene scene) throws Exception {
        var completed = new CompletableFuture<Void>();
        fx(() -> {
            scene.addPostLayoutPulseListener(new Runnable() {
                private int remaining = 3;

                @Override
                public void run() {
                    if (--remaining == 0) {
                        scene.removePostLayoutPulseListener(this);
                        completed.complete(null);
                    } else {
                        Platform.requestNextPulse();
                    }
                }
            });
            Platform.requestNextPulse();
            return null;
        });
        completed.get(30, TimeUnit.SECONDS);
    }

    static void assertGuideMatchesYear(MonteCarloFanChart chart, int year) {
        var guide = (javafx.scene.shape.Line) chart.lookup(".mc-active-year");
        double expected = chart.getXAxis().getDisplayPosition(year);
        assertTrue(chart.selectedDetailProperty().get().startsWith("Calendar Year: " + year));
        assertEquals(expected, guide.getStartX(), 0.000001, "Guide start for " + year);
        assertEquals(expected, guide.getEndX(), 0.000001, "Guide end for " + year);
        // Compare actual rendered median and deterministic vertices in the same plot space.
        for (int seriesIndex : new int[]{2, 3}) {
            var series = chart.getData().get(seriesIndex);
            int index = java.util.stream.IntStream.range(0, series.getData().size())
                    .filter(i -> series.getData().get(i).getXValue().intValue() == year)
                    .findFirst().orElseThrow();
            var path = (javafx.scene.shape.Path) series.getNode();
            // JavaFX may emit both MoveTo and LineTo for the first point.
            var positions = path.getElements().stream()
                    .map(vertex -> vertex instanceof javafx.scene.shape.MoveTo move
                            ? move.getX() : ((javafx.scene.shape.LineTo) vertex).getX())
                    .distinct().toList();
            assertEquals(series.getData().size(), positions.size());
            double plottedX = positions.get(index);
            assertEquals(expected, plottedX, 0.000001, "Plotted series " + seriesIndex + " for " + year);
        }
    }

    @Test
    void guideTracksPlottedCalendarYearsAfterRealLayoutPulses() throws Exception {
        var base = MonteCarloUiFixtures.run("1000").fan();
        var values = base.years().getFirst();
        var model = new MonteCarloFanModel(java.util.stream.IntStream.rangeClosed(2027, 2056)
                .mapToObj(year -> new MonteCarloFanModel.Year(year, values.percentiles(), values.deterministic()))
                .toList(), base.requested(), base.context());
        var stage = fx(() -> {
            var chart = new MonteCarloFanChart();
            chart.load(model);
            var created = new Stage();
            created.setScene(new Scene(new javafx.scene.layout.BorderPane(chart), 960, 480));
            created.show();
            chart.requestFocus();
            return created;
        });
        try {
            awaitLayoutPulses(stage.getScene());
            for (int year : new int[]{2027, 2028, 2042, 2056, 2027}) {
                fx(() -> {
                    var chart = (MonteCarloFanChart) stage.getScene().lookup("#monte-carlo-fan");
                    key(chart, KeyCode.HOME);
                    for (int index = 2027; index < year; index++) {
                        key(chart, KeyCode.RIGHT);
                    }
                    return null;
                });
                awaitLayoutPulses(stage.getScene());
                fx(() -> {
                    var chart = (MonteCarloFanChart) stage.getScene().lookup("#monte-carlo-fan");
                    assertGuideMatchesYear(chart, year);
                    return null;
                });
            }
        } finally {
            fx(() -> {
                stage.close();
                return null;
            });
        }
    }

    private static void assertSelection(MonteCarloFanChart chart, MonteCarloFanModel model, int index) {
        String expected = MonteCarloPresentation.tooltip(model, model.years().get(index));
        assertEquals(expected, chart.selectedDetailProperty().get());
        assertEquals("Investable Assets. " + expected, chart.getAccessibleText());
    }

    @Test
    void emptyAndSingleYearChartsHandleNavigationAndReload() throws Exception {
        var run = MonteCarloUiFixtures.run("1000");
        fx(() -> {
            var chart = new MonteCarloFanChart();
            key(chart, KeyCode.RIGHT);
            var single = new MonteCarloFanModel(java.util.List.of(run.fan().years().getFirst()),
                    run.fan().requested(), run.fan().context());
            chart.load(single);
            for (var code : java.util.List.of(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.HOME, KeyCode.END)) {
                key(chart, code);
                assertSelection(chart, single, 0);
            }
            chart.load(new MonteCarloFanModel(java.util.List.of(), 0,
                    com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.empty()));
            key(chart, KeyCode.END);
            assertEquals("", chart.selectedDetailProperty().get());
            return null;
        });
    }
}
