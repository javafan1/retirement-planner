package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerContextualInputsTest {
    private static final List<String> SHARED = List.of("primaryMortalityAdjustment", "spouseMortalityAdjustment",
            "mortalityDate", "pvDate", "discountRate");

    @BeforeAll static void startFx() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }

    @ParameterizedTest @EnumSource(SocialSecurityAnalyzerJobController.Mode.class)
    void onlyRelevantEditableInputsAreExposed(SocialSecurityAnalyzerJobController.Mode mode) throws Exception {
        fx(f -> {
            f.stage.show(); layout(f.stage);
            f.navigation.select(mode); layout(f.stage);
            boolean longevity = mode == SOCIAL_SECURITY || mode == WEIGHTED;
            for (String name : SHARED) assertEquals(longevity, exposed(field(f.dialog, name, Control.class)), name);
            assertEquals(mode == QUICK, exposed(field(f.dialog, "integratedCandidateCount", Spinner.class)));
            var baseline = field(f.dialog, "baselineInputs", CurrentStrategyBaselineView.class);
            assertEquals(mode == WEIGHTED, exposed(baseline.primarySurvivor));
            assertEquals(mode == WEIGHTED, exposed(baseline.spouseSurvivor));
            var reference = field(f.dialog, "inputSection", TitledPane.class);
            assertTrue(reference.getText().contains("Input Reference"));
            assertFalse(reference.isExpanded());
            assertTrue(field(f.dialog, "inheritedPlan", Label.class).getText().contains("1963"));
            if (mode == EXHAUSTIVE) {
                assertTrue(field(f.dialog, "inheritedPlan", Label.class).getText().contains("Configured plan death scenario"));
                assertFalse(field(f.dialog, "exhaustiveRunButton", Button.class).isDisabled());
            }
            if (mode == QUICK) assertTrue(field(f.dialog, "quickPrerequisite", Label.class).getText().contains("Social Security Only result required"));
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void repeatedNavigationRetainsExactlyOneControlHelpAndListenerSet() throws Exception {
        fx(f -> {
            var controls = SHARED.stream().map(name -> uncheckedField(f.dialog, name, Control.class)).toList();
            var parents = controls.stream().map(Node::getParent).toList();
            var tooltips = controls.stream().map(Control::getTooltip).toList();
            field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("invalid pending factor");
            field(f.dialog, "discountRate", TextField.class).setText("2.125");
            field(f.dialog, "mortalityDate", DatePicker.class).setValue(LocalDate.of(2028, 2, 3));
            field(f.dialog, "pvDate", DatePicker.class).setValue(LocalDate.of(2027, 8, 9));
            field(f.dialog, "mortalityDate", DatePicker.class).getEditor().setText("unfinished date");
            long revision = field(f.dialog, "assumptionsRevision", Long.class);
            for (int cycle = 0; cycle < 8; cycle++) for (var choice : f.navigation.choices()) {
                f.navigation.select(choice.mode());
                for (int index = 0; index < controls.size(); index++) {
                    Control control = field(f.dialog, SHARED.get(index), Control.class);
                    assertSame(controls.get(index), control);
                    assertSame(parents.get(index), control.getParent());
                    assertSame(tooltips.get(index), control.getTooltip());
                    assertSame(control, ((Label) ((VBox) control.getParent()).getChildren().getFirst()).getLabelFor());
                }
            }
            assertEquals(revision, field(f.dialog, "assumptionsRevision", Long.class));
            assertEquals("invalid pending factor", field(f.dialog, "primaryMortalityAdjustment", TextField.class).getText());
            assertEquals("2.125", field(f.dialog, "discountRate", TextField.class).getText());
            assertEquals(LocalDate.of(2028, 2, 3), field(f.dialog, "mortalityDate", DatePicker.class).getValue());
            assertEquals("unfinished date", field(f.dialog, "mortalityDate", DatePicker.class).getEditor().getText());
            assertEquals(LocalDate.of(2027, 8, 9), field(f.dialog, "pvDate", DatePicker.class).getValue());
            field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("0.85");
            assertEquals(revision + 1, field(f.dialog, "assumptionsRevision", Long.class), "One edit, one established invalidation callback");
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void hiddenInvalidInputsDoNotGateExhaustiveOrRewriteFrozenResults() throws Exception {
        fx(f -> {
            var original = DeterministicHeatMapAdapterTest.fixture();
            set(f.dialog, "exhaustivePresentation", original);
            var render = f.dialog.getClass().getDeclaredMethod("renderExhaustive", ExhaustiveIntegratedSearchPresentation.class);
            render.setAccessible(true); render.invoke(f.dialog, original);
            var map = field(f.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            map.metric.setValue(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT);
            var selection = map.reportSelection();
            var summary = field(f.dialog, "exhaustiveSummary", VBox.class);
            String frozen = text(summary);
            f.navigation.select(EXHAUSTIVE);
            field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("invalid");
            field(f.dialog, "discountRate", TextField.class).setText("bad rate");
            assertFalse(exposed(field(f.dialog, "primaryMortalityAdjustment", TextField.class)));
            assertFalse(field(f.dialog, "exhaustiveRunButton", Button.class).isDisabled());
            assertTrue(field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class).run.isDisabled());
            assertEquals("", field(f.dialog, "exhaustiveStale", Label.class).getText());
            assertSame(original, field(f.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertSame(selection, map.reportSelection());
            assertEquals(frozen, text(summary));
        });
    }

    @Test void fixedGraphicalProgressAndOriginalCancelStayAvailableInEveryWorkspace() throws Exception {
        fx(f -> {
            assertTrue(f.jobs.start(WEIGHTED, (p, c) -> "discard", result -> fail("Cancelled work published"), error -> fail(error)));
            for (var choice : f.navigation.choices()) {
                f.navigation.select(choice.mode());
                var cancel = field(f.dialog, "sharedCancel", Button.class);
                var progress = field(f.dialog, "sharedProgress", AnalysisProgressView.class);
                assertTrue(exposed(cancel));
                assertTrue(exposed(progress));
                assertTrue(descendant(cancel, f.navigation.getTop()));
                assertTrue(descendant(progress, f.navigation.getTop()));
                assertTrue(field(f.navigation, "activity", Label.class).getText().contains("Longevity-Weighted"));
                assertEquals(WEIGHTED, f.jobs.mode());
                assertEquals(1, f.jobs.generation());
            }
            field(f.dialog, "sharedCancel", Button.class).fire();
            assertTrue(f.coordinator.isBusy());
            f.executor.run();
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, f.jobs.state());
            assertFalse(exposed(field(f.dialog, "sharedProgress", AnalysisProgressView.class)));
        });
    }

    @Test void validSharedSessionPropagationAndPendingInvalidTextAreUnchanged() throws Exception {
        fx(f -> {
            var source = new ApplicationController();
            source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
            long revision = source.getSourcePlanRevision();
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, source);
            try {
                var navigation = field(dialog, "navigation", AnalyzerNavigationView.class);
                var factor = field(dialog, "primaryMortalityAdjustment", TextField.class);
                factor.setText("0.80");
                assertEquals(0, new BigDecimal("0.80").compareTo(source.getLongevitySessionSettings().primaryAdjustment().factor()));
                var valid = source.getLongevitySessionSettings();
                factor.setText("unfinished");
                for (var choice : navigation.choices()) navigation.select(choice.mode());
                assertEquals("unfinished", factor.getText());
                assertEquals(valid, source.getLongevitySessionSettings());
                assertEquals(revision, source.getSourcePlanRevision());
            } finally { invoke(dialog, "close"); field(dialog, "stage", Stage.class).close(); }
        });
    }

    @ParameterizedTest @CsvSource({"1920,1080", "1366,768", "1180,820", "900,650", "800,600"})
    void configurationWrapsAndRunActionsRemainReachable(int width, int height) throws Exception {
        fx(f -> {
            f.stage.setMinWidth(0); f.stage.setMinHeight(0);
            f.stage.setWidth(width); f.stage.setHeight(height); f.stage.show(); layout(f.stage);
            for (var choice : f.navigation.choices()) {
                f.navigation.select(choice.mode()); layout(f.stage);
                var scroll = (ScrollPane) f.navigation.getCenter();
                var viewport = scroll.lookup(".viewport");
                var visible = viewport.localToScene(viewport.getBoundsInLocal());
                if (choice.mode() == SOCIAL_SECURITY || choice.mode() == WEIGHTED) for (String name : SHARED) {
                    var control = field(f.dialog, name, Control.class);
                    var bounds = control.localToScene(control.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= visible.getMinX() - 1, name);
                    assertTrue(bounds.getMaxX() <= visible.getMaxX() + 1, name);
                }
                var button = switch (choice.mode()) {
                    case SOCIAL_SECURITY -> field(f.dialog, "runButton", Button.class);
                    case QUICK -> field(f.dialog, "integratedRunButton", Button.class);
                    case EXHAUSTIVE -> field(f.dialog, "exhaustiveRunButton", Button.class);
                    case WEIGHTED -> field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class).run;
                };
                IntegratedAnalysisResultTabs.reveal(button); layout(f.stage);
                var bounds = button.localToScene(button.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= visible.getMinY() - 1);
                assertTrue(bounds.getMaxY() <= visible.getMaxY() + 1);
                assertTrue(bounds.getMaxX() <= visible.getMaxX() + 1);
            }
        });
    }

    @Test void referenceExpansionDoesNotEditInputsOrStartAnalysis() throws Exception {
        fx(f -> {
            f.stage.show(); layout(f.stage);
            var reference = field(f.dialog, "inputSection", TitledPane.class);
            var control = field(f.dialog, "discountRate", TextField.class);
            control.setText("unfinished");
            long revision = field(f.dialog, "assumptionsRevision", Long.class);
            for (int pass = 0; pass < 3; pass++) {
                reference.setExpanded(true); layout(f.stage);
                assertTrue(reference.getHeight() > 100);
                reference.setExpanded(false); layout(f.stage);
            }
            assertEquals("unfinished", control.getText());
            assertEquals(revision, field(f.dialog, "assumptionsRevision", Long.class));
            assertEquals(0, f.jobs.generation());
        });
    }

    private static boolean exposed(Node node) {
        if (node.getScene() == null) return false;
        for (Node current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return true;
    }
    private static boolean descendant(Node node, Node parent) {
        for (Node current = node; current != null; current = current.getParent()) if (current == parent) return true;
        return false;
    }
    private static String text(Node node) {
        String value = node instanceof Labeled label ? label.getText() : "";
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) value += "\n" + text(child);
        return value;
    }
    private static void layout(Stage stage) {
        for (int pass = 0; pass < 3; pass++) { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); }
    }
    private static class Fixture implements AutoCloseable {
        final SocialSecurityAnalyzerJobControllerTest.ManualExecutor executor = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
        final SocialSecurityAnalysisJobCoordinator coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
        final SocialSecurityAnalyzerJobController jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
        final SocialSecurityStrategyAnalyzerDialog dialog = new SocialSecurityStrategyAnalyzerDialog(null,
                LongevityWeightedAnalysisRequestFactoryTest.plan(), jobs);
        final AnalyzerNavigationView navigation = field(dialog, "navigation", AnalyzerNavigationView.class);
        final Stage stage = field(dialog, "stage", Stage.class);
        Fixture() throws Exception { }
        @Override public void close() throws Exception { stage.close(); invoke(dialog, "close"); coordinator.close(); }
    }
    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }
    private static <T> T uncheckedField(Object owner, String name, Class<T> type) {
        try { return field(owner, name, type); } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void invoke(Object owner, String name) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(owner);
    }
    private static void fx(Check check) throws Exception {
        var task = new FutureTask<Void>(() -> { try (var fixture = new Fixture()) { check.run(fixture); } return null; });
        Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
    @FunctionalInterface private interface Check { void run(Fixture fixture) throws Exception; }
}
