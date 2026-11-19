package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.IncomeSourcesView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.TAXABLE_FIRST;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.single;
import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.validationEngine;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasWorkflowUiTest {
    @BeforeAll static void startFx() throws Exception { PlanFileOperationsTest.startFx(); }

    @Test void reviewAndProgressUseExistingDialogsInLightAndDark() throws Exception {
        var opening = LocalDate.of(2026, 1, 1);
        var plan = single(LocalDate.of(1965, 7, 1), opening, 1, TAXABLE_FIRST, "500000");
        var pension = new Pension("Company pension", PRIMARY, opening, null, BigDecimal.valueOf(5000), BigDecimal.ZERO);
        pension.setPlanClassification(PensionPlanClassification.DEFINED_BENEFIT_414J);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(plan));
        var review = failure.pensionsToReview().getFirst();
        for (var theme : new ApplicationTheme[]{ApplicationTheme.LIGHT, ApplicationTheme.DARK}) {
            PlanFileOperationsTest.fx(() -> {
                try (var themes = ApplicationThemeManager.start(theme)) {
                    var income = new IncomeSourcesView();
                    income.load(plan);
                    var owner = new Stage();
                    owner.setScene(new Scene(income, 850, 680));
                    owner.show();
                    try {
                        String scale = System.getProperty("glass.win.uiScale");
                        if (scale != null) assertEquals(Double.parseDouble(scale), owner.getOutputScaleX(), .01);
                        var warning = new ArkansasProjectionWarning(owner, failure, failure.pensionsToReview());
                        warning.dialog().show();
                        try {
                            var pane = warning.dialog().getDialogPane();
                            pane.applyCss();
                            pane.layout();
                            assertEquals("State Tax Unavailable", warning.dialog().getTitle());
                            var text = (Label) ((javafx.scene.layout.VBox) pane.getContent()).getChildren().getFirst();
                            assertTrue(text.getText().contains("2026"));
                            assertTrue(text.getText().contains("original retirement income source"));
                            assertTrue(text.getHeight() + 1 >= text.prefHeight(text.getWidth()));
                            var button = warning.dialog().getButtonTypes().stream().filter(b -> "Review Pensions".equals(b.getText()))
                                    .findFirst().orElseThrow();
                            assertEquals(review, warning.selectedReview(button).orElseThrow());
                            assertTrue(warning.selectedReview(ButtonType.CLOSE).isEmpty());
                            capture(pane, "warning-" + theme + "-" + scale);
                        } finally {
                            warning.dialog().close();
                        }
                        var feedback = new PlanOperationFeedback(owner, "Calculating Arkansas projection");
                        try {
                            var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
                            feedback.phase("Year 1 complete. Calculating Arkansas projection.", () -> cancelled.set(true));
                            feedback.showSubtle();
                            capture(feedback.view(), "progress-" + theme + "-" + scale);
                            ((Button) feedback.view().lookup("#plan-operation-cancel")).fire();
                            assertTrue(cancelled.get());
                        } finally {
                            feedback.close();
                        }

                        income.selectPension(ArkansasPensionPreflight.resolve(plan.getHousehold(), review).orElseThrow());
                        setOriginInOpenEditor(income, pension, ButtonType.CANCEL);
                        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, pension.getSourceOrigin());
                        if (theme == ApplicationTheme.DARK) {
                            setOriginInOpenEditor(income, pension, ButtonType.OK);
                            var saved = (Pension) plan.getHousehold().getPrimaryPerson().getIncomeSources().getLast();
                            assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN, saved.getSourceOrigin());
                            assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J, saved.getPlanClassification());
                        }
                    } finally {
                        owner.hide();
                    }
                }
                return null;
            });
        }
        assertDoesNotThrow(() -> validationEngine().project(plan));
    }

    private static void setOriginInOpenEditor(IncomeSourcesView income, Pension pension, ButtonType button) {
        var caught = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            var pane = Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                    .filter(java.util.Objects::nonNull).map(Scene::getRoot).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).filter(p -> p.lookup("#pension-source-origin") != null).findFirst().orElseThrow();
            try {
                @SuppressWarnings("unchecked")
                var source = (ComboBox<RetirementIncomeSourceOrigin>) pane.lookup("#pension-source-origin");
                assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, source.getValue());
                source.setValue(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            } catch (Throwable failure) {
                caught.set(failure);
            } finally {
                ((Button) pane.lookupButton(button)).fire();
            }
        });
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        income.editSelectedPension(pension, () -> accepted.set(true));
        assertEquals(button == ButtonType.OK, accepted.get());
        assertNull(caught.get());
    }

    private static void capture(javafx.scene.Node node, String name) throws Exception {
        var folder = Files.createDirectories(Path.of("target/phase6e-p3c3-previews"));
        var snapshot = node.snapshot(null, null);
        int width = (int) snapshot.getWidth();
        int height = (int) snapshot.getHeight();
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }
}
