package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "weighted.persistence.preview", matches = "true")
class WeightedRestorationPreviewTest {
    @TempDir Path dir;
    @Test void renderedSaveFailureCategoryAndRetryRemainReachable() throws Exception {
        var support = new WeightedRestorationTest(); support.dir = dir;
        var repository = new AnalysisResultStoreTest.Memory();
        var store = new AnalysisResultStore(repository);
        var source = support.controller(store, "save-failure");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("failure.json"));
        var completion = coupleCompletion(source); store.accept(completion); settle(store, completion.planId(), completion.kind());
        try (var session = coupleView(source)) {
            await(session.persistence().notice, "Current Results");
            repository.fail = true;
            fx(() -> { session.persistence().accept(coupleCompletion(source)); return null; });
            await(session.persistence().notice, "Failure category: transactional file writing");
            for (int[] size : new int[][] {{1366, 768}, {900, 650}}) {
                var stage = fx(() -> {
                    var shown = field(session.dialog(), "stage", Stage.class);
                    shown.setWidth(size[0]); shown.setHeight(size[1]); shown.show(); layout(shown); return shown;
                });
                com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                fx(() -> { IntegratedAnalysisResultTabs.reveal(session.persistence().controls); layout(stage); return null; });
                com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                fx(() -> {
                    assertTrue(session.persistence().retry.isVisible());
                    assertEquals(session.persistence().notice.getText(), session.persistence().notice.getAccessibleText());
                    write(session.persistence().controls, "hotfix-save-failure-status-" + size[0] + "x" + size[1]);
                    write(stage.getScene().getRoot(), "hotfix-save-failure-" + size[0] + "x" + size[1]);
                    return null;
                });
            }
        } finally { repository.fail = false; store.retryFailed(); stop(store); }
    }
    @BeforeAll static void start() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }
    @Test void renderedRestoredCoupleStatesAtFourLogicalBudgets() throws Exception {
        var support = new WeightedRestorationTest(); support.dir = dir;
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var source = support.controller(store, "preview"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var completion = coupleCompletion(source); store.accept(completion); settle(store, completion.planId(), completion.kind());
        try {
            for (var size : new int[][] {{1920,1080}, {1366,768}, {1180,820}, {900,650}}) {
                try (var session = coupleView(source)) {
                    await(session.persistence().notice, "Current Results");
                    var stage = fx(() -> field(session.dialog(), "stage", Stage.class));
                    fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); stage.show(); layout(stage); return null; });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        IntegratedAnalysisResultTabs.reveal(session.persistence().controls); layout(stage);
                        assertTrue(session.persistence().notice.getWidth() > 100);
                        assertTrue(session.persistence().notice.getWidth() < stage.getWidth());
                        write(stage.getScene().getRoot(), "couple-current-" + size[0] + "x" + size[1]);
                        field(session.dialog(), "discountRate", TextField.class).setText("2.5");
                        assertTrue(session.persistence().notice.getText().contains("Outdated"));
                        layout(stage); IntegratedAnalysisResultTabs.reveal(session.persistence().controls);
                        return null;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        assertTrue(session.persistence().notice.getText().contains("Outdated"), session.persistence().notice.getText());
                        assertTrue(((javafx.scene.text.Text) session.persistence().notice.lookup(".text")).getText().contains("Outdated"));
                        write(session.persistence().notice, "couple-outdated-status-" + size[0] + "x" + size[1]);
                        write(stage.getScene().getRoot(), "couple-outdated-" + size[0] + "x" + size[1]);
                        session.view().resultTabs.getSelectionModel().select(session.view().heatMapTab); return null;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        IntegratedAnalysisResultTabs.reveal(session.view().heatMap); layout(stage);
                        write(stage.getScene().getRoot(), "couple-heat-map-outdated-" + size[0] + "x" + size[1]);
                        return null;
                    });
                }
            }
        } finally { stop(store); }
    }
    static void layout(Stage stage) { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); }
    @Test void restoredOutdatedInitialFrameAtNarrowBudget() throws Exception {
        var support = new WeightedRestorationTest(); support.dir = dir;
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var source = support.controller(store, "outdated"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("outdated.json"));
        var completion = coupleCompletion(source); store.accept(completion); settle(store, completion.planId(), completion.kind());
        source.getCurrentPlan().getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Changed spending", new java.math.BigDecimal("123.4500")));
        source.markModified();
        try (var session = coupleView(source)) {
            await(session.persistence().notice, "Outdated");
            var stage = fx(() -> {
                var s = field(session.dialog(), "stage", Stage.class); s.setWidth(900); s.setHeight(650); s.show(); layout(s);
                return s;
            });
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            fx(() -> { IntegratedAnalysisResultTabs.reveal(session.persistence().controls); return null; });
            com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
            fx(() -> {
                assertTrue(session.persistence().notice.getText().contains("Outdated"));
                write(stage.getScene().getRoot(), "couple-restored-initial-outdated-900x650");
                return null;
            });
        } finally { stop(store); }
    }
    @Test void renderedRestoredIndividualStatesAtFourLogicalBudgets() throws Exception {
        var support = new WeightedRestorationTest(); support.dir = dir;
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var source = support.controller(store, "individual"); source.newPlan(SinglePersonMortalityAnalysisTest.plan()); source.saveAs(dir.resolve("individual.json"));
        var plan = source.getCurrentPlan();
        var result = SinglePersonMortalityAnalysisTest.run(plan, SinglePersonMortalityAnalysisTest.mortality(plan, "1.00"),
                "0.010", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        var completion = individualCompletion(source, result); store.accept(completion); settle(store, completion.planId(), completion.kind());
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        try {
            for (var size : new int[][] {{1920,1080}, {1366,768}, {1180,820}, {900,650}}) {
                var dialog = fx(() -> {
                    var d = new SocialSecurityStrategyAnalyzerDialog(null, source, new SocialSecurityAnalyzerJobController(coordinator));
                    var v = field(d, "singlePersonView", SinglePersonIntegratedView.class);
                    v.navigation.select(SocialSecurityAnalyzerJobController.Mode.WEIGHTED);
                    var stage = field(d, "stage", Stage.class); stage.setWidth(size[0]); stage.setHeight(size[1]); stage.show(); layout(stage);
                    return d;
                });
                try {
                    var view = fx(() -> field(dialog, "singlePersonView", SinglePersonIntegratedView.class));
                    await(view.weightedView.persistence.notice, "Current Results");
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        var stage = field(dialog, "stage", Stage.class);
                        IntegratedAnalysisResultTabs.reveal(view.weightedView.persistence.controls); layout(stage);
                        write(stage.getScene().getRoot(), "individual-current-" + size[0] + "x" + size[1]);
                        view.weightedView.factor.setText("0.85"); layout(stage);
                        IntegratedAnalysisResultTabs.reveal(view.weightedView.persistence.controls); return null;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        var stage = field(dialog, "stage", Stage.class);
                        assertTrue(view.weightedView.persistence.notice.getText().contains("Outdated"), view.weightedView.persistence.notice.getText());
                        write(view.weightedView.persistence.notice, "individual-outdated-status-" + size[0] + "x" + size[1]);
                        write(stage.getScene().getRoot(), "individual-outdated-" + size[0] + "x" + size[1]);
                        return null;
                    });
                } finally {
                    fx(() -> { field(dialog, "stage", Stage.class).hide(); return null; });
                }
            }
        } finally { coordinator.close(); stop(store); }
    }
    static void write(Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        writeImage(image, name);
    }
    static void writeImage(javafx.scene.image.Image image, String name) throws Exception {
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/phase9c-restoration-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }
}
