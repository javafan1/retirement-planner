package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.ui.dialogs.SocialSecurityDialog;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/** Per-person cards edit the same income records used by pensions and projection. */
public final class SocialSecurityWizardStep implements NewPlanWizardStep {

    private final NewPlanDraft draft;
    private final VBox content = new VBox(12);
    private final VBox cards = new VBox(12);
    private final Label error = text("");

    public SocialSecurityWizardStep(NewPlanDraft draft) {
        this.draft = Objects.requireNonNull(draft);
        error.getStyleClass().add("wizard-field-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        content.getChildren().setAll(text("Social Security is optional. Enter a gross monthly estimate at Full Retirement Age (FRA). "
                + "Claiming age is a provisional plan election and is not necessarily FRA. Claiming strategy analysis is available after creation."),
                text("Social Security COLA is a plan-level assumption, shown in Assumptions. Benefits keep their actual stored valuation year."), cards, error);
        onEntering();
    }

    @Override public StepKey key() { return StepKey.SOCIAL_SECURITY; }
    @Override public String title() { return "Social Security"; }
    @Override public Node content() { return content; }

    @Override public void onEntering() {
        cards.getChildren().clear();
        draft.getPlan().getHousehold().peopleByOwner().forEach((owner, person) -> {
            String suffix = owner.name().toLowerCase(java.util.Locale.ROOT);
            String who = AccountsWizardStep.displayOwner(owner) + (person.getFullName().isBlank() ? "" : " — " + person.getFullName().trim());
            var sources = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                    .map(SocialSecurityIncome.class::cast).toList();
            Label heading = text(who);
            heading.getStyleClass().add("wizard-person-heading");
            VBox card = new VBox(8, heading);
            card.setId("wizard-social-security-" + suffix);
            card.getStyleClass().add("wizard-review-section");
            if (sources.isEmpty()) {
                Label status = text("Not entered (optional)");
                status.setAccessibleText(who + ": Social Security not entered; optional");
                Button add = action("Add Social Security", "wizard-add-social-security-" + suffix, who, () -> edit(owner, null));
                card.getChildren().addAll(status, add);
            }
            else {
                for (var source : sources) {
                    Label summary = text("Owner: " + AccountsWizardStep.displayOwner(source.getOwnership())
                            + "\nGross FRA benefit: $" + source.getFullRetirementMonthlyBenefit().toPlainString() + "/month"
                            + "\nClaiming age: " + source.getClaimingAge() + "; Derived start: " + source.getStartDate()
                            + "\nBenefit valuation year: " + source.getBenefitValuationYear());
                    Button edit = action("Edit", "wizard-edit-social-security-" + suffix, who, () -> edit(owner, source));
                    Button remove = action("Remove", "wizard-remove-social-security-" + suffix, who, () -> {
                        draft.removeIncomeSource(source);
                        onEntering();
                    });
                    card.getChildren().addAll(summary, new FlowPane(10, 8, edit, remove));
                }
            }
            cards.getChildren().add(card);
        });
    }

    private void edit(AccountOwnership owner, SocialSecurityIncome original) {
        var person = draft.getPlan().getHousehold().peopleByOwner().get(owner);
        if (original == null && person.getIncomeSources().stream().anyMatch(SocialSecurityIncome.class::isInstance)) return;
        var dialog = new SocialSecurityDialog(original,
                draft.getPlan().getPlanningAssumptions().getProjectionStartDate().getYear(),
                draft.getPlan().getHousehold(), original == null ? List.of(owner)
                : draft.getPlan().getHousehold().peopleByOwner().entrySet().stream()
                .filter(entry -> entry.getValue().getIncomeSources().stream()
                        .noneMatch(source -> source instanceof SocialSecurityIncome && source != original))
                .map(java.util.Map.Entry::getKey).toList());
        if (content.getScene() != null) dialog.initOwner(content.getScene().getWindow());
        dialog.showAndWait().ifPresent(updated -> {
            draft.replaceIncomeSource(original, updated);
            onEntering();
        });
    }

    @Override public boolean validateAndApply() {
        try {
            draft.getPlan().validateHouseholdReferences();
            error.setText("");
            return true;
        }
        catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
            return false;
        }
    }

    private static Button action(String label, String id, String owner, Runnable run) {
        Button button = new Button(label);
        button.setId(id);
        button.setAccessibleText((label.equals("Add Social Security") ? "Add" : label) + " Social Security for " + owner);
        button.setOnAction(event -> run.run());
        return button;
    }

    private static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }
}
