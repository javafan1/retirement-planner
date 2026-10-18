package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "state.tax.preview", matches = "true")
class IowaIntegratedVisualTest {
    @Test
    void rendersActualIowaIntegratedComparison() throws Exception {
        NewPlanWizardTestSupport.startFx();
        var plan = com.daviddunn.retirementplanner.app.montecarlo.IowaMonteCarloIntegrationTest.plan(StateTaxJurisdiction.IA);
        var strategy = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        var comparison = new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of(strategy));
        assertTrue(comparison.entries().stream().allMatch(entry -> entry.successful()));
        var presentation = new IntegratedSocialSecurityComparisonPresentation(comparison, comparison.entries().stream()
                .map(entry -> new IntegratedSocialSecurityComparisonPresentation.Row(1, entry)).toList());
        NewPlanWizardTestSupport.fx(() -> {
            try (var coordinator = new SocialSecurityAnalysisJobCoordinator();
                 var jobs = new SocialSecurityAnalyzerJobController(coordinator)) {
                var dialog = new SocialSecurityStrategyAnalyzerDialog(null, plan, jobs);
                var presentationField = dialog.getClass().getDeclaredField("integratedPresentation");
                presentationField.setAccessible(true); presentationField.set(dialog, presentation);
                var method = dialog.getClass().getDeclaredMethod("renderIntegrated", IntegratedSocialSecurityComparisonPresentation.class);
                method.setAccessible(true); method.invoke(dialog, presentation);
                var field = dialog.getClass().getDeclaredField("stage"); field.setAccessible(true);
                var stage = (Stage) field.get(dialog); stage.setWidth(1500); stage.setHeight(1000); stage.show();
                try {
                    var root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                    ((AnalyzerNavigationView) root).select(SocialSecurityAnalyzerJobController.Mode.QUICK);
                    for (var node : root.lookupAll(".tab-pane")) if (node instanceof TabPane tabs) {
                        for (var tab : tabs.getTabs()) if (List.of("Integrated Retirement Plan", "Deterministic", "Quick Comparison").contains(tab.getText()))
                            tabs.getSelectionModel().select(tab);
                    }
                    root.applyCss(); root.layout();
                    var image = root.snapshot(null, null);
                    var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    Files.createDirectories(Path.of("target/phase6d5b-preview"));
                    javax.imageio.ImageIO.write(output, "png", Path.of("target/phase6d5b-preview/integrated-Iowa.png").toFile());
                    for (var node : root.lookupAll(".titled-pane")) if (node instanceof javafx.scene.control.TitledPane pane
                            && pane.getText().equals("Analysis Inputs and Plan Assumptions")) {
                        pane.setAnimated(false); pane.setExpanded(true);
                    }
                    root.applyCss(); root.layout();
                    assertTrue(root.lookupAll(".label").stream().filter(javafx.scene.control.Label.class::isInstance)
                            .map(javafx.scene.control.Label.class::cast).anyMatch(label -> label.getText().contains("not assumed to be zero")));
                    var summaryField = dialog.getClass().getDeclaredField("inputSummary"); summaryField.setAccessible(true);
                    image = ((javafx.scene.Parent) summaryField.get(dialog)).snapshot(null, null);
                    output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(output, "png", Path.of("target/phase6d5b-preview/integrated-Iowa-assumptions.png").toFile());
                    var context = IntegratedReportContext.capture(plan, false,
                            new SocialSecurityAnalyzerInputSummary.AnalyzerValues(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE,
                                    plan.getPlanningAssumptions().getProjectionStartDate(), plan.getPlanningAssumptions().getProjectionStartDate(), java.math.BigDecimal.ZERO),
                            CurrentStrategyBaseline.fromPlan(plan));
                    assertTrue(context.assumptions().toString().contains("not assumed to be zero"));
                    assertTrue(context.assumptions().toString().contains("nominal 2026"));
                } finally { stage.close(); }
            }
        });
    }
}
