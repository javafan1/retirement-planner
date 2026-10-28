package com.daviddunn.retirementplanner.ui.baseline;

import com.daviddunn.retirementplanner.app.baseline.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.*;
import static com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineParameterComparison.*;

class BaselineDetailsViewTest {
    @TempDir Path dir;
    @BeforeAll static void startFx() throws Exception {
        var ready = new CompletableFuture<Void>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
        ready.get(20, TimeUnit.SECONDS);
    }
    static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    ApplicationController source() throws Exception {
        var c = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
        c.newPlan(plan(true)); return c;
    }
    void stop(ApplicationController c) { c.getAnalysisParameters().shutdown(); c.getAnalysisResults().shutdownIfIdle(); }
    @Test void adjacentActionUsesOnlyAcceptedComparisonAndKeepsMissingChartAvailable() throws Exception {
        var c = source();
        try {
            fx(() -> {
                var view = new ResultsSummaryView(c);
                var root = new VBox(view); var stage = new Stage(); stage.setScene(new Scene(root, 1180, 820)); stage.show();
                try {
                    view.applyCss(); view.layout();
                    var button = (Button) view.lookup("#baseline-details-action");
                    var breakEven = (Button) view.lookup("#break-even-action");
                    assertNotNull(button); assertTrue(button.isDisabled());
                    var siblings = ((Pane) button.getParent()).getChildren();
                    assertEquals(siblings.indexOf(breakEven) + 1, siblings.indexOf(button));
                    c.getCurrentProjection(); c.saveCurrentAsBaseline("Original"); c.compareProjections();
                    view.load(c.getCurrentPlan(), c.peekCurrentProjection(), c.getCurrentNonInvestableAssetProjections());
                    assertTrue(button.isDisabled());
                    c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1100000")); c.markModified();
                    assertTrue(button.isDisabled());
                    c.invalidateProjection(); c.compareProjections();
                    view.load(c.getCurrentPlan(), c.peekCurrentProjection(), c.getCurrentNonInvestableAssetProjections());
                    assertFalse(button.isDisabled());
                    set(c, "baselineProjection", null); set(c, "baselineDetailsChart", null);
                    set(c, "projectionEngine", new ProjectionEngine() {
                        @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan p) { throw new AssertionError("Details cannot project"); }
                        @Override public Projection project(RetirementPlan p) { throw new AssertionError("Details cannot project"); }
                    });
                    var received = new java.util.concurrent.atomic.AtomicReference<BaselineDetailsSession>();
                    view.setOnBaselineDetails(received::set);
                    boolean modified = c.isModified(); long revision = c.getSourcePlanRevision();
                    var configurations = c.getCurrentPlan().getAnalyzerConfigurations();
                    button.fire(); assertNotNull(received.get()); assertNull(received.get().baselineChart());
                    assertEquals(modified, c.isModified()); assertEquals(revision, c.getSourcePlanRevision());
                    assertSame(configurations, c.getCurrentPlan().getAnalyzerConfigurations());
                    var dialog = new BaselineDetailsDialog(received.get()); assertTrue(dialog.isResizable());
                    assertEquals("Baseline Projection Details", dialog.getTitle());
                    c.newPlan(plan(false)); button.fire(); assertTrue(button.isDisabled());
                } finally { stage.close(); }
                return null;
            });
        } finally { stop(c); }
    }
    static BaselineDetailsSession sample(String kind, com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel chart) {
        var original = ProjectionInputs.capture(plan(true));
        var p = plan(true);
        p.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1100000.0023400"));
        var changed = ProjectionInputs.capture(p);
        List<Row> rows = compare(original, kind.equals("unchanged") ? original : changed);
        if (kind.equals("ambiguous")) {
            Value a = new Fields(Map.of("name", new TextValue("Same"), "ownership", new TextValue("PRIMARY"), "accountType", new TextValue("traditional"), "currentBalance", new NumberValue(new BigDecimal("1"))));
            Value b = new Fields(Map.of("name", new TextValue("Same"), "ownership", new TextValue("PRIMARY"), "accountType", new TextValue("traditional"), "currentBalance", new NumberValue(new BigDecimal("2"))));
            Value c = new Fields(Map.of("name", new TextValue("Same"), "ownership", new TextValue("PRIMARY"), "accountType", new TextValue("traditional"), "currentBalance", new NumberValue(new BigDecimal("3"))));
            rows = compare(new ProjectionInputs(new Fields(Map.of("accounts", new Items(List.of(a, b))))), new ProjectionInputs(new Fields(Map.of("accounts", new Items(List.of(c, c))))));
        }
        return new BaselineDetailsSession("Synthetic original baseline", java.time.LocalDateTime.of(2026, 1, 1, 12, 0), UUID.fromString("11111111-1111-4111-8111-111111111111"), "Synthetic plan", java.time.Instant.parse("2026-01-02T12:00:00Z"), "2026–2028",
                "Frozen current inputs; no editable values substituted.", rows, kind.equals("missing") || kind.equals("ambiguous") ? null : chart);
    }
    @ParameterizedTest @CsvSource({"changed,1180,820,false", "unchanged,1180,820,false", "missing,900,650,false", "ambiguous,760,650,false", "changed,900,650,true", "ambiguous,760,650,true"})
    void renderedStatesAreReadOnlyFilterableAndThemeSafe(String kind, int width, int height, boolean dark) throws Exception {
        var c = source(); c.getCurrentProjection(); c.saveCurrentAsBaseline("Original"); c.compareProjections();
        var chart = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(c.getBaselineProjection().getYears(), c.getBaselineNonInvestableAssetProjections(),
                com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(c.getCurrentPlan().getBaseline().getSnapshot().getHousehold()), c.getCurrentPlan().getBaseline().getSnapshot().getAccountPortfolio().getAccounts());
        var session = sample(kind, chart); var originalRows = session.rows();
        var preview = new Stage[1];
        try {
            fx(() -> {
                var view = new BaselineDetailsView(session);
                if (dark) view.setStyle("-fx-base: #202328; -fx-background: #202328; -fx-control-inner-background: #252830; -fx-text-inner-color: #f1f1f1; -fx-text-base-color: #f1f1f1;");
                var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
                var stage = new Stage(); stage.setScene(new Scene(scroll, width, height)); stage.show();
                preview[0] = stage;
                try {
                    scroll.applyCss(); scroll.layout();
                    var toggle = (CheckBox) view.lookup("#baseline-details-changed-only"); assertFalse(toggle.isSelected());
                    for (Node node : view.lookupAll(".table-view")) assertEquals(List.of("Parameter", "Baseline", "Current", "Difference"), ((TableView<?>)node).getColumns().stream().map(TableColumn::getText).toList());
                    assertTrue(view.lookupAll(".table-view").stream().allMatch(n -> n.getBoundsInParent().getWidth() <= width));
                    save(scroll, kind + "-" + width + "-" + (dark ? "dark" : "light") + "-top");
                    toggle.setSelected(true); scroll.applyCss(); scroll.layout();
                    for (Node node : view.lookupAll(".table-view")) for (Object row : ((TableView<?>)node).getItems()) assertNotEquals(State.UNCHANGED, ((Row)row).state());
                    if (kind.equals("ambiguous")) assertTrue(view.lookupAll(".titled-pane").stream().map(n -> ((TitledPane)n).getText()).allMatch(t -> t.contains("Uncertain")));
                    if (kind.equals("changed")) {
                        assertFalse(view.lookupAll(".baseline-changed").isEmpty());
                        for (Node node : view.lookupAll(".baseline-changed")) assertNotNull(((Region)node).getBackground());
                    }
                    scroll.setVvalue(1); scroll.layout(); save(scroll, kind + "-" + width + "-" + (dark ? "dark" : "light") + "-filtered");
                    for (Node node : view.lookupAll(".titled-pane")) { var pane = (TitledPane)node; pane.setExpanded(false); assertFalse(pane.isExpanded()); pane.setExpanded(true); }
                    assertSame(originalRows, session.rows());
                } catch (Throwable failure) { stage.close(); throw failure; }
                return null;
            });
            // A separate FX turn lets the changed-only table skins finish their layout before
            // inspecting the actual rendered cells, rather than capturing the previous pulse.
            fx(() -> {
                var scroll = (ScrollPane) preview[0].getScene().getRoot();
                var view = (BaselineDetailsView) scroll.getContent();
                view.applyCss(); view.layout(); scroll.layout();
                assertTrue(((CheckBox) view.lookup("#baseline-details-changed-only")).isSelected());
                for (Node node : view.lookupAll(".table-row-cell")) {
                    var row = (TableRow<?>) node;
                    if (!row.isEmpty()) assertTrue(row.getHeight() >= 32, "Wrapped rows require readable height");
                }
                scroll.setVvalue(1); scroll.layout();
                save(scroll, kind + "-" + width + "-" + (dark ? "dark" : "light") + "-filtered");
                return null;
            });
        } finally {
            fx(() -> { if (preview[0] != null) preview[0].close(); return null; });
            stop(c);
        }
    }
    static void save(Node node, String name) throws Exception {
        Files.createDirectories(Path.of("target/phase12b-previews"));
        var image = node.snapshot(null, null);
        var output = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, reader.getArgb(x,y));
        javax.imageio.ImageIO.write(output, "png", Path.of("target/phase12b-previews/" + name + ".png").toFile());
    }
    @ParameterizedTest @CsvSource({"1180,820,false", "900,650,true"})
    void savedOriginalChartRestoresAndSupportsSelectionWithoutCalculation(int width, int height, boolean dark) throws Exception {
        var c = source(); c.getCurrentProjection(); c.saveCurrentAsBaseline("Saved original baseline");
        c.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1150000"));
        c.invalidateProjection(); c.getCurrentProjection(); var file = dir.resolve("visual.json"); c.saveAs(file);
        var restored = source(); var stage = new Stage[1];
        try {
            set(restored, "projectionEngine", new ProjectionEngine() {
                @Override public Projection project(RetirementPlan plan) { throw new AssertionError("No chart recalculation"); }
                @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) { throw new AssertionError("No chart recalculation"); }
            });
            restored.open(file); restored.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            restored.compareProjections(); var session = restored.prepareBaselineDetails(restored.peekCurrentProjection());
            assertNotNull(session.baselineChart());
            fx(() -> {
                var view = new BaselineDetailsView(session);
                if (dark) view.setStyle("-fx-base: #202328; -fx-background: #202328; -fx-control-inner-background: #252830; -fx-text-inner-color: #f1f1f1; -fx-text-base-color: #f1f1f1;");
                var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
                stage[0] = new Stage(); stage[0].setScene(new Scene(scroll, width, height)); stage[0].show();
                scroll.applyCss(); scroll.layout(); return null;
            });
            fx(() -> {
                var root = stage[0].getScene().getRoot(); root.applyCss(); root.layout();
                var points = root.lookupAll(".projection-total-point");
                assertEquals(session.baselineChart().years().size(), points.size());
                var point = points.iterator().next(); assertNotNull(point.getAccessibleText());
                assertTrue(point.getProperties().values().stream().anyMatch(Tooltip.class::isInstance), "Hover tooltip installed");
                point.getOnMouseClicked().handle(null);
                var selected = (Label) root.lookup("#projection-chart-selected-year");
                assertNotNull(selected.getTooltip()); assertTrue(selected.getText().contains("202"));
                save(root, "phase12d-restored-" + width + "-" + (dark ? "dark" : "light"));
                assertFalse(restored.isModified()); return null;
            });
            fx(() -> {
                var scroll = (ScrollPane) stage[0].getScene().getRoot();
                scroll.setVvalue(0); scroll.layout();
                save(scroll, "phase12d-restored-" + width + "-" + (dark ? "dark" : "light") + "-chart");
                return null;
            });
        } finally {
            fx(() -> { if (stage[0] != null) stage[0].close(); return null; });
            stop(restored); stop(c);
        }
    }
    @Test void percentagePointsAndAgeDifferenceFormattingUseExactNumbers() {
        var rates = compare(new ProjectionInputs(new Fields(Map.of("inflationRate", new NumberValue(new BigDecimal("0.0250"))))),
                new ProjectionInputs(new Fields(Map.of("inflationRate", new NumberValue(new BigDecimal("0.0275")))))).getFirst();
        assertEquals("+0.25 pp", BaselineDetailsView.difference(rates));
        assertEquals("2.50%", BaselineDetailsView.format(rates.baseline(), rates.units()));
        var ages = compare(new ProjectionInputs(new Fields(Map.of("claimingAge", new NumberValue(new BigDecimal("65"))))),
                new ProjectionInputs(new Fields(Map.of("claimingAge", new NumberValue(new BigDecimal("67")))))).getFirst();
        assertEquals("+2 years", BaselineDetailsView.difference(ages));
    }
}
