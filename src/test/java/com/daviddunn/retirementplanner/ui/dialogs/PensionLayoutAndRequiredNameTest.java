package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PensionLayoutAndRequiredNameTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> start = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(start); } catch (IllegalStateException started) { Platform.runLater(start); }
        start.get(20, TimeUnit.SECONDS);
    }

    @Test void pensionFooterRemainsReachableWhenFactsExpandAndErrorsScrollIntoView() throws Exception {
        for (ApplicationTheme theme : ApplicationTheme.values()) {
            fx(() -> {
                var themes = ApplicationThemeManager.start(theme);
                var owner = new Stage();
                owner.setScene(new Scene(new StackPane(), 640, 480));
                owner.show();
                var dialog = new PensionDialog(null, household());
                dialog.initOwner(owner);
                dialog.show();
                var pane = dialog.getDialogPane();
                pane.applyCss(); pane.layout();
                var dialogStage = (Stage) pane.getScene().getWindow();
                dialogStage.setHeight(Math.min(520, Screen.getPrimary().getVisualBounds().getHeight() - 24));
                pane.applyCss(); pane.layout();
                ScrollPane scroll = (ScrollPane) pane.lookup("#pension-dialog-form-scroll");
                assertNotNull(scroll, "Pension form is scrollable in " + theme);
                var save = (Button) pane.lookupButton(ButtonType.OK);
                var cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
                assertVisibleInsideScene(save, pane);
                assertVisibleInsideScene(cancel, pane);
                save(dialog, pane, theme, "collapsed");

                var participant = (com.daviddunn.retirementplanner.ui.controls.RetirementParticipantFactsEditor)
                        pane.lookup("#retirement-participant-facts");
                participant.setExpanded(true);
                pane.applyCss(); pane.layout();
                assertTrue(participant.isExpanded());
                assertVisibleInsideScene(save, pane);
                assertVisibleInsideScene(cancel, pane);
                save(dialog, pane, theme, "expanded");

                var name = field(dialog, "nameField", TextField.class);
                name.setText("   ");
                save.fire();
                assertTrue(dialog.isShowing());
                Label nameError = (Label) pane.lookup("#pension-name-error");
                assertEquals("Pension name is required.", nameError.getText());
                assertTrue(nameError.isManaged());
                assertTrue(nameError.isVisible());
                assertTrue(nameError.getHeight() > 0, "name validation feedback should occupy visible layout space");
                save(dialog, pane, theme, "name-error");

                name.setText("Pension");
                field(dialog, "startDatePicker", DatePicker.class).setValue(LocalDate.of(2030, 1, 1));
                field(dialog, "monthlyBenefitField", TextField.class).setText("2000");
                field(dialog, "colaRateField", TextField.class).setText("bad");
                save.fire();
                assertTrue(dialog.isShowing());
                Label validation = (Label) pane.lookup("#pension-validation-error");
                assertEquals("Enter a valid annual COLA rate.", validation.getText());
                assertTrue(validation.isVisible());
                assertTrue(validation.getHeight() > 0);
                var cola = field(dialog, "colaRateField", TextField.class);
                var colaBounds = cola.localToScene(cola.getBoundsInLocal());
                var viewportBounds = scroll.localToScene(scroll.getViewportBounds());
                assertTrue(colaBounds.getMinY() >= viewportBounds.getMinY() - 1
                                && colaBounds.getMaxY() <= viewportBounds.getMaxY() + 1,
                        "invalid annual COLA input should remain visible with its validation message");
                assertVisibleInsideScene(save, pane);
                assertVisibleInsideScene(cancel, pane);
                save(dialog, pane, theme, "validation-error");
                cancel.fire();
                assertFalse(dialog.isShowing());
                owner.close();
                themes.close();
            });
        }
    }

    @Test void pensionNamesRejectBlankPreserveDraftAndNormalizeUnicodeAtAcceptance() throws Exception {
        fx(() -> {
            var dialog = new PensionDialog(null, household());
            dialog.show();
            try {
                field(dialog, "startDatePicker", DatePicker.class).setValue(LocalDate.of(2030, 1, 1));
                field(dialog, "monthlyBenefitField", TextField.class).setText("2000");
                field(dialog, "colaRateField", TextField.class).setText("0.02");
                TextField name = field(dialog, "nameField", TextField.class);
                for (String invalid : new String[] {"", "   ", "\u2003"}) {
                    name.setText(invalid);
                    ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                    assertTrue(dialog.isShowing());
                    assertEquals(invalid, name.getText());
                    assertEquals("Pension name is required.",
                            ((Label) dialog.getDialogPane().lookup("#pension-name-error")).getText());
                }
                name.setText("  Zoë 李 O’Neil!  ");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertFalse(dialog.isShowing());
                assertEquals("Zoë 李 O’Neil!", dialog.getResult().getName());
            } finally { dialog.close(); }
        });
    }

    @Test void accountNameIsRequiredAndTrimmedAtEditorBoundary() throws Exception {
        fx(() -> {
            var dialog = new AccountDialog(null, household());
            dialog.show();
            try {
                field(dialog, "balanceField", TextField.class).setText("100");
                TextField name = field(dialog, "nameField", TextField.class);
                name.setText(" \u2003 ");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertTrue(dialog.isShowing());
                assertEquals("Account name is required.",
                        ((Label) dialog.getDialogPane().lookup("#account-name-error")).getText());
                assertEquals(" \u2003 ", name.getText());
                name.setText("  Brokerage — 退休!  ");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertFalse(dialog.isShowing());
                assertEquals("Brokerage — 退休!", dialog.getResult().getName());
            } finally { dialog.close(); }
        });
    }

    @Test void editingLegacyBlankNameRecordsRequiresCorrectionBeforeAcceptingEdit() throws Exception {
        fx(() -> {
            var legacyPension = new Pension("", AccountOwnership.PRIMARY, LocalDate.of(2030, 1, 1), null,
                    new BigDecimal("2000"), BigDecimal.ZERO);
            var pensionDialog = new PensionDialog(legacyPension, household());
            pensionDialog.show();
            try {
                ((Button) pensionDialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertTrue(pensionDialog.isShowing());
                assertEquals("", legacyPension.getName());
                assertEquals("Pension name is required.",
                        ((Label) pensionDialog.getDialogPane().lookup("#pension-name-error")).getText());
            } finally { pensionDialog.close(); }

            var legacyAccount = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                    "", AccountOwnership.PRIMARY, BigDecimal.ZERO);
            var accountDialog = new AccountDialog(legacyAccount, household());
            accountDialog.show();
            try {
                ((Button) accountDialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertTrue(accountDialog.isShowing());
                assertEquals("", legacyAccount.getName());
                assertEquals("Account name is required.",
                        ((Label) accountDialog.getDialogPane().lookup("#account-name-error")).getText());
            } finally { accountDialog.close(); }
        });
    }

    @Test void legacyBlankNameModelsRemainConstructibleAndWholePlanSerializationDoesNotMigrateThem() throws Exception {
        fx(() -> {
            var plan = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createSinglePersonPlan();
            plan.getHousehold().getPrimaryPerson().setFirstName("");
            plan.getHousehold().getPrimaryPerson().setLastName("  ");
            var account = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                    "", AccountOwnership.PRIMARY, BigDecimal.ZERO);
            plan.getAccountPortfolio().addAccount(account);
            plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("", AccountOwnership.PRIMARY,
                    LocalDate.of(2030, 1, 1), null, BigDecimal.ONE, BigDecimal.ZERO));
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            try {
                String encoded = mapper.writeValueAsString(plan);
                var restored = mapper.readValue(encoded,
                        com.daviddunn.retirementplanner.domain.model.RetirementPlan.class);
                assertEquals("", restored.getHousehold().getPrimaryPerson().getFirstName());
                assertEquals("  ", restored.getHousehold().getPrimaryPerson().getLastName());
                assertEquals("", restored.getAccountPortfolio().getAccounts().getFirst().getName());
                assertEquals("", restored.getHousehold().getPrimaryPerson().getIncomeSources().getFirst().getName());
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
    }

    private static Household household() {
        var primary = new Person("Taylor", "Morgan", LocalDate.of(1960, 1, 1));
        primary.setMortalityCategory(MortalityCategory.FEMALE);
        return new Household(primary, null);
    }

    private static void assertVisibleInsideScene(Node node, Node root) {
        assertTrue(node.isVisible());
        var bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinY() >= -1, "footer control starts outside dialog scene: " + bounds);
        assertTrue(bounds.getMaxY() <= root.getScene().getHeight() + 1,
                "footer control is clipped by dialog scene: " + bounds + " scene=" + root.getScene().getHeight());
    }

    private static void save(Dialog<?> dialog, Parent pane, ApplicationTheme theme, String state) throws Exception {
        pane.applyCss(); pane.layout();
        if (pane.lookup("#pension-dialog-form-scroll") instanceof ScrollPane scroll
                && scroll.getContent() instanceof javafx.scene.Parent content) {
            content.applyCss(); content.layout();
            scroll.applyCss(); scroll.layout();
        }
        var image = pane.snapshot(null, null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
            pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Path folder = Path.of("target/phase16b2c/previews");
        Files.createDirectories(folder);
        ImageIO.write(pixels, "png", folder.resolve("pension-" + state + "-" + theme.name().toLowerCase() + ".png").toFile());
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static void fx(Checked action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
