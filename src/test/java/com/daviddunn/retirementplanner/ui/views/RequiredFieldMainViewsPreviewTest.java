package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.dialogs.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.testing.TestDataFactory;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Actual JavaFX render previews for the Phase 16B2 main-view rollout. */
class RequiredFieldMainViewsPreviewTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> start = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(start); } catch (IllegalStateException started) { Platform.runLater(start); }
        start.get(20, TimeUnit.SECONDS);
    }

    @Test void renderMainViewsAndEditorsInBothThemes() throws Exception {
        fx(() -> {
            RetirementPlan plan = plan(true);
            var controller = new ApplicationController();
            controller.newPlan(plan);
            Stage owner = new Stage();
            try {
                for (ApplicationTheme theme : ApplicationTheme.values()) {
                    try (var themes = ApplicationThemeManager.start(theme)) {
                        renderWrappedLabels(owner, theme);
                        for (boolean couple : new boolean[] {false, true}) {
                            var household = new HouseholdView(); household.load(plan(couple));
                            render(owner, household, 1180, 820, theme, "household-" + (couple ? "couple" : "single"));
                            household.dispose();
                        }

                        var wizardDraft = new com.daviddunn.retirementplanner.ui.wizard.NewPlanDraft();
                        var householdStep = new com.daviddunn.retirementplanner.ui.wizard.HouseholdWizardStep(wizardDraft);
                        var wizard = new com.daviddunn.retirementplanner.ui.wizard.NewRetirementPlanWizard(
                                owner, wizardDraft, List.of(householdStep));
                        wizard.initOwner(owner);
                        wizard.show();
                        wizard.getDialogPane().applyCss(); wizard.getDialogPane().layout();
                        save(wizard.getDialogPane().snapshot(null, null), theme,
                                "new-plan-household-primary-required-names");
                        ((Button) wizard.getDialogPane().lookup("#wizard-add-spouse")).fire();
                        wizard.getDialogPane().applyCss(); wizard.getDialogPane().layout();
                        save(wizard.getDialogPane().snapshot(null, null), theme,
                                "new-plan-household-couple-required-names");
                        wizard.close();

                        renderDialog(owner, new AccountDialog(null, plan.getHousehold(), true), theme,
                                "wizard-account-required-name");
                        renderDialog(owner, new PensionDialog(null, plan.getHousehold(), true), theme,
                                "wizard-pension-required-name");

                        var account = new AccountDialog(null, plan.getHousehold());
                        @SuppressWarnings("unchecked") ComboBox<AccountType> type = (ComboBox<AccountType>) label(account, "Type:").getLabelFor();
                        type.setValue(AccountType.TRADITIONAL_401K);
                        ((TitledPane) account.getDialogPane().lookup("#retirement-participant-facts")).setExpanded(true);
                        renderDialog(owner, account, theme, "account-employer-availability");

                        var pension = new PensionDialog(null, plan.getHousehold());
                        ((TitledPane) pension.getDialogPane().lookup("#retirement-participant-facts")).setExpanded(true);
                        renderDialog(owner, pension, theme, "pension-editor");
                        var ss = new SocialSecurityDialog(null, 2026, plan.getHousehold());
                        renderDialog(owner, ss, theme, "social-security-editor");
                        var expense = new ExpenseDialog(null);
                        renderDialog(owner, expense, theme, "expense-recurring-editor");
                        var oneTime = new ExpenseDialog(null);
                        @SuppressWarnings("unchecked") ComboBox<ExpenseType> expenseType =
                                (ComboBox<ExpenseType>) label(oneTime, "Expense Type:").getLabelFor();
                        expenseType.setValue(ExpenseType.ONE_TIME);
                        renderDialog(owner, oneTime, theme, "expense-one-time-editor");
                        renderDialog(owner, new NonInvestableAssetDialog(null), theme, "noninvestable-asset-editor");

                        var assumptions = new AssumptionsView(); assumptions.load(plan);
                        render(owner, assumptions, 1180, 820, theme, "assumptions");
                        assumptions.dispose();

                        var roth = new RothConversionView(); roth.load(plan);
                        render(owner, roth, 900, 650, theme, "roth-conversions-compact");
                        roth.dispose();

                        var summary = new ResultsSummaryView(controller);
                        summary.load(plan, null, List.of());
                        render(owner, summary, 1180, 820, theme, "summary-key-assumptions");
                        summary.dispose();
                    }
                }
            } finally {
                owner.hide();
                controller.getSettingsService().close();
            }
            return null;
        });
    }

    private static void renderWrappedLabels(Stage stage, ApplicationTheme theme) throws Exception {
        var content = new javafx.scene.layout.VBox(10);
        var attachments = new java.util.ArrayList<RequiredFieldSupport.Attachment>();
        content.setStyle("-fx-padding: 16;");
        for (int width : new int[] {280, 180, 115, 75, 60}) {
            var label = new Label("Assumed second death / end of household projection:");
            label.setWrapText(true);
            label.setPrefWidth(width);
            var field = new TextField("30 years");
            attachments.add(RequiredFieldSupport.attach(label, field));
            content.getChildren().addAll(label, field);
        }
        render(stage, content, 340, 620, theme, "wrapped-label-fixture");
        for (Node node : content.getChildren()) {
            if (node instanceof Label label && label.getGraphic() instanceof javafx.scene.text.TextFlow flow) {
                assertEquals(label.getText() + " *", flow.getChildren().stream()
                        .map(child -> child instanceof javafx.scene.text.Text text ? text.getText() : "")
                        .reduce("", String::concat));
            }
        }
        attachments.forEach(RequiredFieldSupport.Attachment::close);
    }

    private static RetirementPlan plan(boolean couple) {
        var primary = new Person("Primary", "Example", LocalDate.of(1960, 1, 1));
        primary.setMortalityCategory(MortalityCategory.MALE);
        primary.addIncomeSource(new SocialSecurityIncome("Social Security", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 1, 1), null, new BigDecimal("3000"), 67, BigDecimal.ZERO, 2026));
        var spouse = couple ? new Person("Spouse", "Example", LocalDate.of(1962, 2, 2)) : null;
        if (spouse != null) {
            spouse.setMortalityCategory(MortalityCategory.FEMALE);
            spouse.addIncomeSource(new SocialSecurityIncome("Social Security", AccountOwnership.SPOUSE,
                    LocalDate.of(2029, 1, 1), null, new BigDecimal("2000"), 67, BigDecimal.ZERO, 2026));
        }
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(new TraditionalIRA("Traditional IRA", AccountOwnership.PRIMARY, new BigDecimal("50000")));
        portfolio.addAccount(new RothIRA("Roth IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        return new RetirementPlan(new Household(primary, spouse), portfolio,
                TestDataFactory.planningAssumptions());
    }

    private static void render(Stage stage, Parent view, int width, int height,
                               ApplicationTheme theme, String name) throws Exception {
        var scene = new Scene(new StackPane(view), width, height);
        ApplicationThemeManager.prepare(scene);
        stage.setScene(scene);
        stage.show();
        view.applyCss();
        if (view instanceof javafx.scene.layout.Region region) region.autosize();
        view.layout();
        save(scene.snapshot(null), theme, name + "-" + width + "x" + height);
    }

    private static void renderDialog(Stage owner, Dialog<?> dialog, ApplicationTheme theme, String name) throws Exception {
        dialog.initOwner(owner);
        dialog.show();
        var pane = dialog.getDialogPane();
        pane.applyCss(); pane.layout();
        save(pane.snapshot(null, null), theme, name);
        dialog.close();
    }

    private static Label label(Dialog<?> dialog, String text) {
        return find(dialog.getDialogPane(), text);
    }

    private static Label find(javafx.scene.Parent root, String text) {
        if (root instanceof Label label && text.equals(label.getText())) return label;
        for (Node node : root.getChildrenUnmodifiable()) if (node instanceof javafx.scene.Parent parent) {
            try { return find(parent, text); } catch (AssertionError ignored) { }
        }
        throw new AssertionError("Missing label " + text);
    }

    private static void save(WritableImage image, ApplicationTheme theme, String name) throws Exception {
        Path directory = Path.of("target/phase16b2/previews");
        Files.createDirectories(directory);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
            pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(theme.name().toLowerCase() + "-" + name + ".png").toFile());
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(60, TimeUnit.SECONDS);
    }
}
