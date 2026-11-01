package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.dialogs.AccountDialog;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RothAvailabilityUiTest {
    @BeforeAll static void init() throws Exception { NewPlanWizardTestSupport.startFx(); }

    @Test void bothSurfacesGateNewEnablementAndPreserveSavedDisable() throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            var tab = new RothConversionView(); tab.load(plan);
            var summary = new ResultsSummaryView(new ApplicationController()); summary.load(plan, null, List.of());
            CheckBox enabled = field(tab, "enabledCheckBox");
            CheckBox otherEnabled = field(summary, "rothEnabledCheckBox");
            assertTrue(enabled.isDisabled()); assertTrue(otherEnabled.isDisabled());
            assertEquals(((Label) field(tab, "availabilityLabel")).getText(), ((Label) field(summary, "rothAvailabilityLabel")).getText());
            plan.setRothConversionRequest(request());
            tab.load(plan); summary.load(plan, null, List.of());
            assertFalse(enabled.isDisabled()); assertFalse(otherEnabled.isDisabled());
            assertTrue(((TextField) field(tab, "conversionYearField")).isDisabled());
            assertTrue(tab.applyChanges()); // Passive unchanged saved schedule remains accepted.
            enabled.setSelected(false);
            assertTrue(tab.applyChanges());
            assertFalse(plan.getRothConversionRequest().isEnabled());
            assertEquals(request().getAnnualAmount(), plan.getRothConversionRequest().getAnnualAmount());
            assertEquals(request().getStartYear(), plan.getRothConversionRequest().getStartYear());
            assertTrue(enabled.isDisabled());
            plan.setRothConversionRequest(request()); summary.load(plan, null, List.of());
            var accepted = new AtomicReference<RothConversionRequest>();
            summary.setOnRothConversionApply(accepted::set);
            otherEnabled.setSelected(false);
            ((Button) field(summary, "applyRothButton")).fire();
            assertNotNull(accepted.get()); assertFalse(accepted.get().isEnabled());
            assertEquals(request().getAnnualAmount(), accepted.get().getAnnualAmount());
        });
    }

    @Test void accountRemovalReassessesWithoutOverwritingPendingTabEdits() throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(true);
            var source = new TraditionalIRA("Spouse IRA", AccountOwnership.SPOUSE, BigDecimal.ZERO);
            plan.getAccountPortfolio().addAccount(source);
            plan.setRothConversionRequest(request());
            var view = new RothConversionView(); view.load(plan);
            var summary = new ResultsSummaryView(new ApplicationController()); summary.load(plan, null, List.of());
            ((TextField) field(summary, "rothStartYearField")).setText("2031");
            assertFalse(((CheckBox) field(view, "enabledCheckBox")).isDisabled());
            ((TextField) field(view, "conversionYearField")).setText("2030");
            plan.getAccountPortfolio().removeAccount(source);
            view.refresh(plan);
            summary.load(plan, null, List.of());
            assertEquals("2031", ((TextField) field(summary, "rothStartYearField")).getText());
            assertEquals("2030", ((TextField) field(view, "conversionYearField")).getText());
            var before = CompleteProjectionSnapshot.freeze(plan);
            assertFalse(view.applyChanges());
            assertEquals(before, CompleteProjectionSnapshot.freeze(plan));
            view.cancelChanges();
            assertEquals("2027", ((TextField) field(view, "conversionYearField")).getText());
        });
    }

    @Test void canceledTransactionalApplyPreservesBothSurfacesDraftsAndPlan() throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            plan.getAccountPortfolio().addAccount(new TraditionalIRA("Primary source", AccountOwnership.PRIMARY, BigDecimal.ZERO));
            var tab = new RothConversionView();
            tab.load(plan);
            ((CheckBox) field(tab, "enabledCheckBox")).setSelected(true);
            ((TextField) field(tab, "conversionYearField")).setText("2028");
            tab.setTransactionalApplyHandler(request -> false);
            int originalCount = plan.getAccountPortfolio().getAccounts().size();
            assertFalse(tab.applyChanges());
            assertNull(plan.getRothConversionRequest());
            assertEquals(originalCount, plan.getAccountPortfolio().getAccounts().size());
            assertEquals("2028", ((TextField) field(tab, "conversionYearField")).getText());
            assertTrue(tab.isDirty());

            var summary = new ResultsSummaryView(new ApplicationController());
            summary.load(plan, null, List.of());
            summary.setRothConversionTransactionHandler(request -> false);
            ((CheckBox) field(summary, "rothEnabledCheckBox")).setSelected(true);
            ((TextField) field(summary, "rothStartYearField")).setText("2029");
            ((Button) field(summary, "applyRothButton")).fire();
            assertNull(plan.getRothConversionRequest());
            assertEquals(originalCount, plan.getAccountPortfolio().getAccounts().size());
            assertEquals("2029", ((TextField) field(summary, "rothStartYearField")).getText());
        });
    }

    @ParameterizedTest @CsvSource({"LIGHT", "DARK"})
    void modeledDestinationConfirmationRendersForBothOwnersInBothThemes(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var themes = ApplicationThemeManager.start(theme)) {
                var stage = new Stage();
                stage.setScene(new Scene(new javafx.scene.layout.StackPane(), 700, 500));
                stage.show();
                try {
                    var dialog = com.daviddunn.retirementplanner.ui.dialogs.RothDestinationCreationDialog.create(
                            stage, List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
                    dialog.show();
                    try {
                        dialog.getDialogPane().applyCss();
                        dialog.getDialogPane().layout();
                        save(dialog.getDialogPane(), theme + "-modeled-destination-confirmation");
                        String content = ((Label) dialog.getDialogPane().lookup("#roth-destination-confirmation-message")).getText();
                        assertTrue(content.contains("$0"));
                        assertTrue(content.contains("retirement plan model"));
                        assertTrue(dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst()).isVisible());
                    } finally { dialog.close(); }
                } finally { stage.hide(); }
            }
        });
    }

    @ParameterizedTest @CsvSource({"false", "true"})
    void ordinaryAndGuidedAccountEditorsRequireExplicitNewChoiceButPreserveLegacy(boolean guided) throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            var dialog = new AccountDialog(null, plan.getHousehold(), guided);
            dialog.show();
            try {
                ((ComboBox<AccountType>) field(dialog, "typeCombo")).setValue(AccountType.TRADITIONAL_401K);
                ((TextField) field(dialog, "balanceField")).setText("100000");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertTrue(dialog.isShowing());
                ((ComboBox<EmployerRothConversionAvailability.Status>) dialog.getDialogPane().lookup("#employer-roth-availability"))
                        .setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                ((TextField) dialog.getDialogPane().lookup("#employer-roth-first-year")).setText("2032");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertFalse(dialog.isShowing());
                assertEquals(2032, dialog.getResult().getRothConversionAvailability().firstEligibleYear());
            } finally { dialog.close(); }
            var source = new Traditional401K("Legacy", AccountOwnership.PRIMARY, BigDecimal.TEN);
            var edit = new AccountDialog(source, plan.getHousehold(), guided);
            edit.show();
            try {
                ((TextField) field(edit, "nameField")).setText("Renamed");
                ((Button) edit.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertNull(edit.getResult().getRothConversionAvailability());
                assertNull(source.getRothConversionAvailability());
            } finally { edit.close(); }
        });
    }

    @ParameterizedTest
    @CsvSource({"TRADITIONAL_401K,false", "TRADITIONAL_401K,true", "TRADITIONAL_403B,false", "TRADITIONAL_403B,true"})
    void employerAccountFooterStaysAvailableWhenYearControlsExpand(AccountType accountType, boolean guided) throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            var owner = new Stage();
            owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 900, 650));
            owner.show();
            var dialog = new AccountDialog(null, plan.getHousehold(), guided);
            dialog.initOwner(owner);
            dialog.show();
            try {
                var type = (ComboBox<AccountType>) field(dialog, "typeCombo");
                type.setValue(accountType);
                ((TextField) field(dialog, "balanceField")).setText("100000");
                var status = (ComboBox<EmployerRothConversionAvailability.Status>)
                        dialog.getDialogPane().lookup("#employer-roth-availability");
                status.setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                var year = (TextField) dialog.getDialogPane().lookup("#employer-roth-first-year");
                year.setText("2032");
                status.setValue(EmployerRothConversionAvailability.Status.AVAILABLE);
                assertEquals("2032", year.getText(), "Switching the draft choice retains the entered year.");
                assertFalse(year.isManaged());
                status.setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                assertTrue(year.isManaged());
                dialog.getDialogPane().applyCss();
                dialog.getDialogPane().layout();
                Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
                Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
                assertTrue(save.isVisible());
                assertTrue(cancel.isVisible());
                assertTrue(save.getBoundsInParent().getMinY() >= 0);
                assertTrue(save.getBoundsInParent().getMaxY() <= dialog.getDialogPane().getHeight());
                assertTrue(dialog.getDialogPane().lookup("#account-dialog-form-scroll") instanceof ScrollPane);
                var dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
                var screenBounds = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                        .getFirst().getVisualBounds();
                assertTrue(dialogStage.getHeight() <= screenBounds.getHeight());
                assertTrue(dialogStage.getWidth() <= screenBounds.getWidth());
                save.fire();
                assertFalse(dialog.isShowing());
                assertEquals(2032, dialog.getResult().getRothConversionAvailability().firstEligibleYear());
            } finally {
                dialog.close();
                owner.hide();
            }
        });
    }

    @Test
    void yearInputValidatesMalformedAndOutOfRangeValuesAndAllowsBlankYear() throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            for (String invalid : List.of("203", "20300", "20x0", "0000", "2030.5")) {
                var dialog = new AccountDialog(null, plan.getHousehold());
                dialog.show();
                try {
                    ((ComboBox<AccountType>) field(dialog, "typeCombo")).setValue(AccountType.TRADITIONAL_403B);
                    ((TextField) field(dialog, "balanceField")).setText("100000");
                    ((ComboBox<EmployerRothConversionAvailability.Status>) dialog.getDialogPane()
                            .lookup("#employer-roth-availability"))
                            .setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                    ((TextField) dialog.getDialogPane().lookup("#employer-roth-first-year")).setText(invalid);
                    ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                    assertTrue(dialog.isShowing(), "Invalid year should keep the editor open: " + invalid);
                    Label error = (Label) dialog.getDialogPane().lookup("#employer-roth-year-error");
                    assertNotNull(error);
                    assertTrue(error.isVisible());
                    assertTrue(error.getText().contains("four digits") || error.getText().contains("0001"));
                    assertTrue(((TextField) dialog.getDialogPane().lookup("#employer-roth-first-year"))
                            .getAccessibleHelp().contains("four-digit"));
                } finally { dialog.close(); }
            }
            var blank = new AccountDialog(null, plan.getHousehold());
            blank.show();
            try {
                ((ComboBox<AccountType>) field(blank, "typeCombo")).setValue(AccountType.TRADITIONAL_401K);
                ((TextField) field(blank, "balanceField")).setText("100000");
                ((ComboBox<EmployerRothConversionAvailability.Status>) blank.getDialogPane()
                        .lookup("#employer-roth-availability"))
                        .setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                ((TextField) blank.getDialogPane().lookup("#employer-roth-first-year")).clear();
                ((Button) blank.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertNull(blank.getResult().getRothConversionAvailability().firstEligibleYear());
            } finally { blank.close(); }
        });
    }

    @Test
    void cancelingExpandedEmployerAccountDoesNotCreateOrMutateAnAccount() throws Exception {
        fx(() -> {
            var plan = BaselineDetailsTest.plan(false);
            var original = new Traditional401K("Employer", AccountOwnership.PRIMARY, BigDecimal.TEN);
            var dialog = new AccountDialog(original, plan.getHousehold());
            dialog.show();
            try {
                ((ComboBox<EmployerRothConversionAvailability.Status>) dialog.getDialogPane()
                        .lookup("#employer-roth-availability"))
                        .setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                ((TextField) dialog.getDialogPane().lookup("#employer-roth-first-year")).setText("2040");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertNull(dialog.getResult());
                assertNull(original.getRothConversionAvailability());
                assertEquals(BigDecimal.TEN, original.getCurrentBalance());
            } finally { dialog.close(); }
        });
    }

    @ParameterizedTest @CsvSource({"LIGHT,1180,820", "DARK,1180,820", "LIGHT,900,650", "DARK,900,650"})
    void renderActualViewsAndAccountEditors(ApplicationTheme theme, int width, int height) throws Exception {
        fx(() -> {
            try (var themes = ApplicationThemeManager.start(theme)) {
                var plan = BaselineDetailsTest.plan(true);
                var employer = new Traditional401K("Primary employer plan", AccountOwnership.PRIMARY, new BigDecimal("100000"));
                employer.setRothConversionAvailability(new EmployerRothConversionAvailability(
                        EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE, 2030));
                plan.getAccountPortfolio().addAccount(employer); plan.setRothConversionRequest(request());
                var tab = new RothConversionView(); tab.load(plan);
                var stage = new Stage(); stage.setScene(new Scene(tab, width, height)); stage.show();
                try {
                    tab.applyCss(); tab.layout(); save(tab, theme + "-" + width + "-tab");
                    var summary = new ResultsSummaryView(new ApplicationController()); summary.load(plan, null, List.of());
                    stage.setScene(new Scene(summary, width, height)); summary.applyCss(); summary.layout();
                    Parent card = ((Label) field(summary, "rothAvailabilityLabel")).getParent();
                    save(card, theme + "-" + width + "-summary-card");
                    var dialog = new AccountDialog(employer, plan.getHousehold(), width < 1000);
                    dialog.initOwner(stage); dialog.show();
                    try { dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                        save(dialog.getDialogPane(), theme + "-" + width + "-account");
                        ComboBox<EmployerRothConversionAvailability.Status> availability =
                                (ComboBox<EmployerRothConversionAvailability.Status>) dialog.getDialogPane()
                                        .lookup("#employer-roth-availability");
                        availability.setValue(EmployerRothConversionAvailability.Status.AVAILABLE);
                        assertEquals(EmployerRothConversionAvailability.Status.AVAILABLE, availability.getValue());
                        assertFalse(dialog.getDialogPane().lookup("#employer-roth-first-year").isManaged());
                        dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                        save(dialog.getDialogPane(), theme + "-" + width + "-account-collapsed");
                        availability.setValue(EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE);
                        assertTrue(dialog.getDialogPane().lookup("#employer-roth-first-year").isManaged());
                        dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                        save(dialog.getDialogPane(), theme + "-" + width + "-account-expanded");
                    } finally { dialog.close(); }
                } finally { stage.hide(); }
            }
        });
    }

    static RothConversionRequest request() {
        return new RothConversionRequest(true, 2027, new BigDecimal("50000.00"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL);
    }
    static void save(Parent view, String name) throws Exception {
        var image = view.snapshot(null, null);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        var directory = Path.of("target/roth-availability/previews"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(name + ".png").toFile());
    }
}
