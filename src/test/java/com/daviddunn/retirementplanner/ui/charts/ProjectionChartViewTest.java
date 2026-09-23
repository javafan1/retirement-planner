package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.charts.ProjectionChartFixtures.*;

class ProjectionChartViewTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        task.get(30, TimeUnit.SECONDS);
    }

    @Test void switchingMetricsRendersPreparedValuesAndPreservesEventsWithoutComparisonFeatures() throws Exception {
        fx(() -> {
            var model = ProjectionChartFixtures.model(List.of(year(2027, 75, 0), year(2029, 75, 30),
                    year(2033, 0, 30), year(2035, 0, 0)), List.of(), people());
            var view = new ProjectionChartView();
            view.load(model);
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 1100, 480)); stage.show();
            try {
                ComboBox<ProjectionChartMetric> selector = (ComboBox<ProjectionChartMetric>) view.lookup("#projection-chart-metric");
                assertEquals(ProjectionChartMetric.INVESTABLE_ASSETS, selector.getValue());
                assertTrue(view.lookup("#projection-chart") instanceof StackedAreaChart);
                view.applyCss(); view.layout();
                var firstPoint = view.lookupAll(".projection-total-point").stream()
                        .filter(node -> node.getAccessibleText().startsWith("2027")).findFirst().orElseThrow();
                assertTrue(firstPoint.getAccessibleText().contains("30.0%"));
                assertTrue(firstPoint.getAccessibleText().contains("Actual Roth Conversion:"));
                assertFalse(firstPoint.getAccessibleText().contains("Actual RMD:"));
                firstPoint.getOnMouseClicked().handle(null);
                assertTrue(((Label) view.lookup("#projection-chart-selected-year")).getText().startsWith("2027"));
                for (var metric : ProjectionChartMetric.values()) {
                    selector.setValue(metric);
                    view.applyCss(); view.layout();
                    XYChart<Number, Number> chart = (XYChart<Number, Number>) view.lookup("#projection-chart");
                    chart.layout();
                    assertEquals(2, chart.lookupAll(".timeline-claim-label").size());
                    assertEquals(2, chart.lookupAll(".projection-roth-band").size());
                    assertEquals(2, chart.lookupAll(".projection-rmd-band").size());
                                        assertEquals(4, chart.lookupAll(".projection-period-label").size());
                    assertTrue(chart.lookupAll(".projection-period-label").stream()
                            .map(node -> ((Label) node).getText()).anyMatch(text -> text.equals("Roth Conversion\n2027")));
                    for (var node : chart.lookupAll(".projection-roth-band")) {
                        var band = (javafx.scene.shape.Rectangle) node;
                        assertTrue(band.getWidth() > 0);
                        assertEquals(chart.getYAxis().getHeight(), band.getHeight(), 0.01);
                    }
                    assertFalse(chart.isHorizontalZeroLineVisible());
                    assertNull(chart.lookup("#break-even-marker"));
                    assertNull(view.lookup("#break-even-probability-values"));
                    if (metric != ProjectionChartMetric.INVESTABLE_ASSETS) {
                        assertTrue(chart instanceof LineChart);
                        assertEquals(1, chart.getData().size());
                        assertEquals(model.years().getFirst().values().get(metric), chart.getData().getFirst().getData().getFirst().getYValue());
                    }
                }
                selector.setValue(ProjectionChartMetric.INVESTABLE_ASSETS);
                for (int width : new int[]{1400, 900, 500}) {
                    view.resize(width, 480); view.applyCss(); view.layout();
                    XYChart<?, ?> chart = (XYChart<?, ?>) view.lookup("#projection-chart"); chart.layout();
                    var labels = new ArrayList<>(chart.lookupAll(".timeline-claim-label"));
                    assertFalse(labels.get(0).getBoundsInParent().intersects(labels.get(1).getBoundsInParent()));
                    for (var label : labels) assertTrue(label.getBoundsInParent().getMaxX() <= chart.getXAxis().getWidth() + 1);
                }
                assertEquals(4, model.years().size());
                view.load(ProjectionChartModel.empty());
                assertTrue(selector.isDisabled());
                assertNull(view.lookup("#projection-chart"));
                view.load(ProjectionChartFixtures.model(List.of(com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder.aProjectionYear().build()), List.of(), null));
                view.applyCss(); view.layout();
                assertTrue(view.lookup("#projection-chart") instanceof StackedAreaChart);
            } finally { stage.close(); }
        });
    }

    @Test void summaryKeepsHeaderCardsSidebarAndTableWithChartImmediatelyBeforeTable() throws Exception {
        fx(() -> {
            var controller = new ApplicationController();
            var summary = new ResultsSummaryView(controller);
            Node header = summary.getTop(), sidebar = summary.getRight();
            var scroll = (ScrollPane) summary.getCenter();
            var content = (VBox) scroll.getContent();
            var cards = content.getChildren().getFirst();
            var projection = new Projection(); projection.addYear(year(2027, 75, 30));
            summary.load(controller.getCurrentPlan(), projection, List.of());
            assertSame(header, summary.getTop()); assertSame(sidebar, summary.getRight());
            assertSame(cards, content.getChildren().getFirst());
            int chartIndex = content.getChildren().indexOf(content.lookup("#projection-chart-panel"));
            assertEquals(2, chartIndex); // Existing metric cards and Baseline section remain first.
            var tableSection = (VBox) content.getChildren().get(chartIndex + 1);
            var table = (TableView<?>) tableSection.getChildren().stream().filter(TableView.class::isInstance).findFirst().orElseThrow();
            assertEquals(projection.getYears(), table.getItems());
            assertEquals(4, content.getChildren().size()); // No old two-chart row below table.
            assertTrue(scroll.isFitToWidth());
        });
    }
    static void fx(Runnable runnable) throws Exception {
        FutureTask<Void> task = new FutureTask<>(runnable, null); Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }

    @Test void realProjectionCompositionAndDesktopPreviewRemainObservational() throws Exception {
        fx(() -> {
            var controller = new ApplicationController();
            var plan = controller.getCurrentPlan();
            var primary = plan.getHousehold().getPrimaryPerson();
            var spouse = plan.getHousehold().getSpouse();
            primary.setFirstName("Alex"); primary.setBirthDate(java.time.LocalDate.of(1963, 6, 4));
            spouse.setFirstName("Sam"); spouse.setBirthDate(java.time.LocalDate.of(1965, 2, 28));
            primary.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                    "Alex SS", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY,
                    java.time.LocalDate.of(2033, 6, 4), null, new java.math.BigDecimal("3000"), 70, ZERO, 2027));
            spouse.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                    "Sam SS", com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE,
                    java.time.LocalDate.of(2027, 2, 28), null, new java.math.BigDecimal("2000"), 62, ZERO, 2027));
            plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                    new java.math.BigDecimal("0.04"), new java.math.BigDecimal("0.02"), 25, java.time.LocalDate.of(2027, 1, 1)));
            var owner = com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
            plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Brokerage", owner, new java.math.BigDecimal("600000")));
            plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA", owner, new java.math.BigDecimal("1500000")));
            plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Roth", owner, new java.math.BigDecimal("200000")));
            plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.CheckingAccount("Cash", owner, new java.math.BigDecimal("50000")));
            plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                    true, 2029, new java.math.BigDecimal("75000"), com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
            // One test-fixture projection. All subsequent UI interactions consume this same result.
            var projection = new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().project(plan);
            var model = ProjectionChartFixtures.model(projection.getYears(), List.of(),
                    com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()));
            assertTrue(model.years().stream().allMatch(ProjectionChartModel.Point::compositionComplete));
            assertFalse(model.rothPeriods().isEmpty()); assertFalse(model.rmdPeriods().isEmpty());
            var summary = new ResultsSummaryView(controller);
            summary.load(plan, projection, List.of());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            Stage stage = new Stage();
            stage.setScene(new Scene(summary, 1900, 1000));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
            stage.show();
            try {
                String before = mapper.writeValueAsString(plan);
                ComboBox<ProjectionChartMetric> selector = (ComboBox<ProjectionChartMetric>) summary.lookup("#projection-chart-metric");
                selector.setValue(ProjectionChartMetric.TOTAL_NET_WORTH);
                selector.setValue(ProjectionChartMetric.INVESTABLE_ASSETS);
                assertEquals(before, mapper.writeValueAsString(plan));
                for (int i = 0; i < 3; i++) { summary.applyCss(); summary.layout(); }
                assertTrue(summary.lookup("#projection-chart") instanceof StackedAreaChart);
                var image = summary.snapshot(null, null);
                var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(png, "png", java.nio.file.Path.of("target", "projection-chart-preview.png").toFile());
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            finally { stage.close(); }
        });
    }
    @Test void roundingProjectionDisplaysUnadjustedStackAndAuthoritativeTotalAndMaterialFallback() throws Exception {
        fx(() -> {
            var plan = ProjectionChartReconciliationTest.roundingPlan();
            var projection = new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().project(plan);
            var summary = new ResultsSummaryView(new ApplicationController());
            summary.load(plan, projection, List.of());
            Stage stage = new Stage();
            stage.setScene(new Scene(summary, 1900, 1000));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
            stage.show();
            try {
                for (int i = 0; i < 3; i++) { summary.applyCss(); summary.layout(); }
                var chart = (StackedAreaChart<Number, Number>) summary.lookup("#projection-chart");
                assertEquals(3, chart.getData().size());
                var point = ProjectionChartModel.from(projection.getYears(), List.of(), null,
                        plan.getAccountPortfolio().getAccounts()).years().get(3);
                var sum = chart.getData().stream().map(series -> (java.math.BigDecimal) series.getData().get(3).getYValue())
                        .reduce(ZERO, java.math.BigDecimal::add);
                assertEquals(0, new java.math.BigDecimal("2211334.90407004880506").compareTo(sum));
                assertEquals(0, new java.math.BigDecimal("2211334.91").compareTo(point.values().get(ProjectionChartMetric.INVESTABLE_ASSETS)));
                assertEquals(30, chart.lookupAll(".projection-total-point").size());
                var image = summary.snapshot(null, null);
                var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(png, "png", java.nio.file.Path.of("target", "projection-chart-rounding-preview.png").toFile());
                var invalid = com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder.aProjectionYear()
                        .withEndingInvestableAssets(1).build();
                var view = (ProjectionChartView) summary.lookup("#projection-chart-panel");
                view.load(ProjectionChartModel.from(List.of(invalid), List.of(), null, List.of()));
                assertTrue(view.lookup("#projection-chart") instanceof LineChart);
                assertTrue(view.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(node -> ((Label) node).getText()).anyMatch(text -> text.equals("Detailed asset composition is unavailable for this projection.")));
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            finally { stage.close(); }
        });
    }}
