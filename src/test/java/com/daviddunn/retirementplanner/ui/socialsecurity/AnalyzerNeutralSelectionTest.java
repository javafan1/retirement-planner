package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.introduction.AnalyzerIntroductionDialog;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.AnalyzerIntroductionNavigationTest.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityAnalyzerJobController.Mode.*;

class AnalyzerNeutralSelectionTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }

    @ParameterizedTest @CsvSource({"true,LIGHT,1180,820", "true,DARK,900,650", "false,LIGHT,900,650", "false,DARK,1180,820"})
    void neutralSelectorsCancelKeyboardAndRealPreviews(boolean couple, ApplicationTheme theme, int width, int height) throws Exception {
        withPlan(couple, false, (controller, dialog, stage, navigation) -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                stage.setMinWidth(0); stage.setMinHeight(0); stage.setWidth(width); stage.setHeight(height);
                var root = stage.getScene().getRoot();
                stage.getScene().setRoot(new Group());
                stage.setScene(new Scene(root, width, height));
                stage.sizeToScene();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                assertEquals(width, stage.getScene().getWidth());
                assertEquals(height, stage.getScene().getHeight());
                assertNull(navigation.selected());
                assertNotNull(stage.getScene().lookup("#analyzer-selection-instruction"));
                assertEquals(couple ? 4 : 3, navigation.choices().size());
                assertEquals(0, Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage).count());
                var compact = field(navigation, "compact", ComboBox.class);
                var buttons = (Map<?, ?>) field(navigation, "buttons", Map.class);
                assertNull(compact.getValue());
                assertTrue(buttons.values().stream().allMatch(button -> !((ToggleButton) button).isSelected()));
                var image = stage.getScene().snapshot(null);
                var output = Path.of("target/phase13i/previews"); Files.createDirectories(output);
                var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y=0; y<pixels.getHeight(); y++) for (int x=0; x<pixels.getWidth(); x++) pixels.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(pixels, "png", output.resolve("neutral-" + couple + "-" + theme + "-" + width + ".png").toFile());

                for (var choice : navigation.choices()) {
                    ((ToggleButton) buttons.get(choice.mode())).fire();
                    var pending = intro(stage);
                    navigation.requestSelect(choice.mode());
                    assertEquals(1, Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage).count());
                    assertNull(navigation.selected()); assertNull(compact.getValue());
                    assertTrue(buttons.values().stream().allMatch(button -> !((ToggleButton) button).isSelected()));
                    ((Button) pending.lookupButton(ButtonType.CANCEL)).fire();
                    assertNull(navigation.selected());
                }
                compact.setValue(navigation.choices().getLast());
                var pending = intro(stage);
                assertNull(compact.getValue());
                pending.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                assertNull(navigation.selected()); assertNull(compact.getValue());
                var first = (ToggleButton) buttons.get(navigation.choices().getFirst().mode());
                first.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.END, false, false, false, false));
                ((Button) intro(stage).lookupButton(AnalyzerIntroductionDialog.CONTINUE)).fire();
                assertSame(navigation.choices().getLast(), navigation.selected());
                assertSame(navigation.selected(), compact.getValue());
                assertTrue(((ToggleButton) buttons.get(navigation.selected().mode())).isSelected());
                var previous = navigation.selected();
                first.fire(); ((Stage) intro(stage).getScene().getWindow()).close();
                assertSame(previous, navigation.selected()); assertSame(previous, compact.getValue());
                assertTrue(((ToggleButton) buttons.get(previous.mode())).isSelected());
                navigation.select(navigation.choices().getFirst().mode());
                assertSame(navigation.choices().getFirst(), navigation.selected());
                assertEquals(0, Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage).count());
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void suppressedIntroductionsOpenOnlyExplicitChoicesWithoutWork(boolean couple) throws Exception {
        withPlan(couple, true, (controller, dialog, stage, navigation) -> {
            assertNull(navigation.selected());
            for (var choice : navigation.choices()) {
                navigation.requestSelect(choice.mode());
                assertSame(choice, navigation.selected());
                assertEquals(0, Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getOwner() == stage).count());
            }
            if (couple) { navigation.select(QUICK); assertTrue(field(dialog, "integratedRunButton", Button.class).isDisabled()); }
            assertTrue(controller.getSettingsService().snapshot().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));
        });
    }

    private void withPlan(boolean couple, boolean suppressed, Check check) throws Exception {
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        controller.newPlan(BaselineDetailsTest.plan(couple)); controller.getCurrentProjection();
        if (suppressed) for (var entry : List.of(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY, AnalyzerIntroduction.SOCIAL_SECURITY_QUICK,
                AnalyzerIntroduction.SOCIAL_SECURITY_EXHAUSTIVE, AnalyzerIntroduction.SOCIAL_SECURITY_WEIGHTED)) {
            controller.getSettingsService().suppressIntroduction(entry).toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        var input = CompleteProjectionSnapshot.freeze(controller.getCurrentPlan());
        var snapshot = controller.getAcceptedProjectionSnapshot();
        long revision = controller.getSourcePlanRevision(); boolean dirty = controller.isModified();
        var engine = controller.getClass().getDeclaredField("projectionEngine"); engine.setAccessible(true);
        engine.set(controller, new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine() {
            @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) { throw new AssertionError("Navigation must not calculate"); }
            @Override public com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) { throw new AssertionError("Navigation must not calculate"); }
        });
        try {
            fx(() -> {
                var executor = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
                var coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
                var jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
                var owner = new Stage(); owner.setScene(new Scene(new VBox(), 1180, 820)); owner.show();
                var dialog = new SocialSecurityStrategyAnalyzerDialog(owner, controller, jobs); dialog.configureIntroductions();
                var stage = field(dialog,"stage",Stage.class); stage.show();
                var navigation = couple ? field(dialog,"navigation",AnalyzerNavigationView.class)
                        : field(field(dialog,"singlePersonView",SinglePersonIntegratedView.class),"navigation",AnalyzerNavigationView.class);
                try {
                    check.run(controller, dialog, stage, navigation);
                    assertEquals(0, jobs.generation()); assertTrue(executor.queue.isEmpty());
                } finally { stage.close(); owner.hide(); coordinator.close(); }
                return null;
            });
            assertEquals(input, CompleteProjectionSnapshot.freeze(controller.getCurrentPlan()));
            assertSame(snapshot, controller.getAcceptedProjectionSnapshot()); assertEquals(revision, controller.getSourcePlanRevision()); assertEquals(dirty, controller.isModified());
        } finally { controller.getAnalysisParameters().shutdown(); controller.getAnalysisResults().shutdownIfIdle(); controller.getSettingsService().close(); }
    }

    @FunctionalInterface private interface Check {
        void run(ApplicationController controller, SocialSecurityStrategyAnalyzerDialog dialog, Stage stage, AnalyzerNavigationView navigation) throws Exception;
    }
}
