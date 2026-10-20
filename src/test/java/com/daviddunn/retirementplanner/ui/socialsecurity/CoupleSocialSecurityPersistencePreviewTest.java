package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "couple.ss.persistence.preview", matches = "true")
class CoupleSocialSecurityPersistencePreviewTest {
    @TempDir Path dir;
    @BeforeAll static void prepare() throws Exception { CoupleSocialSecurityPersistenceTest.prepare(); }
    @Test void currentAndOutdatedResultsRemainReadable() throws Exception {
        var support = new CoupleSocialSecurityPersistenceTest(); support.dir = dir;
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var source = support.controller(store, "settings"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var completion = CoupleSocialSecurityPersistenceTest.completion(source); store.accept(completion); settle(store, completion.planId(), completion.kind());
        try {
            for (int[] size : new int[][] {{1366,768}, {900,650}}) {
                try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
                    await(view.persistence().notice, "Current Results");
                    var stage = fx(() -> {
                        var shown = field(view.dialog(), "stage", Stage.class);
                        shown.setWidth(size[0]); shown.setHeight(size[1]); shown.show();
                        shown.getScene().getRoot().applyCss(); shown.getScene().getRoot().layout(); return shown;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        IntegratedAnalysisResultTabs.reveal(view.persistence().controls);
                        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        assertTrue(view.persistence().notice.getWidth() > 100);
                        return null;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        WeightedRestorationPreviewTest.write(stage.getScene().getRoot(), "9d1-ss-current-" + size[0]);
                        field(view.dialog(), "discountRate", TextField.class).setText("2.5"); return null;
                    });
                    await(view.persistence().notice, "Outdated");
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        IntegratedAnalysisResultTabs.reveal(view.persistence().controls);
                        stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                        return null;
                    });
                    com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.pulses();
                    fx(() -> {
                        assertTrue(view.persistence().notice.getText().contains("Outdated"));
                        WeightedRestorationPreviewTest.write(view.persistence().notice, "9d1-ss-outdated-status-" + size[0]);
                        WeightedRestorationPreviewTest.write(stage.getScene().getRoot(), "9d1-ss-outdated-" + size[0]);
                        assertFalse(field(view.dialog(), "strategies", javafx.scene.control.TableView.class).getItems().isEmpty());
                        return null;
                    });
                }
            }
        } finally { stop(store); }
    }
}
