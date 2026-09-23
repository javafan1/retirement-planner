package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import com.daviddunn.retirementplanner.ui.views.ResultsView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.css.PseudoClass;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenAnalysisViewTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        task.get(30, TimeUnit.SECONDS);
    }

    @Test void frozenPlanCardsSelectorChartMarkerAndTable() throws Exception {
        fx(() -> {
            var result = result(-100, -50, 10, 40);
            var view = new BreakEvenAnalysisView(result);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 1050, 850));
            stage.show();
            try {
                String text = texts(view);
                assertTrue(text.contains("Lisa 62"));
                assertTrue(text.contains("Lisa 70"));
                assertTrue(text.contains("David 70"));
                assertTrue(text.contains("No Difference")); // SS zero in both fixtures.
                ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
                assertEquals(BreakEvenMetric.TOTAL_NET_WORTH, selector.getValue());
                assertTrue(((LineChart<?, ?>) view.lookup("#break-even-chart")).getTitle().contains("Total Net Worth"));
                selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
                view.applyCss(); view.layout();
                Label summary = lookup(view, "#break-even-selected-summary");
                assertTrue(summary.getText().contains("Break-even 2029"));
                assertTrue(((Label) view.lookup("#break-even-annotation")).getText().contains("Lisa 64"));
                LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
                assertEquals(List.of(-100, -50, 10, 40), chart.getData().getFirst().getData().stream()
                        .map(point -> point.getYValue().intValue()).toList());
                assertEquals("Sustained Break-Even", chart.getData().getLast().getName());
                assertEquals(2029, chart.getData().getLast().getData().getFirst().getXValue().intValue());
                assertNotNull(view.lookup("#break-even-marker"));
                TableView<BreakEvenYearResult> table = lookup(view, "#break-even-years");
                assertEquals(result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).years(), table.getItems());
                selector.setValue(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY);
                assertEquals(2, chart.getData().size());
                assertTrue(table.getColumns().get(3).getText().contains("Cumulative"));
                assertTrue(summary.getText().contains("No difference"));
            } finally { stage.close(); }
        });
    }

    @Test void noRecoveryAlreadyAheadAndTemporaryCrossoverMessages() throws Exception {
        fx(() -> {
            for (var pair : List.of(new Object[]{new long[]{-100, -20}, "No break-even through"},
                    new Object[]{new long[]{10, 20}, "already ahead at start"},
                    new Object[]{new long[]{-100, 10, -20}, "No sustained break-even"})) {
                var view = new BreakEvenAnalysisView(result((long[]) pair[0]));
                ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
                selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
                Label label = lookup(view, "#break-even-selected-summary");
                assertTrue(label.getText().contains((String) pair[1]));
                LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
                assertEquals(2, chart.getData().size());
            }
            var result = result(-100, 10, -20, 30);
            String summary = BreakEvenPresentation.summary(result, result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS));
            assertTrue(summary.contains("Year 2030"));
            assertTrue(summary.contains("First crossover: 2028"));
        });
    }

    @Test void differingHorizonsStopAtActualOverlapInEitherDirection() throws Exception {
        fx(() -> {
            var shortPlan = snapshot(List.of(year(2027, 100), year(2029, 100)), 62);
            var longPlan = snapshot(List.of(year(2027, 20), year(2029, 50), year(2032, 300)), 70);
            for (boolean reverse : List.of(false, true)) {
                var result = new BreakEvenAnalyzer().analyze(reverse ? longPlan : shortPlan, reverse ? shortPlan : longPlan);
                var view = new BreakEvenAnalysisView(result);
                Label period = lookup(view, "#break-even-period");
                assertTrue(period.getText().contains("2027–2029 · 2 comparable years"));
                assertTrue(period.getText().contains("Planning horizons differ"));
                assertTrue(period.getText().contains((reverse ? "Current" : "Baseline") + " ends in 2029"));
                LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
                chart.getData().forEach(series -> series.getData().forEach(point -> assertTrue(point.getXValue().intValue() <= 2029)));
                assertEquals(2029, ((javafx.scene.chart.NumberAxis) chart.getXAxis()).getUpperBound());
            }
        });
    }

    @Test void resultsViewActionOpensDialogWithoutChangingResultsOrSelection() throws Exception {
        fx(() -> {
            ResultsView view = new ResultsView();
            Button action = lookup(view, "#break-even-action");
            assertTrue(action.isDisabled());
            var result = result(-100, 20);
            var projection = new com.daviddunn.retirementplanner.domain.projection.Projection();
            projection.addYear(year(2027, 900));
            projection.addYear(year(2028, 1020));
            view.load(projection, List.of());
            view.getTable().getSelectionModel().select(1);
            view.setBreakEvenAnalysis(result);
            assertFalse(action.isDisabled());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() -> {
                try {
                    var window = Window.getWindows().stream()
                            .filter(candidate -> candidate instanceof Stage stage && stage.getTitle().equals("Break-Even Analysis"))
                            .findFirst().orElseThrow();
                    try {
                        assertNotNull(window.getScene().lookup("#break-even-chart"));
                        assertTrue(texts(window.getScene().getRoot()).contains("Baseline Plan"));
                    } finally { ((Stage) window).close(); }
                } catch (Throwable exception) { failure.set(exception); }
            });
            action.fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertSame(projection.getYearAt(1), view.getTable().getSelectionModel().getSelectedItem());
            assertEquals(projection.getYears(), view.getTable().getItems());
            assertEquals(-100, result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).years().getFirst().difference().intValue());
            var empty = new BreakEvenAnalyzer().analyze(snapshot(List.of(), 62), snapshot(List.of(), 70));
            view.setBreakEvenAnalysis(empty);
            assertTrue(action.isDisabled());
            view.setBreakEvenAnalysis(result);
            view.load(null, null);
            assertTrue(action.isDisabled());
        });
    }

    @Test void allCardsUseTheirOwnResultAndEveryStatusHasAReadableState() throws Exception {
        fx(() -> {
            var result = result(-100, -50, 10, 40);
            var view = new BreakEvenAnalysisView(result);
            GridPane cards = lookup(view, "#break-even-cards");
            assertEquals(4, cards.getChildren().size());
            for (BreakEvenMetric metric : BreakEvenMetric.values()) {
                VBox card = lookup(view, "#break-even-card-" + metric.name());
                assertTrue(texts(card).contains(BreakEvenPresentation.summaryName(metric)));
                assertTrue(texts(card).contains(BreakEvenPresentation.cardValue(result.metrics().get(metric))));
                assertNotNull(card.getAccessibleText());
            }
            VBox recovery = lookup(view, "#break-even-card-INVESTABLE_ASSETS");
            assertTrue(texts(recovery).contains("2029"));
            assertTrue(texts(recovery).contains("David 66 · Lisa 64"));
            assertTrue(texts(recovery).contains("through 2030"));
            for (var example : List.of(new Object[]{new long[]{-2, -1}, "Not Reached"},
                    new Object[]{new long[]{1, 2}, "Already Ahead"},
                    new Object[]{new long[]{0, 0}, "No Difference"},
                    new Object[]{new long[]{-2, 1, -1}, "No Sustained Break-Even"},
                    new Object[]{new long[]{}, "No Comparable Years"})) {
                var stateView = new BreakEvenAnalysisView(result((long[]) example[0]));
                assertTrue(texts(stateView.lookup("#break-even-card-INVESTABLE_ASSETS")).contains((String) example[1]));
                ComboBox<BreakEvenMetric> selector = lookup(stateView, "#break-even-metric");
                selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
                Label annotation = lookup(stateView, "#break-even-annotation");
                assertFalse(annotation.isVisible());
                assertNull(stateView.lookup("#break-even-marker"));
            }
        });
    }

    @Test void identicalHorizonsAreCompactAndHelpIsRetained() throws Exception {
        fx(() -> {
            var view = new BreakEvenAnalysisView(result(-100, 20));
            Label period = lookup(view, "#break-even-period");
            assertEquals("Comparison Period: 2027–2028 · 2 years", period.getText());
            assertFalse(period.getText().contains("Baseline:"));
            assertFalse(period.getText().contains("Current:"));
            assertTrue(period.getTooltip().getText().contains("only over comparable years"));
            assertTrue(period.getAccessibleHelp().contains("not beyond"));
        });
    }

    @Test void cardsWrapFourTwoOneAndCalloutStaysInsidePlotAtBothEdges() throws Exception {
        fx(() -> {
            // Recovery in the second year sits near the left edge of this horizon.
            var view = new BreakEvenAnalysisView(result(-10, 1, 2, 3, 4, 5, 6, 7, 8, 9));
            ScrollPane scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            Stage stage = new Stage();
            stage.setScene(new Scene(scroll, 1100, 850));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
            stage.getScene().getStylesheets().add(getClass().getResource("/css/break-even.css").toExternalForm());
            stage.show();
            try {
                ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
                selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
                for (int width : new int[]{1100, 760, 420, 1100}) {
                    // Directly size the scroll container so this check is independent of the window manager.
                    scroll.resize(width, 850);
                    scroll.applyCss(); scroll.layout(); view.layout();
                    GridPane cards = lookup(view, "#break-even-cards");
                    int columns = width == 1100 ? 4 : width == 760 ? 2 : 1;
                    assertEquals(columns, cards.getColumnConstraints().size());
                    for (int i = 0; i < 4; i++) {
                        Region card = (Region) cards.getChildren().get(i);
                        assertEquals(i / columns, GridPane.getRowIndex(card));
                        assertTrue(card.getWidth() <= cards.getWidth());
                        assertTrue(card.getHeight() >= card.minHeight(card.getWidth()) - 1);
                    }
                    LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
                    chart.layout();
                    assertAnnotationWithinPlot(view, chart);
                }
                Label year = (Label) view.lookup("#break-even-card-INVESTABLE_ASSETS .break-even-card-year");
                Label title = (Label) view.lookup("#break-even-card-INVESTABLE_ASSETS .break-even-card-title");
                assertTrue(year.getFont().getSize() > title.getFont().getSize() * 2);
                // Exercise the final-year callout using the same chart, with no additional calculation.
                BreakEvenChart chart = lookup(view, "#break-even-chart");
                var end = result(-10, 1, 2, 3, 4, 5, 6, 7, 8, 9).metrics()
                        .get(BreakEvenMetric.INVESTABLE_ASSETS).years().getLast();
                chart.setBreakEven(end, "Break-even " + end.year() + "\nAlex 70 · Sam 68");
                chart.layout();
                assertAnnotationWithinPlot(view, chart);
                assertTrue(chart.lookup(".chart-horizontal-zero-line") instanceof javafx.scene.shape.Shape);
                var zero = (javafx.scene.shape.Shape) chart.lookup(".chart-horizontal-zero-line");
                var grid = (javafx.scene.shape.Shape) chart.lookup(".chart-horizontal-grid-lines");
                assertTrue(zero.getStrokeWidth() > grid.getStrokeWidth());
            } finally { stage.close(); }
        });
    }

    @Test void dynamicNamesSustainedCalloutAndTableEmphasisStaySynchronized() throws Exception {
        fx(() -> {
            var original = result(-100, 10, -20, 30);
            var names = new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Amira", LocalDate.of(1963, 6, 4), 70),
                    new BreakEvenPlanSummary.PersonSummary("Jonas", LocalDate.of(1965, 2, 28), 62));
            var renamed = new BreakEvenAnalysisResult(original.baselineStartYear(), original.baselineEndYear(),
                    original.currentStartYear(), original.currentEndYear(), original.comparisonStartYear(),
                    original.comparisonEndYear(), original.comparableYearCount(), original.planningHorizonsDiffer(),
                    names, names, original.metrics());
            var view = new BreakEvenAnalysisView(renamed);
            ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
            selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
            Label annotation = lookup(view, "#break-even-annotation");
            assertEquals("Break-even 2030\nAmira 67 · Jonas 65", annotation.getText());
            assertTrue(texts(view.lookup("#break-even-card-INVESTABLE_ASSETS")).contains("First crossover: 2028"));
            assertFalse(texts(view).contains("David"));
            assertFalse(texts(view).contains("Lisa"));
            LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
            assertEquals(3, chart.getData().size()); // One sustained marker; no duplicate first-crossover marker.
            assertTrue(chart.getData().getLast().getData().getFirst().getNode().getAccessibleText().contains("Amira age 67"));
            TableView<BreakEvenYearResult> table = lookup(view, "#break-even-years");
            TableRow<BreakEvenYearResult> row = table.getRowFactory().call(table);
            row.updateTableView(table);
            row.updateIndex(3);
            assertTrue(row.getPseudoClassStates().contains(PseudoClass.getPseudoClass("sustained-break-even")));
            selector.setValue(BreakEvenMetric.AFTER_TAX_ESTATE);
            assertFalse(annotation.isVisible());
            assertTrue(chart.getTitle().contains("After-Tax Estate"));
            assertEquals(2, chart.getData().size());
            assertEquals(renamed.metrics().get(BreakEvenMetric.AFTER_TAX_ESTATE).years(), table.getItems());
        });
    }

    private static void assertAnnotationWithinPlot(Parent view, LineChart<Number, Number> chart) {
        Label annotation = lookup(view, "#break-even-annotation");
        assertTrue(annotation.isVisible());
        assertTrue(annotation.getLayoutX() >= 0);
        assertTrue(annotation.getLayoutY() >= 0);
        assertTrue(annotation.getLayoutX() + annotation.getWidth() <= chart.getXAxis().getWidth() + 1);
        assertTrue(annotation.getLayoutY() + annotation.getHeight() <= chart.getYAxis().getHeight() + 1);
    }

    @Test void insightSitsBetweenCardsAndPeriodAndTracksMetricWithoutChangingContext() throws Exception {
        fx(() -> {
            var result = result(-100, 10, -20, 30);
            var insight = new BreakEvenInsightService().prepare(result);
            var context = BreakEvenContext.unavailable();
            var view = new BreakEvenAnalysisView(result, context, insight);
            int panelIndex = view.getChildren().indexOf(view.lookup("#break-even-insight"));
            assertEquals(view.getChildren().indexOf(view.lookup("#break-even-cards")) + 1, panelIndex);
            assertEquals(panelIndex + 1, view.getChildren().indexOf(view.lookup("#break-even-comparison-controls")));
            Label headline = lookup(view, "#break-even-insight-headline");
            assertTrue(headline.getText().contains("Total Net Worth"));
            assertTrue(texts(view.lookup("#break-even-insight")).contains("At comparison end (2030)"));
            VBox details = lookup(view, "#break-even-insight-details");
            assertFalse(details.isVisible());
            ((Hyperlink) view.lookup("#break-even-insight-toggle")).fire();
            assertTrue(details.isVisible());
            assertEquals("Hide details", ((Hyperlink) view.lookup("#break-even-insight-toggle")).getText());
            ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
            selector.setValue(BreakEvenMetric.AFTER_TAX_ESTATE);
            assertTrue(headline.getText().contains("After-Tax Estate"));
            assertTrue(headline.getText().contains("equal throughout"));
            assertEquals(insight, new BreakEvenInsightService().prepare(result));
            assertEquals(BreakEvenContext.unavailable(), context);
        });
    }

    @Test void insightShowsSsYearWealthSnapshotObservedDriversAndExistingSurvival() throws Exception {
        fx(() -> {
            var original = result(-100, -50, -20, 30);
            var ssSource = result(-100, 10, 20, 30).metrics().get(BreakEvenMetric.INVESTABLE_ASSETS);
            Map<BreakEvenMetric, BreakEvenMetricResult> metrics = new EnumMap<>(original.metrics());
            metrics.put(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY, new BreakEvenMetricResult(
                    BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY, ssSource.status(), ssSource.years(),
                    ssSource.firstCrossoverYear(), ssSource.sustainedBreakEvenYear(), ssSource.crossings()));
            var prepared = new BreakEvenAnalysisResult(original.baselineStartYear(), original.baselineEndYear(),
                    original.currentStartYear(), original.currentEndYear(), original.comparisonStartYear(), original.comparisonEndYear(),
                    original.comparableYearCount(), original.planningHorizonsDiffer(), original.baselineAssumptions(), original.currentAssumptions(), metrics);
            var baseInsight = new BreakEvenInsightService().prepare(prepared);
            var insight = new BreakEvenInsight(baseInsight.referenceYear(), baseInsight.snapshot(), List.of(
                    new BreakEvenInsight.Observation(BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS,
                            new java.math.BigDecimal("100"), new java.math.BigDecimal("140"))));
            var survival = new BreakEvenSurvivalPoint(2028, 65, 63, java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ONE, new java.math.BigDecimal("0.90"));
            var context = new BreakEvenContext(List.of(), Map.of(2028, survival), "Existing mortality convention");
            var view = new BreakEvenAnalysisView(prepared, context, insight);
            var scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            Stage stage = new Stage();
            stage.setScene(new Scene(scroll, 1100, 850));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/break-even.css").toExternalForm());
            stage.show();
            try {
                assertTrue(texts(view.lookup("#break-even-insight")).contains("At SS break-even (2028)"));
                assertTrue(((Label) view.lookup("#break-even-insight-INVESTABLE_ASSETS")).getText().contains("-$50.00"));
                VBox details = lookup(view, "#break-even-insight-details");
                assertTrue(texts(details).contains("90% modeled probability"));
                assertTrue(texts(details).contains("Baseline: $100.00; Current: $140.00; Difference: +$40.00"));
                for (int width : new int[]{1100, 700, 420}) {
                    scroll.resize(width, 850);
                    for (int i = 0; i < 3; i++) { scroll.applyCss(); scroll.layout(); view.layout(); }
                    Region panel = lookup(view, "#break-even-insight");
                    assertTrue(panel.getBoundsInParent().getMaxX() <= view.getWidth() + 1);
                    assertTrue(panel.getHeight() >= panel.minHeight(panel.getWidth()) - 1);
                }
                assertEquals(metrics, prepared.metrics());
                assertSame(survival, context.survival().get(2028));
            } finally { stage.close(); }
        });
    }

    @Test void phaseTwoEventsProbabilitiesCardsAndTickAlignmentUsePreparedContext() throws Exception {
        fx(() -> {
            var original = result(-100, 10, -20, 30);
            var people = new BreakEvenPlanSummary(
                    new BreakEvenPlanSummary.PersonSummary("Amira", LocalDate.of(1960, 6, 15), 70,
                            LocalDate.of(2030, 6, 15), com.daviddunn.retirementplanner.domain.model.MortalityCategory.MALE),
                    new BreakEvenPlanSummary.PersonSummary("Jonas", LocalDate.of(1965, 2, 28), 62,
                            LocalDate.of(2027, 2, 28), com.daviddunn.retirementplanner.domain.model.MortalityCategory.FEMALE));
            var current = new BreakEvenPlanSummary(people.primary(), new BreakEvenPlanSummary.PersonSummary("Jonas",
                    people.spouse().birthDate(), 65, LocalDate.of(2030, 2, 28), people.spouse().mortalityCategory()));
            var preparedResult = new BreakEvenAnalysisResult(original.baselineStartYear(), original.baselineEndYear(),
                    original.currentStartYear(), original.currentEndYear(), original.comparisonStartYear(), original.comparisonEndYear(),
                    original.comparableYearCount(), original.planningHorizonsDiffer(), people, current, original.metrics());
            var context = new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory().create(preparedResult,
                    com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)));
            assertEquals(3, context.events().size());
            var view = new BreakEvenAnalysisView(preparedResult, context);
            Stage stage = new Stage();
            ScrollPane scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            stage.setScene(new Scene(scroll, 1100, 850));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/break-even.css").toExternalForm());
            stage.show();
            try {
                ComboBox<BreakEvenMetric> selector = lookup(view, "#break-even-metric");
                selector.setValue(BreakEvenMetric.INVESTABLE_ASSETS);
                Label card = lookup(view, "#break-even-survival-INVESTABLE_ASSETS");
                assertTrue(card.getText().startsWith(BreakEvenPresentation.probability(context.survival().get(2030))));
                assertNull(view.lookup("#break-even-survival-CUMULATIVE_SOCIAL_SECURITY")); // Identical SS has no fake probability card.
                LineChart<Number, Number> chart = lookup(view, "#break-even-chart");
                for (int width : new int[]{1100, 700, 420}) {
                    scroll.resize(width, 850);
                    for (int i = 0; i < 3; i++) { scroll.applyCss(); scroll.layout(); view.layout(); chart.layout(); }
                    List<Node> events = new ArrayList<>(chart.lookupAll(".break-even-claim-label"));
                    assertEquals(3, events.size());
                    assertTrue(events.stream().map(BreakEvenAnalysisViewTest::texts).anyMatch(text -> text.contains("Both plans")));
                    for (int i = 0; i < events.size(); i++) {
                        for (int j = i + 1; j < events.size(); j++) {
                            assertFalse(events.get(i).getBoundsInParent().intersects(events.get(j).getBoundsInParent()));
                        }
                        assertTrue(events.get(i).getBoundsInParent().getMaxX() <= chart.getXAxis().getWidth() + 1);
                    }
                    Pane probabilities = lookup(view, "#break-even-probability-values");
                    assertFalse(probabilities.getChildren().isEmpty());
                    for (Node node : probabilities.getChildren()) {
                        int year = Integer.parseInt(node.getId().substring("break-even-probability-".length()));
                        double tickX = chart.getXAxis().localToScene(chart.getXAxis().getDisplayPosition(year), 0).getX();
                        double valueX = node.localToScene(node.getBoundsInLocal()).getCenterX();
                        assertEquals(tickX, valueX, 1.0, "Probability must track year " + year);
                    }
                    assertAnnotationWithinPlot(view, chart);
                }
                Node marker = view.lookup("#break-even-marker");
                assertTrue(marker.getAccessibleText().contains("At least one alive:"));
                assertTrue(((Label) view.lookup("#break-even-probability-heading")).getTooltip().getText().contains("January 1"));
                var before = preparedResult.metrics();
                selector.setValue(BreakEvenMetric.AFTER_TAX_ESTATE);
                assertNull(view.lookup("#break-even-marker"));
                assertSame(before, preparedResult.metrics());
                assertEquals(3, chart.lookupAll(".break-even-claim-label").size());
            } finally { stage.close(); }
        });
    }

    private static BreakEvenAnalysisResult result(long... differences) {
        List<ProjectionYear> baseline = new ArrayList<>(), current = new ArrayList<>();
        for (int i = 0; i < differences.length; i++) {
            baseline.add(year(2027 + i, 1000));
            current.add(year(2027 + i, 1000 + differences[i]));
        }
        return new BreakEvenAnalyzer().analyze(snapshot(baseline, 62), snapshot(current, 70));
    }
    private static BreakEvenProjectionSnapshot snapshot(List<ProjectionYear> years, int spouseClaimingAge) {
        return new BreakEvenProjectionSnapshot(years, List.of(), new BreakEvenPlanSummary(
                new BreakEvenPlanSummary.PersonSummary("David", LocalDate.of(1963, 6, 4), 70),
                new BreakEvenPlanSummary.PersonSummary("Lisa", LocalDate.of(1965, 2, 28), spouseClaimingAge)));
    }
    private static ProjectionYear year(int year, long amount) {
        return ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).withPrimaryPersonAge(year - 1963)
                .withEndingInvestableAssets(amount).build();
    }
    @SuppressWarnings("unchecked") private static <T> T lookup(Parent root, String selector) { return (T) root.lookup(selector); }
    private static String texts(Node node) {
        String result = node instanceof Label label ? label.getText() + "\n" : "";
        if (node instanceof ScrollPane scroll) result += texts(scroll.getContent());
        else if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) result += texts(child);
        return result;
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
}
