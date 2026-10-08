package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;

/** Only implemented steps are registered; cancellation has no application-side effects. */
public final class NewRetirementPlanWizard extends Dialog<RetirementPlan> {

    private final NewPlanDraft draft;
    private final List<NewPlanWizardStep> steps;
    private final Label stepLabel = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final ScrollPane page = new ScrollPane();
    private final ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
    private final ButtonType nextType = new ButtonType("Next", ButtonBar.ButtonData.NEXT_FORWARD);
    private final ButtonType createType = new ButtonType("Create Plan", ButtonBar.ButtonData.OK_DONE);
    private int stepIndex;
    private RetirementPlan completedPlan;

    public NewRetirementPlanWizard(Window owner) {

        this(owner, new NewPlanDraft());
    }

    private NewRetirementPlanWizard(Window owner, NewPlanDraft draft) {

        this(owner, draft, List.of(new HouseholdWizardStep(draft), new AccountsWizardStep(draft),
                new IncomeWizardStep(draft), new ExpensesWizardStep(draft),
                new AssumptionsWizardStep(draft), new ReviewWizardStep(draft)));
    }

    /** Additional implemented pages can be registered without changing navigation. */
    public NewRetirementPlanWizard(Window owner, NewPlanDraft draft, List<NewPlanWizardStep> steps) {

        this.draft = Objects.requireNonNull(draft, "Draft is required.");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("At least one implemented wizard step is required.");
        }
        if (owner != null) {
            initOwner(owner);
        }
        setTitle("New Retirement Plan");
        setResizable(true);
        getDialogPane().setId("new-plan-wizard");
        getDialogPane().getStyleClass().add("new-plan-wizard");
        getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/new-plan-wizard.css")).toExternalForm());
        Label title = new Label("NEW RETIREMENT PLAN");
        title.getStyleClass().add("wizard-title");
        title.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label description = new Label("Build your initial retirement plan. Additional and advanced details remain available in the plan's tabs.");
        description.setWrapText(true);
        description.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        description.getStyleClass().add("wizard-description");
        stepLabel.getStyleClass().add("wizard-step-heading");
        stepLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        progress.setId("wizard-progress");
        page.setFitToWidth(true);
        page.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        page.setMinHeight(0);
        page.setId("wizard-page");
        Label path = new Label(this.steps.stream().map(NewPlanWizardStep::title).collect(java.util.stream.Collectors.joining(" → ")));
        path.setId("wizard-step-path");
        path.setWrapText(true);
        path.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        path.getStyleClass().add("wizard-description");
        VBox content = new VBox(12, title, description, path, stepLabel, progress, page);
        content.setPadding(new Insets(18));
        VBox.setVgrow(page, javafx.scene.layout.Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(760, 700);
        getDialogPane().setMinSize(460, 380);
        getDialogPane().getButtonTypes().setAll(backType, nextType, createType, ButtonType.CANCEL);
        button(backType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (stepIndex > 0) {
                stepIndex--;
                showStep();
            }
        });
        button(nextType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (stepIndex == steps.size() - 1) return;
            if (steps.get(stepIndex).validateAndApply()) {
                stepIndex++;
                showStep();
            }
            else {
                revealInvalidControl();
            }
        });
        button(createType).addEventFilter(ActionEvent.ACTION, event -> {
            if (stepIndex != steps.size() - 1) {
                event.consume();
                return;
            }
            for (int index = 0; index < steps.size(); index++) {
                if (!steps.get(index).validateAndApply()) {
                    event.consume();
                    stepIndex = index;
                    showStep();
                    revealInvalidControl();
                    return;
                }
            }
            completedPlan = draft.complete();
        });
        setResultConverter(type -> type == createType ? completedPlan : null);
        for (NewPlanWizardStep step : this.steps) {
            if (step instanceof ReviewWizardStep review) review.setEditStep(this::navigateTo);
        }
        showStep();
    }

    private void navigateTo(int index) {
        if (index < 0 || index >= steps.size()) throw new IllegalArgumentException("Invalid wizard step.");
        stepIndex = index;
        showStep();
    }

    private Button button(ButtonType type) {

        return (Button) getDialogPane().lookupButton(type);
    }

    private void showStep() {

        steps.get(stepIndex).onEntering();
        stepLabel.setText("Step " + (stepIndex + 1) + " of " + steps.size() + " — " + steps.get(stepIndex).title());
        progress.setProgress((stepIndex + 1.0) / steps.size());
        page.setContent(steps.get(stepIndex).content());
        page.setVvalue(0);
        visible(button(backType), steps.size() > 1);
        button(backType).setDisable(stepIndex == 0);
        visible(button(nextType), stepIndex < steps.size() - 1);
        visible(button(createType), stepIndex == steps.size() - 1);
        button(nextType).setDefaultButton(stepIndex < steps.size() - 1);
        button(createType).setDefaultButton(stepIndex == steps.size() - 1);
    }

    private void revealInvalidControl() {

        javafx.application.Platform.runLater(() -> {
            page.applyCss();
            page.layout();
            var invalid = page.getContent().lookup(":invalid");
            if (invalid != null) {
                var bounds = page.getContent().sceneToLocal(invalid.localToScene(invalid.getBoundsInLocal()));
                double height = page.getContent().getBoundsInLocal().getHeight() - page.getViewportBounds().getHeight();
                if (height > 0) {
                    page.setVvalue(Math.max(0, Math.min(1, (bounds.getMinY() - 20) / height)));
                }
                invalid.requestFocus();
            }
        });
    }

    private static void visible(Button button, boolean visible) {

        button.setVisible(visible);
        button.setManaged(visible);
    }
}
