package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static com.daviddunn.retirementplanner.ui.socialsecurity.ClaimingStrategyHeatMapModelTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimingStrategyHeatMapViewTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> startup = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(startup); } catch (IllegalStateException started) { Platform.runLater(startup); }
        startup.get(20, TimeUnit.SECONDS);
    }

    static LongevityWeightedIntegratedPresentation fixture() {
        return presentation(List.of(entry(1, 69, 62, 66, 64, "1000", 1, "10"),
                entry(2, 62, 70, 65, 63, "993.45", 2, "3.45")), Optional.of(entry(0, 67, 67, 65, 65, "990", 0, null)));
    }

    @Test void emptyStateIsExplicitAndNoActionIsAvailable() throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(order -> fail("No navigation before a result"));
            assertTrue(text(view).contains(ClaimingStrategyHeatMapView.EMPTY_TEXT));
            assertTrue(view.metric.isDisabled());
            assertTrue(view.fullAnalysis.isDisabled());
            assertTrue(view.lookupAll(".heat-map-cell").isEmpty());
        });
    }

    @Test void selectingCellShowsCompleteStrategyWithoutCallingNavigationOrAnalysis() throws Exception {
        fx(() -> {
            var opened = new ArrayList<Integer>();
            var view = new ClaimingStrategyHeatMapView(opened::add);
            view.render(LongevityWeightedHeatMapAdapter.from(fixture()));
            button(view, 62, 70).fire();
            assertTrue(opened.isEmpty());
            String detail = text(view);
            for (String expected : List.of("Primary 62 / Spouse 70", "99.3% of optimal", "Primary own age: 62",
                    "Spouse own age: 70", "Primary Survivor Benefit Claiming Age: 65 years 4 months", "Spouse Survivor Benefit Claiming Age: 63 years 7 months",
                    "Expected PV Social Security", "Not Available", "Future-Dollar Estate")) {
                assertTrue(detail.contains(expected), expected);
            }
            assertFalse(view.fullAnalysis.isDisabled());
            view.fullAnalysis.fire();
            assertEquals(List.of(2), opened);
        });
    }

    @Test void metricChangesUseCapturedValuesAndPreserveSelection() throws Exception {
        fx(() -> {
            var opened = new ArrayList<Integer>();
            var source = fixture();
            var model = LongevityWeightedHeatMapAdapter.from(source);
            var view = new ClaimingStrategyHeatMapView(opened::add);
            view.render(model);
            var selected = button(view, 62, 70);
            selected.fire();
            assertEquals("99.3%", selected.getText());
            var styles = List.copyOf(selected.getStyleClass());
            for (var metric : view.metric.getItems()) {
                view.metric.setValue(metric);
                assertEquals(ClaimingStrategyHeatMapView.format(metric, model.cell(62, 70).value(metric)), selected.getText());
                assertTrue(selected.isSelected());
                assertEquals(styles, selected.getStyleClass(), "Color always uses % of optimal");
            }
            assertTrue(opened.isEmpty());
            assertEquals(new BigDecimal("993.45"), source.result().orderedEntries().get(1).aggregate().orElseThrow().expectedPvAfterTaxEstate());
            assertEquals(0, source.result().work().stageFourEvaluations());
            assertEquals(5, view.metric.getItems().size());
            assertFalse(view.metric.getItems().contains(ClaimingStrategyHeatMapMetric.EXPECTED_PV_SOCIAL_SECURITY));
        });
    }

    @Test void optimalHasTextBorderClassAndAccessibleMarkerBeyondColor() throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(order -> { });
            view.render(LongevityWeightedHeatMapAdapter.from(fixture()));
            var optimal = button(view, 69, 62);
            assertEquals("★ 100.0%", optimal.getText());
            assertTrue(optimal.getStyleClass().contains("heat-map-optimal"));
            assertTrue(optimal.getStyleClass().contains("heat-map-tier-1"));
            assertTrue(optimal.getAccessibleText().contains("Optimal"));
            assertTrue(optimal.getTooltip().getText().contains("Optimal"));
            assertFalse(button(view, 62, 70).getStyleClass().contains("heat-map-optimal"));
        });
    }

    @Test void unavailablePairDoesNotSupplyMetricsOrSurvivorDefaults() throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(order -> fail("Unavailable cells cannot navigate"));
            view.render(LongevityWeightedHeatMapAdapter.from(fixture()));
            var unavailable = button(view, 70, 70);
            unavailable.fire();
            assertEquals("Not Available", unavailable.getText());
            assertTrue(unavailable.getStyleClass().contains("heat-map-unavailable"));
            assertTrue(text(view).contains("Primary Survivor Benefit Claiming Age: Not Available"));
            assertTrue(text(view).contains("Spouse Survivor Benefit Claiming Age: Not Available"));
            assertTrue(view.fullAnalysis.isDisabled());
        });
    }

    @Test void busyStateDisablesInteractionsAndRestoresPreviousResult() throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(order -> fail("Busy navigation"));
            view.setAnalysisBusy(true);
            assertTrue(text(view).contains("Integrated analysis is running"));
            view.render(LongevityWeightedHeatMapAdapter.from(fixture()));
            assertTrue(view.metric.isDisabled());
            assertTrue(button(view, 62, 70).isDisabled());
            assertTrue(view.fullAnalysis.isDisabled());
            button(view, 62, 70).fire();
            assertTrue(button(view, 69, 62).isSelected());
            view.setAnalysisBusy(false);
            assertFalse(view.metric.isDisabled());
            assertFalse(view.fullAnalysis.isDisabled());
        });
    }

    @Test void usesSharedTooltipFactoryAndExistingDetailWorkflow() throws Exception {
        fx(() -> {
            var view = new LongevityWeightedIntegratedView();
            var source = fixture();
            view.render(source, "Current elections");
            var reference = com.daviddunn.retirementplanner.ui.controls.HelpIcon.createTooltip("Reference");
            var tooltip = view.heatMap.metric.getTooltip();
            assertTrue(tooltip.isWrapText());
            assertEquals(reference.getMaxWidth(), tooltip.getMaxWidth());
            assertEquals(reference.getShowDelay(), tooltip.getShowDelay());
            assertEquals(reference.getShowDuration(), tooltip.getShowDuration());
            assertEquals(reference.getHideDelay(), tooltip.getHideDelay());
            assertEquals(reference.getStyle(), tooltip.getStyle());
            var starts = new ArrayList<String>();
            view.run.setOnAction(event -> starts.add("run"));
            view.resultTabs.getSelectionModel().select(view.heatMapTab);
            button(view.heatMap, 62, 70).fire();
            view.heatMap.fullAnalysis.fire();
            assertSame(source.result().orderedEntries().get(1), view.table.getSelectionModel().getSelectedItem());
            assertSame(view.rankedTab, view.resultTabs.getSelectionModel().getSelectedItem());
            var details = (TitledPane) view.rankedContent.getChildren().stream().filter(node -> node instanceof TitledPane pane
                    && pane.getText().equals("Selected Strategy Details")).findFirst().orElseThrow();
            assertTrue(details.isExpanded());
            assertTrue(text(details.getContent()).contains("Original strategy occurrence: 2"));
            assertTrue(starts.isEmpty());
            view.stale.setText("Inputs changed — rerun");
            assertEquals(view.stale.getText(), view.heatMap.resultNotice.getText());
        });
    }

    @Test void newResultResetsMetricButTabSwitchingPreservesPresentationState() throws Exception {
        fx(() -> {
            var view = new LongevityWeightedIntegratedView();
            var source = fixture();
            view.render(source, "Current elections");
            assertEquals(List.of("Ranked Strategies", "Claiming-Age Heat Map"),
                    view.resultTabs.getTabs().stream().map(Tab::getText).toList());
            assertSame(view.rankedContent, view.rankedTab.getContent());
            assertSame(view.heatMap, view.heatMapTab.getContent());
            view.run.setOnAction(event -> fail("Tab switching must not run analysis"));
            view.resultTabs.getSelectionModel().select(view.heatMapTab);
            var cell = button(view.heatMap, 62, 70);
            cell.fire();
            view.heatMap.metric.setValue(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE);
            Object model = heatMapModel(view.heatMap);
            for (int repeat = 0; repeat < 3; repeat++) {
                view.resultTabs.getSelectionModel().select(view.rankedTab);
                view.resultTabs.getSelectionModel().select(view.heatMapTab);
            }
            assertSame(model, heatMapModel(view.heatMap));
            assertSame(cell, button(view.heatMap, 62, 70));
            assertTrue(cell.isSelected());
            assertEquals(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE, view.heatMap.metric.getValue());
            view.render(fixture(), "Current elections");
            assertEquals(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL, view.heatMap.metric.getValue());
        });
    }

    private static Object heatMapModel(ClaimingStrategyHeatMapView view) {
        try {
            var field = ClaimingStrategyHeatMapView.class.getDeclaredField("model");
            field.setAccessible(true);
            return field.get(view);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    @ParameterizedTest
    @CsvSource({"99.5,heat-map-tier-1", "99.4999,heat-map-tier-2", "99.0,heat-map-tier-2",
            "98.9999,heat-map-tier-3", "97.0,heat-map-tier-3", "96.9999,heat-map-tier-4",
            "95.0,heat-map-tier-4", "94.9999,heat-map-tier-5", "-1,heat-map-tier-5"})
    void tiersUseUnroundedPercentage(String pv, String expected) throws Exception {
        fx(() -> {
            var model = LongevityWeightedHeatMapAdapter.from(presentation(List.of(entry(1, 62, 62, 60, 60, "100", 1, null),
                    entry(2, 63, 63, 60, 60, pv, 2, null)), Optional.empty()));
            assertEquals(expected, ClaimingStrategyHeatMapView.tierClass(model.cell(63, 63)));
        });
    }

    @ParameterizedTest
    @CsvSource({"100,100.0%", "99.345,99.3%", "99.35,99.4%", "99.95,100.0%"})
    void percentageFormattingIsDeterministic(String raw, String expected) throws Exception {
        fx(() -> assertEquals(expected, ClaimingStrategyHeatMapView.format(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                Optional.of(new BigDecimal(raw)))));
    }

    @ParameterizedTest
    @CsvSource({"900,650,false", "1180,820,false", "1366,768,false", "1880,960,false", "900,650,true", "1366,768,true", "1880,960,true"})
    void gridFitsViewportAndKeepsPrimaryHorizontal(int width, int height, boolean deterministic) throws Exception {
        fx(() -> {
            var view = new ClaimingStrategyHeatMapView(deterministic ? ClaimingStrategyHeatMapProfile.deterministic()
                    : ClaimingStrategyHeatMapProfile.weighted(), order -> { });
            view.render(deterministic ? DeterministicHeatMapAdapter.from(DeterministicHeatMapAdapterTest.fixture())
                    : LongevityWeightedHeatMapAdapter.from(fixture()));
            var scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            var root = new StackPane(scroll);
            var stage = new Stage();
            stage.setScene(new Scene(root, width, height));
            stage.show();
            try {
                root.applyCss();
                root.layout();
                assertTrue(view.getWidth() <= scroll.getViewportBounds().getWidth() + 1);
                assertEquals(81, view.lookupAll(".heat-map-cell").size());
                var first = button(view, 62, 62);
                var horizontal = button(view, 70, 62);
                var vertical = button(view, 62, 70);
                assertEquals(first.getLayoutY(), horizontal.getLayoutY());
                assertTrue(horizontal.getLayoutX() > first.getLayoutX());
                assertEquals(first.getLayoutX(), vertical.getLayoutX());
                assertTrue(vertical.getLayoutY() > first.getLayoutY());
                assertTrue(button(view, 69, 62).getBorder().getStrokes().getFirst().getWidths().getTop() >= 2);
                assertTrue(view.getHeight() + 1 >= view.prefHeight(view.getWidth()));
                var area = view.lookup("#heat-map-grid-area");
                var details = view.lookup("#heat-map-details");
                if (width >= 1366) {
                    assertTrue(details.getBoundsInParent().getMinX() >= area.getBoundsInParent().getMaxX());
                    assertEquals(area.getBoundsInParent().getMinY(), details.getBoundsInParent().getMinY(), 1);
                    assertTrue(vertical.localToScene(vertical.getBoundsInLocal()).getMaxY()
                            <= scroll.localToScene(scroll.getViewportBounds()).getMaxY());
                    assertTrue(first.getHeight() >= 36 && first.getHeight() <= 42);
                } else {
                    assertTrue(details.getBoundsInParent().getMinY() >= area.getBoundsInParent().getMaxY());
                }
                for (int spouse = 62; spouse <= 70; spouse++) {
                    for (int primary = 62; primary <= 70; primary++) assertNotNull(button(view, primary, spouse));
                }
            } finally { stage.close(); }
        });
    }

    static ToggleButton button(ClaimingStrategyHeatMapView view, int primary, int spouse) {
        return (ToggleButton) view.lookup("#claiming-heat-map-" + primary + "-" + spouse);
    }

    private static String text(Node node) {
        String own = node instanceof Labeled labeled ? labeled.getText() : "";
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) own += "\n" + text(child);
        return own;
    }

    private static void fx(Runnable test) throws Exception {
        var task = new FutureTask<Void>(() -> { test.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
