package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenCompactLayoutTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(task); } catch (IllegalStateException started) { Platform.runLater(task); }
        task.get(30, TimeUnit.SECONDS);
    }

    @Test void completeChartAndMortalityRowFitWithoutScrolling() throws Exception {
        var baseline = snapshot(62);
        var current = snapshot(65);
        var result = new BreakEvenAnalyzer().analyze(baseline, current);
        var insight = new BreakEvenInsightService().prepare(result, baseline.years(), current.years());
        var context = new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory().create(result,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)));
        var beforeMetrics = result.metrics();
        var beforeSurvival = context.survival();
        FutureTask<Void> task = new FutureTask<>(() -> {
            var dialog = new BreakEvenAnalysisDialog(result, context, insight);
            dialog.show();
            try {
                var pane = dialog.getDialogPane();
                var scroll = (ScrollPane) pane.getContent();
                var view = (BreakEvenAnalysisView) scroll.getContent();
                for (int i = 0; i < 4; i++) { pane.applyCss(); pane.layout(); scroll.layout(); view.layout(); }
                var chart = (BreakEvenChart) view.lookup("#break-even-chart"); chart.layout();
                var heading = view.lookup("#break-even-probability-heading");
                var viewport = scroll.lookup(".viewport");
                double aboveChart = chart.localToScene(chart.getBoundsInLocal()).getMinY()
                        - view.localToScene(view.getBoundsInLocal()).getMinY();
                double mortalityBottom = heading.localToScene(heading.getBoundsInLocal()).getMaxY();
                double viewportBottom = viewport.localToScene(viewport.getBoundsInLocal()).getMaxY();
                String mode = "after";
                Path folder = Path.of("target", "break-even-compact"); Files.createDirectories(folder);
                Files.writeString(folder.resolve(mode + "-measurements.txt"), "aboveChart=" + aboveChart
                        + "\nchartHeight=" + chart.getHeight() + "\nplotHeight=" + chart.getYAxis().getHeight()
                        + "\nmortalityBottom=" + mortalityBottom + "\nviewportBottom=" + viewportBottom
                        + "\ndialog=" + pane.getWidth() + " x " + pane.getHeight()
                        + "\nresults=" + result.metrics().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue().sustainedBreakEvenYear()).toList());
                var snapshot = pane.snapshot(null, null);
                var image = new java.awt.image.BufferedImage(1920, 1080, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics(); graphics.setColor(new java.awt.Color(244, 247, 251)); graphics.fillRect(0, 0, 1920, 1080); graphics.dispose();
                int left = (1920 - (int) snapshot.getWidth()) / 2, top = (1080 - (int) snapshot.getHeight()) / 2;
                for (int y = 0; y < snapshot.getHeight(); y++) for (int x = 0; x < snapshot.getWidth(); x++) image.setRGB(left + x, top + y, snapshot.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(image, "png", folder.resolve(mode + "-desktop.png").toFile());
                {
                    assertEquals(0, scroll.getVvalue());
                    assertTrue(mortalityBottom <= viewportBottom, "Mortality row must fit: " + mortalityBottom + " <= " + viewportBottom);
                    assertEquals(400, chart.getHeight(), 1, "Keep the existing chart height");
                    assertFalse(context.survival().isEmpty());
                    assertEquals(3, chart.lookupAll(".timeline-claim-label").size());
                    assertEquals(BreakEvenMetric.TOTAL_NET_WORTH, ((ComboBox<?>) view.lookup("#break-even-metric")).getValue());
                    assertNotNull(pane.lookupButton(ButtonType.CLOSE));
                    var details = (VBox) view.lookup("#break-even-insight-details");
                    var toggle = (Hyperlink) view.lookup("#break-even-insight-toggle");
                    assertFalse(details.isVisible()); assertFalse(details.isManaged());
                    assertTrue(details.getChildren().stream().filter(Label.class::isInstance)
                            .map(node -> ((Label) node).getText()).anyMatch(text -> text.startsWith("Through ")));
                    var period = view.lookup("#break-even-period");
                    var selector = view.lookup("#break-even-metric");
                    assertTrue(Math.abs(period.localToScene(period.getBoundsInLocal()).getMinY()
                            - selector.localToScene(selector.getBoundsInLocal()).getMinY()) < 20);
                    var chartValues = chart.getData().getFirst().getData().stream().map(point -> point.getYValue()).toList();
                    double collapsedHeight = view.getHeight();
                    toggle.fire();
                    for (int i = 0; i < 3; i++) { pane.applyCss(); pane.layout(); scroll.layout(); view.layout(); }
                    assertTrue(details.isVisible()); assertTrue(details.isManaged());
                    assertTrue(view.getHeight() > collapsedHeight);
                    assertEquals("Hide details", toggle.getText());
                    assertEquals(chartValues, chart.getData().getFirst().getData().stream().map(point -> point.getYValue()).toList());
                    toggle.fire();
                    assertFalse(details.isVisible());
                    // Responsive layout keeps content inside the viewport; scrolling is legitimate here.
                    for (int width : new int[]{760, 420}) {
                        scroll.resize(width, 760);
                        for (int i = 0; i < 3; i++) { scroll.applyCss(); scroll.layout(); view.layout(); }
                        var controls = (GridPane) view.lookup("#break-even-comparison-controls");
                        assertTrue(controls.getWidth() <= view.getWidth());
                        assertTrue(selector.localToScene(selector.getBoundsInLocal()).getMaxX()
                                <= view.localToScene(view.getBoundsInLocal()).getMaxX() + 1);
                        if (controls.getWidth() < 720) assertTrue(selector.localToScene(selector.getBoundsInLocal()).getMinY()
                                >= period.localToScene(period.getBoundsInLocal()).getMaxY());
                    }
                }
                assertSame(beforeMetrics, result.metrics()); assertSame(beforeSurvival, context.survival());
            } finally { dialog.close(); }
            return null;
        });
        Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }

    static BreakEvenProjectionSnapshot snapshot(int spouseAge) {
        var plan = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
        var primary = plan.getHousehold().getPrimaryPerson(); var spouse = plan.getHousehold().getSpouse();
        primary.setFirstName("David"); primary.setBirthDate(LocalDate.of(1963, 6, 4)); primary.setMortalityCategory(MortalityCategory.MALE);
        spouse.setFirstName("Lisa"); spouse.setBirthDate(LocalDate.of(1965, 2, 28)); spouse.setMortalityCategory(MortalityCategory.FEMALE);
        primary.addIncomeSource(new SocialSecurityIncome("David SS", AccountOwnership.PRIMARY, LocalDate.of(2033, 6, 4), null, new BigDecimal("3000"), 70, BigDecimal.ZERO, 2027));
        spouse.addIncomeSource(new SocialSecurityIncome("Lisa SS", AccountOwnership.SPOUSE, LocalDate.of(1965 + spouseAge, 2, 28), null, new BigDecimal("2000"), spouseAge, BigDecimal.ZERO, 2027));
        plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.04"), new BigDecimal("0.02"), 30, LocalDate.of(2027, 1, 1)));
        plan.getHousehold().addExpense(new Expense("Living expenses", new BigDecimal("80000")));
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Brokerage", AccountOwnership.JOINT, new BigDecimal("500000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("1500000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", AccountOwnership.SPOUSE, new BigDecimal("100000")));
        return new BreakEvenProjectionSnapshot(new ProjectionEngine().project(plan).getYears(), List.of(), BreakEvenPlanSummary.from(plan.getHousehold()));
    }
}