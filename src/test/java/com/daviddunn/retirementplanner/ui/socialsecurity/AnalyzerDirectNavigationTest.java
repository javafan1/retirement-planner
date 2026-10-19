package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonDeterministicStrategyTest;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerDirectNavigationTest {
    @BeforeAll static void startFx() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }

    @Test void directChoicesRetainPendingInputsAndNeverStartOrInvalidateJobs() throws Exception {
        fx(f -> {
            f.stage.show();
            layout(f.stage);
            assertEquals(List.of(SOCIAL_SECURITY, QUICK, EXHAUSTIVE, WEIGHTED),
                    f.navigation.choices().stream().map(AnalyzerNavigationView.Choice::mode).toList());
            assertEquals(SOCIAL_SECURITY, f.navigation.selected().mode());
            var pending = field(f.dialog, "discountRate", TextField.class);
            pending.setText("pending invalid text");
            var count = field(f.dialog, "integratedCandidateCount", Spinner.class);
            count.getValueFactory().setValue(17);
            long revision = field(f.dialog, "assumptionsRevision", Long.class);
            for (var choice : f.navigation.choices()) {
                Node retained = choice.workspace();
                f.navigation.select(choice.mode());
                assertSame(retained, f.navigation.selected().workspace());
                assertSame(f.navigation, retained.getScene().getRoot());
                assertEquals("pending invalid text", pending.getText());
                assertEquals(17, count.getValue());
                assertEquals(revision, field(f.dialog, "assumptionsRevision", Long.class));
                assertEquals(0, f.jobs.generation());
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, f.jobs.state());
            }
        });
    }

    @Test void quickPrerequisiteUsesExistingReadinessAndNavigationOnly() throws Exception {
        fx(f -> {
            f.navigation.select(QUICK);
            var prerequisite = field(f.dialog, "quickPrerequisite", Label.class);
            var run = field(f.dialog, "integratedRunButton", Button.class);
            assertTrue(run.isDisabled());
            assertTrue(prerequisite.getText().contains("Run Social Security Only"));
            var go = field(f.dialog, "goToSocialSecurity", Button.class);
            assertEquals("Go to Social Security Only", go.getAccessibleText());
            go.fire();
            assertEquals(SOCIAL_SECURITY, f.navigation.selected().mode());
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void completedHeatMapAndSelectedMetricSurviveEveryChoice() throws Exception {
        fx(f -> {
            var view = field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class);
            var completed = ClaimingStrategyHeatMapViewTest.fixture();
            view.render(completed, "Current elections");
            f.navigation.select(WEIGHTED);
            view.resultTabs.getSelectionModel().select(view.heatMapTab);
            var selected = ClaimingStrategyHeatMapViewTest.button(view.heatMap, 62, 70);
            selected.fire();
            view.heatMap.metric.setValue(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT);
            String text = selected.getText();
            var rows = List.copyOf(view.table.getItems());
            for (var choice : f.navigation.choices()) f.navigation.select(choice.mode());
            assertSame(view.heatMapTab, view.resultTabs.getSelectionModel().getSelectedItem());
            assertTrue(selected.isSelected());
            assertEquals(text, selected.getText());
            assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, view.heatMap.metric.getValue());
            assertEquals(rows, view.table.getItems());
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void navigationDuringWorkKeepsAdmissionProgressAndCancellationOwner() throws Exception {
        fx(f -> {
            assertTrue(f.jobs.start(WEIGHTED, (p, c) -> "completed", result -> fail("Cancelled work published"), failure -> fail(failure)));
            long generation = f.jobs.generation();
            for (var choice : f.navigation.choices()) {
                f.navigation.select(choice.mode());
                assertEquals(generation, f.jobs.generation());
                assertEquals(WEIGHTED, f.jobs.mode());
                assertTrue(field(f.navigation, "activity", Label.class).getText().contains("Longevity-Weighted Search"));
                assertTrue(field(f.navigation, "activity", Label.class).isVisible());
                assertTrue(field(f.dialog, "runButton", Button.class).isDisabled());
                assertTrue(field(f.dialog, "exhaustiveRunButton", Button.class).isDisabled());
                assertTrue(field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class).run.isDisabled());
            }
            field(f.dialog, "sharedCancel", Button.class).fire();
            assertEquals(SocialSecurityAnalyzerJobController.State.CANCELLING, f.jobs.state());
            assertTrue(f.coordinator.isBusy());
            f.executor.run();
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, f.jobs.state());
            assertEquals("Analysis cancelled", f.jobs.status());
            assertFalse(f.coordinator.isBusy());
        });
    }

    @ParameterizedTest
    @CsvSource({"1920,1080", "1366,768", "1180,820", "900,650", "800,600"})
    void responsiveSelectorsRetainWorkspacesAndKeyboardRouting(int width, int height) throws Exception {
        fx(f -> {
            f.stage.setMinWidth(0);
            f.stage.setMinHeight(0);
            f.stage.setWidth(width);
            f.stage.setHeight(height);
            f.stage.show();
            layout(f.stage, width, height);
            boolean compact = f.navigation.getWidth() < 1160;
            assertEquals(compact, f.navigation.isCompact());
            if (!compact) assertTrue(((ScrollPane) f.navigation.getCenter()).getViewportBounds().getWidth() >= 880);
            var selector = field(f.navigation, "compact", ComboBox.class);
            assertEquals(compact, selector.isManaged());
            assertEquals(compact, selector.isVisible());
            if (compact) {
                assertNull(f.navigation.getLeft());
                selector.setValue(f.navigation.choices().get(2));
            } else {
                var buttons = field(f.navigation, "buttons", Map.class);
                var first = (ToggleButton) buttons.get(SOCIAL_SECURITY);
                assertNotNull(f.navigation.getLeft());
                assertTrue(first.getAccessibleText().contains("Social Security Only"));
                first.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.END, false, false, false, false));
                assertEquals(WEIGHTED, f.navigation.selected().mode());
                first.fire();
            }
            var retained = f.navigation.selected().workspace();
            f.stage.setWidth(compact ? 1600 : 900);
            layout(f.stage, compact ? 1600 : 900, height);
            assertSame(retained, f.navigation.selected().workspace());
            assertNotEquals(compact, f.navigation.isCompact());
            f.navigation.focusSelector();
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void individualChoicesKeepIndependentControlsAndNoQuickOrSurvivorInputs() throws Exception {
        fx(f -> {
            var view = new SinglePersonIntegratedView(SinglePersonDeterministicStrategyTest.plan(), f.jobs);
            assertEquals(List.of(EXHAUSTIVE, SOCIAL_SECURITY, WEIGHTED),
                    view.navigation.choices().stream().map(AnalyzerNavigationView.Choice::mode).toList());
            assertEquals(EXHAUSTIVE, view.navigation.selected().mode());
            view.ssView.discount.setText("bad pending value");
            view.weightedView.discount.setText("2.75");
            for (var choice : view.navigation.choices()) view.navigation.select(choice.mode());
            assertEquals("bad pending value", view.ssView.discount.getText());
            assertEquals("2.75", view.weightedView.discount.getText());
            assertSame(view.weightedView, view.navigation.selected().workspace());
            assertTrue(view.navigation.choices().stream().noneMatch(choice -> choice.mode() == QUICK));
            assertEquals(0, f.jobs.generation());
        });
    }

    @Test void selectorFocusAndArrowKeysRemainReachableAcrossResize() throws Exception {
        var create = new FutureTask<Fixture>(() -> {
            var fixture = new Fixture();
            fixture.stage.show();
            layout(fixture.stage, 1600, 900);
            fixture.navigation.focusSelector();
            return fixture;
        });
        Platform.runLater(create);
        var fixture = create.get(30, TimeUnit.SECONDS);
        try {
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            var wide = new FutureTask<Void>(() -> {
                assertInstanceOf(ToggleButton.class, fixture.stage.getScene().getFocusOwner());
                fixture.stage.getScene().getFocusOwner().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
                assertEquals(QUICK, fixture.navigation.selected().mode());
                layout(fixture.stage, 800, 600);
                return null;
            });
            Platform.runLater(wide); wide.get(30, TimeUnit.SECONDS);
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            var narrow = new FutureTask<Void>(() -> {
                fixture.navigation.focusSelector();
                assertSame(field(fixture.navigation, "compact", ComboBox.class), fixture.stage.getScene().getFocusOwner());
                assertNull(fixture.navigation.getLeft());
                assertEquals(0, fixture.jobs.generation());
                return null;
            });
            Platform.runLater(narrow); narrow.get(30, TimeUnit.SECONDS);
        } finally {
            var close = new FutureTask<Void>(() -> { fixture.close(); return null; });
            Platform.runLater(close); close.get(30, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @CsvSource({"1920,1080", "1366,768", "1180,820", "900,650", "800,600"})
    @EnabledIfSystemProperty(named = "analyzer.navigation.preview", matches = "true")
    void renderedNavigationPreviews(int width, int height) throws Exception {
        var create = new FutureTask<Fixture>(Fixture::new);
        Platform.runLater(create);
        var fixture = create.get(30, TimeUnit.SECONDS);
        try {
            apply(fixture, f -> {
                f.stage.setMinWidth(0); f.stage.setMinHeight(0);
                f.stage.setWidth(width); f.stage.setHeight(height); f.stage.show();
                field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class)
                        .render(ClaimingStrategyHeatMapViewTest.fixture(), "Current elections");
            });
            for (var mode : List.of(SOCIAL_SECURITY, QUICK, EXHAUSTIVE, WEIGHTED)) {
                apply(fixture, f -> { f.navigation.select(mode); layout(f.stage); });
                com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                apply(fixture, f -> {
                    assertSame(f.navigation.selected().workspace(), field(f.navigation, "workspace", javafx.scene.layout.VBox.class).getChildren().getFirst());
                    write(f.navigation, "couple-" + mode + "-" + width + "x" + height);
                });
            }
            if (Boolean.getBoolean("analyzer.context.preview")) {
                apply(fixture, f -> {
                    f.navigation.select(SOCIAL_SECURITY);
                    field(f.dialog, "inputSection", TitledPane.class).setExpanded(true);
                    layout(f.stage);
                });
                com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                apply(fixture, f -> {
                    write(f.navigation, "couple-reference-expanded-" + width + "x" + height);
                    field(f.dialog, "inputSection", TitledPane.class).setExpanded(false);
                    field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("unfinished");
                    f.navigation.select(WEIGHTED);
                    layout(f.stage);
                });
                com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                apply(fixture, f -> {
                    assertEquals("unfinished", field(f.dialog, "primaryMortalityAdjustment", TextField.class).getText());
                    write(f.navigation, "couple-pending-invalid-" + width + "x" + height);
                    field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("1.00");
                });
            }
            apply(fixture, f -> f.navigation.select(WEIGHTED));
            apply(fixture, f -> {
                var weighted = field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class);
                weighted.resultTabs.getSelectionModel().select(weighted.heatMapTab);
                layout(f.stage);
            });
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            apply(fixture, f -> IntegratedAnalysisResultTabs.reveal(
                    field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class).heatMap));
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            apply(fixture, f -> write(f.navigation, "couple-heat-map-" + width + "x" + height));
            apply(fixture, f -> {
                f.jobs.start(WEIGHTED, (p, c) -> "unused", result -> fail("Cancelled preview published"), failure -> fail(failure));
                f.navigation.select(SOCIAL_SECURITY);
            });
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            apply(fixture, f -> {
                write(f.navigation, "couple-active-job-" + width + "x" + height);
                f.jobs.cancel(); f.executor.run();
            });
            var single = new SinglePersonIntegratedView[1];
            var singleStage = new Stage[1];
            apply(fixture, f -> {
                var plan = SinglePersonDeterministicStrategyTest.plan();
                plan.getHousehold().getPrimaryPerson().setMortalityCategory(
                        com.daviddunn.retirementplanner.domain.model.MortalityCategory.FEMALE);
                single[0] = new SinglePersonIntegratedView(plan, f.jobs);
                singleStage[0] = new Stage();
                singleStage[0].setScene(new Scene(single[0], width, height));
                singleStage[0].show();
            });
            try {
                for (var mode : List.of(EXHAUSTIVE, SOCIAL_SECURITY, WEIGHTED)) {
                    apply(fixture, f -> { single[0].navigation.select(mode); layout(singleStage[0]); });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    apply(fixture, f -> write(single[0], "single-" + mode + "-" + width + "x" + height));
                }
            } finally { apply(fixture, f -> singleStage[0].close()); }
        } finally { apply(fixture, Fixture::close); }
    }

    private static void layout(Stage stage) { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); }
    private static void layout(Stage stage, int width, int height) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().resize(width, height);
        stage.getScene().getRoot().layout();
    }
    private static void write(Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of(System.getProperty("analyzer.preview.output", "target/phase8b-navigation-preview")); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }

    private static class Fixture implements AutoCloseable {
        final SocialSecurityAnalyzerJobControllerTest.ManualExecutor executor = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
        final SocialSecurityAnalysisJobCoordinator coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
        final SocialSecurityAnalyzerJobController jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
        final SocialSecurityStrategyAnalyzerDialog dialog = new SocialSecurityStrategyAnalyzerDialog(null,
                LongevityWeightedAnalysisRequestFactoryTest.plan(), jobs);
        final Stage stage = field(dialog, "stage", Stage.class);
        final AnalyzerNavigationView navigation = field(dialog, "navigation", AnalyzerNavigationView.class);
        Fixture() throws Exception { }
        @Override public void close() throws Exception { stage.close(); invoke(dialog, "close"); coordinator.close(); }
    }
    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
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
    private static void apply(Fixture fixture, Check check) throws Exception {
        var task = new FutureTask<Void>(() -> { check.run(fixture); return null; });
        Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
    @FunctionalInterface private interface Check { void run(Fixture fixture) throws Exception; }
}
