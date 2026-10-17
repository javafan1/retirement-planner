package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real JavaFX snapshots with synthetic identities; opt in for desktop acceptance. */
@EnabledIfSystemProperty(named = "complete.wizard.preview", matches = "true")
class CompleteWizardPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void inspectCompleteWizardStatesAndInputHover() throws Exception {
        Path folder = Path.of("target/phase7c4-wizard-preview");
        Files.createDirectories(folder);
        var states = List.of("household-single", "household-couple", "accounts-empty", "accounts-single", "accounts-couple",
                "social-security-single", "social-security-couple", "pensions-single", "pensions-couple", "spending-empty", "spending-populated", "assumptions-defaults",
                "assumptions-validation", "review-single", "review-couple", "review-warning", "review-error",
                "narrow-household", "narrow-accounts", "narrow-assumptions", "narrow-review",
                "social-security-empty", "pensions-empty", "narrow-social-security", "narrow-pensions");
        StringBuilder geometry = new StringBuilder();
        for (String state : states) {
            AtomicReference<NewRetirementPlanWizard> reference = new AtomicReference<>();
            fx(() -> {
                var wizard = new NewRetirementPlanWizard(null);
                reference.set(wizard);
                wizard.show();
                var pane = wizard.getDialogPane();
                boolean populated = state.endsWith("single") || state.endsWith("couple") || state.endsWith("populated")
                        || state.startsWith("narrow-");
                if (populated) CompleteWizardFixtures.enterThroughReview(pane, state.endsWith("couple") || state.startsWith("narrow-"));
                else {
                    fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                    for (int index = 0; index < 6; index++) button(pane, "Next").fire();
                }
                String section = state.startsWith("narrow-") ? state.substring(7) : state.startsWith("social-security-") ? "social-security" : state.substring(0, state.indexOf('-'));
                if (!section.equals("review")) ((Button) pane.lookup("#wizard-review-edit-" + section)).fire();
                if (section.equals("pensions")) ((TableView<?>) pane.lookup("#wizard-pensions")).getSelectionModel().selectFirst();
                if (section.equals("accounts")) ((TableView<?>) pane.lookup("#wizard-accounts")).getSelectionModel().selectFirst();
                if (state.equals("assumptions-validation")) {
                    ((TextField) pane.lookup("#wizard-assumption-length")).setText("0");
                    button(pane, "Next").fire();
                }
                if (state.equals("review-error")) {
                    NewPlanDraft draft = field(wizard, "draft");
                    draft.getPlan().getHousehold().getPrimaryPerson().setBirthDate(null);
                    ((ReviewWizardStep) ((List<?>) field(wizard, "steps")).get(6)).onEntering();
                }
                if (state.startsWith("narrow-")) {
                    pane.getScene().getWindow().setWidth(490);
                    pane.getScene().getWindow().setHeight(570);
                }
            });
            try {
                Thread.sleep(200);
                fx(() -> {
                    var pane = reference.get().getDialogPane();
                    pane.applyCss(); pane.layout();
                    ScrollPane page = (ScrollPane) pane.lookup("#wizard-page");
                    assertTrue(page.getViewportBounds().getWidth() > 300);
                    assertTrue(page.getViewportBounds().getHeight() > 190);
                    for (String text : List.of("Back", "Next", "Create Plan", "Cancel")) {
                        Button action = button(pane, text);
                        if (!action.isVisible()) continue;
                        var bounds = action.localToScene(action.getBoundsInLocal());
                        assertTrue(bounds.getMaxX() <= pane.getScene().getWidth(), state + " clipped action");
                        assertTrue(bounds.getMaxY() <= pane.getScene().getHeight(), state + " clipped action");
                    }
                    assertTrue(pane.lookupAll(".help-icon").isEmpty());
                    assertTrue(pane.lookupAll(".ikonli-font-icon").isEmpty());
                    if (state.contains("assumptions")) {
                        double left = -1;
                        for (String id : List.of("start", "length", "return", "inflation", "healthcare", "cola", "filing-status")) {
                            if (List.of("healthcare", "cola").contains(id)
                                    && !((TitledPane) pane.lookup("#wizard-advanced-assumptions")).isExpanded()) continue;
                            Control input = (Control) pane.lookup("#wizard-assumption-" + id);
                            assertNotNull(input.getTooltip());
                            var bounds = page.getContent().sceneToLocal(input.localToScene(input.getLayoutBounds()));
                            assertTrue(bounds.getMaxX() <= page.getViewportBounds().getWidth() + 1, state + " clipped input " + id);
                            // The first three guided fields now have stacked labels and visible units.
                            if (!List.of("start", "length", "return", "healthcare", "cola").contains(id)) {
                                if (left < 0) left = bounds.getMinX();
                                assertEquals(left, bounds.getMinX(), 1);
                            }
                        }
                    }
                    write(pane.snapshot(null, null), folder.resolve(state + ".png"));
                    geometry.append(state).append(": ").append(pane.getWidth()).append(" x ").append(pane.getHeight())
                            .append("; viewport height ").append(page.getViewportBounds().getHeight())
                            .append("; page height ").append(page.getContent().getBoundsInLocal().getHeight()).append("\n");
                    if (state.equals("review-couple") || state.equals("narrow-review") || state.contains("pensions")) {
                        page.setVvalue(1); pane.layout();
                        write(pane.snapshot(null, null), folder.resolve(state + "-bottom.png"));
                    }
                });
                if (state.equals("assumptions-defaults")) {
                    fx(() -> new javafx.scene.robot.Robot().mouseMove(0, 0));
                    Thread.sleep(350);
                    fx(() -> {
                        Control input = (Control) reference.get().getDialogPane().lookup("#wizard-assumption-return");
                        var bounds = input.localToScreen(input.getBoundsInLocal());
                        new javafx.scene.robot.Robot().mouseMove(bounds.getCenterX(), bounds.getCenterY());
                        input.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 5, 5, bounds.getCenterX(), bounds.getCenterY(),
                                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                                false, false, false, new PickResult(input, 5, 5)));
                    });
                    Thread.sleep(800);
                    fx(() -> {
                        Tooltip tooltip = ((Control) reference.get().getDialogPane().lookup("#wizard-assumption-return")).getTooltip();
                        assertTrue(tooltip.isShowing(), "Direct input hover must retain the existing help");
                        write(tooltip.getScene().getRoot().snapshot(null, null), folder.resolve("investment-return-help.png"));
                        tooltip.hide();
                    });
                }
            }
            finally { fx(() -> reference.get().close()); }
        }
        Files.writeString(folder.resolve("geometry.txt"), geometry.toString());
        for (int group = 0; group < (states.size() + 5) / 6; group++) {
            BufferedImage sheet = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
            var graphics = sheet.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 1200, 900);
            for (int cell = 0; cell < 6 && group * 6 + cell < states.size(); cell++) {
                String state = states.get(group * 6 + cell);
                BufferedImage screenshot = javax.imageio.ImageIO.read(folder.resolve(state + ".png").toFile());
                int x = cell % 3 * 400; int y = cell / 3 * 450;
                graphics.setColor(java.awt.Color.BLACK); graphics.drawString(state, x + 8, y + 16);
                graphics.drawImage(screenshot, x, y + 25, 390, 410, null);
            }
            graphics.dispose();
            javax.imageio.ImageIO.write(sheet, "png", folder.resolve("contact-sheet-" + group + ".png").toFile());
        }
    }

    @Test void inspectConfirmationBlockedRemovalAndLongAccountDetails() throws Exception {
        Path folder = Path.of("target/phase7c3-wizard-preview");
        Files.createDirectories(folder);
        AtomicReference<NewRetirementPlanWizard> reference = new AtomicReference<>();
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(true);
            draft.getPlan().getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                    "Joint account blocks removal", com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT,
                    new java.math.BigDecimal("12345.6700")));
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            reference.set(wizard);
            wizard.show();
        });
        try {
            pulses();
            fx(() -> {
                var wizard = reference.get();
                var pane = wizard.getDialogPane();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                javafx.application.Platform.runLater(() -> {
                    var confirmation = SevenStepWizardTest.confirmation();
                    try {
                        confirmation.applyCss(); confirmation.layout();
                        write(confirmation.snapshot(null, null), folder.resolve("spouse-removal-confirmation.png"));
                    }
                    catch (Throwable exception) { failure.set(exception); }
                    finally { button(confirmation, "Cancel").fire(); }
                });
                ((Button) pane.lookup("#wizard-remove-spouse")).fire();
                if (failure.get() != null) throw new AssertionError(failure.get());
                assertNotNull(person(pane, "spouse"));
                answerSpouseRemoval(true);
                ((Button) pane.lookup("#wizard-remove-spouse")).fire();
            });
            pulses();
            fx(() -> {
                var wizard = reference.get();
                var pane = wizard.getDialogPane();
                assertTrue(((Label) pane.lookup("#wizard-validation")).getText().contains("Joint account blocks removal"));
                write(pane.snapshot(null, null), folder.resolve("spouse-removal-blocked.png"));
                NewPlanDraft draft = field(wizard, "draft");
                for (int index = 0; index < 20; index++) draft.getPlan().getAccountPortfolio().addAccount(
                        new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Long account description " + index,
                                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, new java.math.BigDecimal("12345.6700")));
                button(pane, "Next").fire();
                pane.getScene().getWindow().setWidth(490);
                pane.getScene().getWindow().setHeight(570);
            });
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                TableView<?> table = (TableView<?>) pane.lookup("#wizard-accounts");
                table.getSelectionModel().select(20);
                table.scrollTo(20);
                assertEquals(21, table.getItems().size());
                assertTrue(((Label) pane.lookup("#wizard-account-details")).getText().contains("12345.6700"));
            });
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                var page = (ScrollPane) pane.lookup("#wizard-page");
                page.setVvalue(1);
                pane.layout(); page.layout();
                write(pane.snapshot(null, null), folder.resolve("narrow-long-accounts.png"));
                var bounds = button(pane, "Next").localToScene(button(pane, "Next").getBoundsInLocal());
                assertTrue(bounds.getMaxY() <= pane.getScene().getHeight());
                for (String id : List.of("wizard-add-account", "wizard-edit-account", "wizard-remove-account")) {
                    var action = pane.lookup("#" + id);
                    var actionBounds = page.sceneToLocal(action.localToScene(action.getBoundsInLocal()));
                    assertTrue(actionBounds.getMinY() >= 0 && actionBounds.getMaxY() <= page.getHeight());
                }
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    private static void write(WritableImage image, Path path) throws Exception {
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }

    @Test void inspectSpendingAssumptionsAndValuationClarity() throws Exception {
        Path folder = Path.of("target/phase7c4-clarity-preview");
        Files.createDirectories(folder);
        var states = List.of("spending-empty", "spending-recurring", "spending-one-time", "spending-long",
                "assumptions-collapsed", "assumptions-expanded", "assumptions-invalid", "ss-matching", "ss-earlier",
                "ss-later", "ss-couple", "review", "review-empty", "narrow-assumptions", "narrow-spending", "narrow-review", "narrow-ss");
        for (String state : states) {
            var reference = new AtomicReference<NewRetirementPlanWizard>();
            fx(() -> {
                boolean couple = state.contains("review") || state.equals("ss-couple");
                var draft = WizardClarityTest.draft(couple);
                var plan = draft.getPlan();
                boolean review = state.contains("review");
                if (state.contains("ss-") || state.equals("narrow-ss") || review && !state.endsWith("empty")) {
                    int year = state.equals("ss-matching") ? 2026 : state.equals("ss-later") ? 2028 : 2024;
                    plan.getHousehold().getPrimaryPerson().addIncomeSource(WizardClarityTest.source(
                            com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, year));
                    if (couple) plan.getHousehold().getSpouse().addIncomeSource(WizardClarityTest.source(
                            com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE, 2028));
                }
                if (state.contains("spending") && !state.endsWith("empty") || review && !state.endsWith("empty")) {
                    int count = state.endsWith("long") || state.equals("narrow-spending") ? 20 : 1;
                    for (int index = 0; index < count; index++) plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                            "Recurring healthcare " + index, new java.math.BigDecimal("12345.6700"),
                            com.daviddunn.retirementplanner.domain.financial.GrowthCategory.HEALTHCARE,
                            LocalDate.of(2027, 1, 1), LocalDate.of(2040, 12, 31), com.daviddunn.retirementplanner.domain.financial.ExpenseType.RECURRING));
                    if (state.contains("one-time") || review) plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense(
                            "One-time purchase", new java.math.BigDecimal("9876.5400"), com.daviddunn.retirementplanner.domain.financial.GrowthCategory.GENERAL,
                            LocalDate.of(2030, 5, 6), LocalDate.of(2030, 5, 6), com.daviddunn.retirementplanner.domain.financial.ExpenseType.ONE_TIME));
                }
                var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
                reference.set(wizard); wizard.show();
                int destination = review ? 6 : state.contains("assumptions") ? 5 : state.contains("spending") ? 4 : 2;
                for (int index = 0; index < destination; index++) button(wizard.getDialogPane(), "Next").fire();
                var pane = wizard.getDialogPane();
                pane.applyCss(); pane.layout();
                if (destination == 4) {
                    var table = (TableView<?>) pane.lookup("#wizard-expenses");
                    table.getSelectionModel().selectLast(); table.scrollTo(table.getItems().size() - 1);
                }
                if (destination == 5) {
                    var editor = WizardClarityTest.editor(wizard);
                    if (state.endsWith("expanded")) ((TitledPane) field(editor, "guidedAdvanced")).setExpanded(true);
                    if (state.endsWith("invalid")) {
                        ((TextField) field(editor, "healthcareInflationField")).setText("bad healthcare");
                        button(pane, "Next").fire();
                    }
                }
                if (state.startsWith("narrow")) {
                    pane.getScene().getWindow().setWidth(490);
                    pane.getScene().getWindow().setHeight(570);
                }
            });
            try {
                pulses();
                fx(() -> {
                    var pane = reference.get().getDialogPane();
                    pane.applyCss(); pane.layout();
                    for (String action : List.of("Back", "Next", "Create Plan", "Cancel")) {
                        var button = button(pane, action);
                        if (!button.isVisible()) continue;
                        var bounds = button.localToScene(button.getBoundsInLocal());
                        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= pane.getScene().getWidth());
                        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= pane.getScene().getHeight());
                    }
                    write(pane.snapshot(null, null), folder.resolve(state + ".png"));
                    var page = (ScrollPane) pane.lookup("#wizard-page");
                    page.setVvalue(1); pane.layout(); page.layout();
                    write(pane.snapshot(null, null), folder.resolve(state + "-bottom.png"));
                });
            }
            finally { fx(() -> reference.get().close()); }
        }
        for (int group = 0; group < (states.size() + 5) / 6; group++) {
            BufferedImage sheet = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
            var graphics = sheet.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 1200, 900);
            for (int cell = 0; cell < 6 && group * 6 + cell < states.size(); cell++) {
                String state = states.get(group * 6 + cell);
                var snapshot = javax.imageio.ImageIO.read(folder.resolve(state + ".png").toFile());
                int x = cell % 3 * 400; int y = cell / 3 * 450;
                graphics.setColor(java.awt.Color.BLACK); graphics.drawString(state, x + 8, y + 16);
                graphics.drawImage(snapshot, x, y + 25, 390, 410, null);
            }
            graphics.dispose();
            javax.imageio.ImageIO.write(sheet, "png", folder.resolve("contact-sheet-" + group + ".png").toFile());
        }
    }
}
