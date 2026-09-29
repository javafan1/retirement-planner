package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;

class MonteCarloComparisonViewTest {
    @BeforeAll static void init() throws Exception { startFx(); }
    static void key(MonteCarloDifferenceChart chart, KeyCode code) {
        chart.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
    static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    @Test void actionAvailabilityFollowsBaselineAndSingleAnalysisRemains() throws Exception {
        fx(() -> {
            var window = new MainWindow(); var scene = window.createScene();
            var field = MainWindow.class.getDeclaredField("controller"); field.setAccessible(true);
            var controller = (ApplicationController) field.get(window);
            controller.getCurrentPlan().setBaseline(null);
            var menu = ((MenuBar) scene.lookup(".menu-bar")).getMenus().stream().filter(m -> m.getText().equals("Analysis")).findFirst().orElseThrow();
            menu.getOnShowing().handle(new javafx.event.Event(Menu.ON_SHOWING));
            var action = menu.getItems().stream().filter(i -> "monte-carlo-comparison-menu".equals(i.getId())).findFirst().orElseThrow();
            assertTrue(action.isDisable());
            assertTrue(menu.getItems().stream().anyMatch(i -> i.isVisible() && i.getText().equals(MonteCarloStrategyComparisonRunService.MISSING_BASELINE)));
            controller.saveCurrentAsBaseline("Test"); menu.getOnShowing().handle(new javafx.event.Event(Menu.ON_SHOWING));
            assertFalse(action.isDisable());
            assertTrue(menu.getItems().stream().anyMatch(i -> "monte-carlo-analysis-menu".equals(i.getId())));
            return null;
        });
    }

    @Test void missingBaselineViewExplainsAndDisablesRun() throws Exception {
        fx(() -> {
            var view = new MonteCarloStrategyComparisonView(new Controller(MonteCarloUiFixtures.plan("1000", 3)), Runnable::run,
                    (p, u, c) -> { fail("No baseline must never run"); return null; });
            try {
                assertTrue(((Button) view.lookup("#mcc-run")).isDisabled());
                assertEquals(MonteCarloStrategyComparisonRunService.MISSING_BASELINE, ((Label) view.lookup("#mcc-context")).getText());
            } finally { view.close(); }
            return null;
        });
    }

    @Test void chartHasSignedBoundsZeroGuideKeyboardClickAndAccessibleCounts() throws Exception {
        var stage = new Stage[1];
        var chart = fx(() -> {
            var c = new MonteCarloDifferenceChart(); c.load(MonteCarloComparisonFixtures.annual());
            var root = new StackPane(c); root.getStylesheets().add(getClass().getResource("/css/monte-carlo.css").toExternalForm());
            stage[0] = new Stage(); stage[0].setScene(new Scene(root, 1300, 350)); stage[0].show(); return c;
        });
        try {
            awaitLayoutPulses(stage[0].getScene());
            fx(() -> {
                assertTrue(chart.valueAxis().getLowerBound() < 0); assertTrue(chart.valueAxis().getUpperBound() > 0);
                assertTrue(((Line) chart.lookup("#mc-comparison-zero")).getEndX() > 0);
                var zero = (Line) chart.lookup("#mc-comparison-zero");
                assertTrue(zero.getStrokeWidth() > 2);
                assertFalse(zero.getStrokeDashArray().isEmpty());
                assertEquals(chart.valueAxis().localToScene(0, chart.valueAxis().getDisplayPosition(0)).getY(),
                        zero.localToScene(0, zero.getStartY()).getY(), 0.01);
                var title = chart.valueAxis().lookup(".axis-label");
                assertEquals("Current − Baseline ($)", chart.valueAxis().getLabel());
                assertTrue(chart.localToScene(chart.getBoundsInLocal()).contains(title.localToScene(title.getBoundsInLocal())));
                assertEquals(0, chart.selectedIndex()); key(chart, KeyCode.LEFT); assertEquals(0, chart.selectedIndex());
                key(chart, KeyCode.RIGHT); assertEquals(1, chart.selectedIndex()); key(chart, KeyCode.END); assertEquals(2, chart.selectedIndex());
                key(chart, KeyCode.RIGHT); assertEquals(2, chart.selectedIndex()); key(chart, KeyCode.HOME); assertEquals(0, chart.selectedIndex());
                chart.lookup("#mc-comparison-hit-2041").fireEvent(click());
                assertEquals(1, chart.selectedIndex()); assertTrue(chart.isFocused());
                assertTrue(chart.lookup("#mc-comparison-guide").isVisible());
                assertTrue(chart.getAccessibleText().contains("2041"));
                assertTrue(chart.getAccessibleText().contains("4 comparable of 2,000"));
                assertTrue(chart.warningProperty().get().contains("Only 4"));
                assertEquals(MonteCarloComparisonFixtures.annual().get(2041), chart.years().get(1));
                return null;
            });
        } finally { fx(() -> { stage[0].close(); return null; }); }
    }

    @Test void completedViewFreezesMetadataSelectionDoesNotRerunAndEditsMarkStale() throws Exception {
        var jobs = new ArrayDeque<Runnable>(); var calls = new AtomicInteger();
        var plan = MonteCarloComparisonFixtures.plan(); var controller = new Controller(plan);
        var view = fx(() -> {
            var v = new MonteCarloStrategyComparisonView(controller, jobs::add, (p, u, c) -> {
                calls.incrementAndGet(); return new MonteCarloStrategyComparisonRunService().run(p, u, c);
            });
            ((ComboBox<Integer>) v.lookup("#mcc-count")).setValue(3);
            ((Button) v.lookup("#mcc-run")).fire();
            assertTrue(v.lookup("#mcc-count").isDisabled()); assertFalse(v.lookup("#mcc-cancel").isDisabled());
            return v;
        });
        jobs.remove().run(); waitForFx();
        fx(() -> {
            try {
                assertEquals(MonteCarloStrategyComparisonSession.State.COMPLETED, view.session().state());
                var result = view.session().result();
                assertEquals(3, result.result().requestedPairedCount()); assertEquals(417, result.result().request().assumptions().settings().seed());
                assertEquals(3, view.session().progress().totalWork());
                assertTrue(((Label) view.lookup("#mcc-identity")).getText().contains("Strategy A"));
                assertTrue(((Label) view.lookup("#mcc-denominator")).getText().contains("3 of 3"));
                var chart = (MonteCarloDifferenceChart) view.lookup("#mc-comparison-chart");
                for (int i = 0; i < 50; i++) { key(chart, KeyCode.END); key(chart, KeyCode.HOME); }
                var table = (TableView<?>) view.lookup("#mcc-metrics"); table.getSelectionModel().select(3);
                assertEquals("P(Current > Baseline)", table.getColumns().get(3).getText());
                assertTrue(table.getAccessibleText().contains("Probability Current Plan exceeds Saved Baseline"));
                assertFalse(((Label) view.lookup("#mcc-funding-a")).getText().isBlank());
                assertFalse(((Label) view.lookup("#mcc-funding-b")).getText().isBlank());
                assertTrue(((Label) view.lookup("#mcc-funding-difference")).getText().contains("percentage points"));
                assertTrue(((Label) view.lookup("#mcc-direction")).getText().contains("paid MORE"));
                assertTrue(((Label) view.lookup("#mcc-relations")).getText().contains("paid more"));
                assertEquals(1, calls.get()); assertFalse(((TitledPane) view.lookup("#mcc-details")).isExpanded());
                var frozen = ((Label) ((TitledPane) view.lookup("#mcc-details")).getContent()).getText();
                plan.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1961, 1, 1)); controller.markModified();
                assertTrue(view.session().stale()); assertSame(result, view.session().result());
                ((TextField) view.lookup("#mcc-seed")).setText("999");
                assertTrue(((Label) view.lookup("#mcc-status")).getText().contains("STALE"));
                assertEquals(frozen, ((Label) ((TitledPane) view.lookup("#mcc-details")).getContent()).getText());
                for (var node : view.lookupAll(".label")) {
                    String text = ((Label) node).getText().toLowerCase(Locale.ROOT);
                    for (String banned : List.of("winner", "recommended", "optimal", "best plan")) assertFalse(text.contains(banned), text);
                }
            } finally { view.close(); }
            return null;
        });
    }

    @Test void cancelAndCloseCannotPublishPendingWork() throws Exception {
        var jobs = new ArrayDeque<Runnable>();
        var view = fx(() -> {
            var v = new MonteCarloStrategyComparisonView(new Controller(MonteCarloComparisonFixtures.plan()), jobs::add,
                    (p, u, c) -> { fail("Cancelled before execution"); return null; });
            ((Button) v.lookup("#mcc-run")).fire(); ((Button) v.lookup("#mcc-cancel")).fire(); return v;
        });
        jobs.remove().run(); waitForFx();
        fx(() -> {
            assertEquals(MonteCarloStrategyComparisonSession.State.CANCELLED, view.session().state());
            assertNull(view.session().result()); assertFalse(view.lookup("#mcc-run").isDisabled());
            view.close(); assertEquals(MonteCarloStrategyComparisonSession.State.CLOSED, view.session().state()); return null;
        });
    }

    @Test void identicalStrategiesRenderZeroAndEmptyChartIsAccessible() throws Exception {
        var completed = MonteCarloComparisonFixtures.run();
        fx(() -> {
            for (var metric : MonteCarloStrategyComparisonPresentation.metrics(completed.result().summary())) {
                assertEquals("$0", metric.median()); assertEquals("$0", metric.mean());
            }
            var chart = new MonteCarloDifferenceChart(); chart.load(completed.result().summary().annualResults());
            assertTrue(chart.selectedYearProperty().get().contains("Median $0"));
            chart.load(Map.of()); key(chart, KeyCode.END);
            assertTrue(chart.getAccessibleText().contains("No annual financial rows")); return null;
        });
    }
}
