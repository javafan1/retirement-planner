package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic UI entry fixtures, shared by end-to-end and screenshot acceptance. */
final class CompleteWizardFixtures {
    private CompleteWizardFixtures() { }

    static void enterThroughReview(DialogPane pane, boolean couple) throws Exception {
        fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
        if (couple) {
            ((Button) pane.lookup("#wizard-add-spouse")).fire();
            fill(person(pane, "spouse"), "Sam", "Example", LocalDate.of(1966, 7, 8), MortalityCategory.MALE);
        }
        button(pane, "Next").fire();
        account(pane, "Primary IRA", AccountType.TRADITIONAL_IRA, AccountOwnership.PRIMARY, "800000");
        account(pane, "Primary Roth", AccountType.ROTH_IRA, AccountOwnership.PRIMARY, "200000");
        account(pane, "Investments", AccountType.BROKERAGE, couple ? AccountOwnership.JOINT : AccountOwnership.PRIMARY,
                couple ? "750000" : "500000");
        if (couple) account(pane, "Spouse IRA", AccountType.TRADITIONAL_IRA, AccountOwnership.SPOUSE, "500000");
        button(pane, "Next").fire();
        socialSecurity(pane, "3000");
        if (couple) socialSecurity(pane, "2400");
        pension(pane, AccountOwnership.PRIMARY, "1000", couple ? "600" : null);
        if (couple) pension(pane, AccountOwnership.SPOUSE, "900", "400");
        button(pane, "Next").fire();
        expense(pane, "Retirement spending", couple ? "85000" : "60000", GrowthCategory.GENERAL);
        expense(pane, "Healthcare", "6000", GrowthCategory.HEALTHCARE);
        button(pane, "Next").fire();
        ((DatePicker) pane.lookup("#wizard-assumption-start")).setValue(LocalDate.of(2026, 1, 1));
        ((TextField) pane.lookup("#wizard-assumption-length")).setText("30");
        ((TextField) pane.lookup("#wizard-assumption-return")).setText("6.5");
        ((TextField) pane.lookup("#wizard-assumption-inflation")).setText("2.5");
        ((TextField) pane.lookup("#wizard-assumption-healthcare")).setText("4");
        ((TextField) pane.lookup("#wizard-assumption-cola")).setText("2");
        ((ComboBox<FilingStatus>) pane.lookup("#wizard-assumption-filing-status")).setValue(
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        button(pane, "Next").fire();
        assertTrue(button(pane, "Create Plan").isVisible());
    }

    static void account(DialogPane wizard, String name, AccountType type, AccountOwnership owner, String balance) {
        edit(wizard, "wizard-add-account", "wizard-input-current-balance", pane -> {
            ((TextField) pane.lookup("#wizard-input-name")).setText(name);
            ((ComboBox<AccountType>) pane.lookup("#wizard-input-type")).setValue(type);
            ((ComboBox<AccountOwnership>) pane.lookup("#wizard-input-owner")).setValue(owner);
            ((TextField) pane.lookup("#wizard-input-current-balance")).setText(balance);
        });
    }

    static void socialSecurity(DialogPane wizard, String benefit) {
        edit(wizard, "wizard-add-social-security", "wizard-input-fra-monthly-benefit", pane -> {
            ((TextField) pane.lookup("#wizard-input-fra-monthly-benefit")).setText(benefit);
            ((ComboBox<Integer>) pane.lookup("#wizard-input-claiming-age")).setValue(67);
        });
    }

    static void pension(DialogPane wizard, AccountOwnership owner, String benefit, String survivor) {
        edit(wizard, "wizard-add-pension", "wizard-input-monthly-benefit", pane -> {
            ((TextField) pane.lookup("#wizard-input-name")).setText(owner == AccountOwnership.PRIMARY ? "Primary pension" : "Spouse pension");
            ((ComboBox<AccountOwnership>) pane.lookup("#wizard-input-owner")).setValue(owner);
            ((DatePicker) pane.lookup("#wizard-input-start-date")).setValue(LocalDate.of(2026, 1, 1));
            ((TextField) pane.lookup("#wizard-input-monthly-benefit")).setText(benefit);
            ((TextField) pane.lookup("#wizard-input-annual-cola-rate")).setText("0.02");
            if (survivor != null) ((TextField) pane.lookup("#wizard-input-survivor-monthly-benefit")).setText(survivor);
        });
    }

    static void expense(DialogPane wizard, String description, String amount, GrowthCategory growth) {
        edit(wizard, "wizard-add-expense", "wizard-input-description", pane -> {
            ((TextField) pane.lookup("#wizard-input-description")).setText(description);
            ((TextField) pane.lookup("#wizard-input-amount")).setText(amount);
            ((ComboBox<GrowthCategory>) pane.lookup("#wizard-input-growth-category")).setValue(growth);
        });
    }

    static void edit(DialogPane wizard, String actionId, String editorInputId, Consumer<DialogPane> input) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            DialogPane pane = List.copyOf(Window.getWindows()).stream().filter(Window::isShowing)
                    .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                    .filter(dialog -> dialog.lookup("#" + editorInputId) != null).findFirst().orElseThrow();
            try {
                input.accept(pane);
                Window editorWindow = pane.getScene().getWindow();
                button(pane, "OK").fire();
                assertFalse(editorWindow.isShowing(), "Editor should accept the fixture");
            }
            catch (Throwable exception) {
                failure.set(exception);
                button(pane, "Cancel").fire();
            }
        });
        ((Button) wizard.lookup("#" + actionId)).fire();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
