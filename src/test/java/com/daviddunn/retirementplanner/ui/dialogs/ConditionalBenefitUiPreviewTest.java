package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.IncomeSourcesView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitUiPreviewTest {
    private static final Path OUTPUT = Path.of("target", "phase22b4", "previews");
    @BeforeAll static void initialize() throws Exception { startFx(); Files.createDirectories(OUTPUT); }

    @Test void actualMonthlyAndLumpDialogsWrapAndKeepSaveCancelVisibleInBothThemesAndSizes() throws Exception {
        for (var theme : ApplicationTheme.values()) for (int width : new int[]{900, 1180}) {
            int height = width == 900 ? 650 : 820;
            var themeManager = fx(() -> ApplicationThemeManager.start(theme));
            try {
                for (var pattern : PaymentPattern.values()) {
                    var dialog = fx(() -> {
                        var result = new ConditionalBenefitDialog(plan(true), null);
                        result.getDialogPane().setPrefSize(width, height);
                        result.show();
                        configure(result, TriggerType.DEATH, pattern);
                        input(result, "name").setText("Survivor benefit with a long descriptive name for household planning, original assumptions and compact-window inspection");
                        choice(result, "category").setValue(Category.DEATH_BENEFIT);
                        date(result, "coverage-start").setValue(java.time.LocalDate.of(2030, 1, 1));
                        date(result, "coverage-end").setValue(java.time.LocalDate.of(2040, 12, 31));
                        ((Stage) result.getDialogPane().getScene().getWindow()).sizeToScene();
                        return result;
                    });
                    try {
                        pulses(fx(() -> dialog.getDialogPane().getScene()));
                        fx(() -> {
                            var pane = dialog.getDialogPane();
                            assertEquals(width, pane.getScene().getWidth(), 1);
                            assertEquals(height, pane.getScene().getHeight(), 1);
                            assertInside(pane.lookupButton(ConditionalBenefitDialog.SAVE), pane.getScene());
                            assertInside(pane.lookupButton(ButtonType.CANCEL), pane.getScene());
                            assertTrue(((ScrollPane) pane.lookup("#cb-form-scroll")).getContent().getLayoutBounds().getHeight()
                                    > ((ScrollPane) pane.lookup("#cb-form-scroll")).getViewportBounds().getHeight());
                            image(pane, "editor-" + pattern + "-" + theme + "-" + width + "-top.png");
                            ((ScrollPane) pane.lookup("#cb-form-scroll")).setVvalue(1);
                            return null;
                        });
                        pulses(fx(() -> dialog.getDialogPane().getScene()));
                        fx(() -> {
                            image(dialog.getDialogPane(), "editor-" + pattern + "-" + theme + "-" + width + "-treatment.png");
                            input(dialog, "amount").setText("invalid amount");
                            ((Button) dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                            return null;
                        });
                        pulses(fx(() -> dialog.getDialogPane().getScene()));
                        fx(() -> {
                            var pane = dialog.getDialogPane();
                            var status = (Label) pane.lookup("#cb-validation-status");
                            assertTrue(status.getText().contains("not saved"));
                            assertInside(status, pane.getScene());
                            assertInside(input(dialog, "amount"), pane.getScene());
                            assertInside(pane.lookupButton(ConditionalBenefitDialog.SAVE), pane.getScene());
                            image(pane, "editor-" + pattern + "-" + theme + "-" + width + "-validation.png");
                            return null;
                        });
                    } finally { fx(() -> { dialog.close(); return null; }); }
                }
            } finally { fx(() -> { themeManager.close(); return null; }); }
        }
    }

    @Test void actualIncomeWorkspacePreservesExistingInputsAndShowsCompleteSelectedDefinition() throws Exception {
        var plan = plan(true);
        var monthly = create(plan, TriggerType.DEATH, PaymentPattern.MONTHLY_RECURRING);
        var lump = create(plan, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
        plan.setConditionalBenefits(List.of(BenefitFixtures.change(monthly, "label", "Survivor Income — original monthly payment"),
                BenefitFixtures.change(lump, "label", "Survivor Lump Sum — future receipt, not a current account balance")));
        for (var theme : ApplicationTheme.values()) for (int width : new int[]{900, 1180}) {
            int height = width == 900 ? 650 : 820;
            var manager = fx(() -> ApplicationThemeManager.start(theme));
            var stage = fx(() -> {
                var view = new IncomeSourcesView(); view.load(plan);
                var value = new Stage(); value.setScene(new Scene(view, width, height)); value.show(); return value;
            });
            try {
                pulses(fx(stage::getScene));
                fx(() -> {
                    var view = (IncomeSourcesView) stage.getScene().getRoot();
                    assertEquals(2, view.getTable().getItems().size());
                    image(view, "income-" + theme + "-" + width + "-list.png");
                    @SuppressWarnings("unchecked") var table = (TableView<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition>)
                            view.lookup("#conditional-benefits-table");
                    table.getSelectionModel().selectLast();
                    ((TitledPane) view.lookup("#conditional-benefit-details")).setExpanded(true);
                    return null;
                });
                pulses(fx(stage::getScene));
                fx(() -> {
                    ((ScrollPane) stage.getScene().getRoot().lookup("#income-sources-scroll")).setVvalue(1);
                    return null;
                });
                pulses(fx(stage::getScene));
                fx(() -> {
                    var view = stage.getScene().getRoot();
                    var details = (TitledPane) view.lookup("#conditional-benefit-details");
                    assertTrue(((Label) details.getContent()).getText().contains("$400,000.00 one-time receipt"));
                    assertTrue(((Label) details.getContent()).getText().contains("not existing account balances"));
                    image(view, "income-" + theme + "-" + width + "-details.png");
                    return null;
                });
            } finally { fx(() -> { stage.close(); manager.close(); return null; }); }
        }
    }

    private static void assertInside(Node node, Scene scene) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(node.isVisible());
        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= scene.getHeight() + 1, bounds.toString());
        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= scene.getWidth() + 1, bounds.toString());
    }

    @Test void unsupportedStateWarningRemainsVisibleAboveTheScrollableForm() throws Exception {
        var plan = plan(true);
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(),
                a.getProjectionLengthYears(), a.getProjectionStartDate(), com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.simplified(
                        com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.PA)));
        var dialog = fx(() -> {
            var result = new ConditionalBenefitDialog(plan, null); result.getDialogPane().setPrefSize(900, 650);
            result.show(); return result;
        });
        try {
            pulses(fx(() -> dialog.getDialogPane().getScene()));
            fx(() -> {
                var pane = dialog.getDialogPane();
                ((ScrollPane) pane.lookup("#cb-form-scroll")).setVvalue(1);
                assertInside(pane.lookup("#cb-state-warning"), pane.getScene());
                assertInside(pane.lookupButton(ConditionalBenefitDialog.SAVE), pane.getScene());
                image(pane, "unsupported-state-warning-900.png");
                return null;
            });
        } finally { fx(() -> { dialog.close(); return null; }); }
    }

    private static void image(Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var buffer = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++)
            buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffer, "png", OUTPUT.resolve(name).toFile());
    }

    private static void pulses(Scene scene) throws Exception {
        var complete = new CompletableFuture<Void>();
        fx(() -> {
            scene.addPostLayoutPulseListener(new Runnable() {
                private int remaining = 3;
                @Override public void run() {
                    if (--remaining == 0) { scene.removePostLayoutPulseListener(this); complete.complete(null); }
                    else Platform.requestNextPulse();
                }
            });
            Platform.requestNextPulse(); return null;
        });
        complete.get(15, TimeUnit.SECONDS);
    }
}
