package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Only implemented steps are registered; cancellation has no application-side effects. */
public final class NewRetirementPlanWizard extends Dialog<RetirementPlan> {

    private final NewPlanDraft draft;
    private final List<NewPlanWizardStep> steps;
    private final Label stepLabel = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final ScrollPane page = new ScrollPane();
    private final Label validationSummary = new Label();
    private final ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
    private final ButtonType nextType = new ButtonType("Next", ButtonBar.ButtonData.NEXT_FORWARD);
    private final ButtonType createType = new ButtonType("Create Plan", ButtonBar.ButtonData.OK_DONE);
    private int stepIndex;
    private RetirementPlan completedPlan;
    private final List<Button> navigation = new ArrayList<>();
    private final ComboBox<Integer> compact = new ComboBox<>();
    private final VBox sidebar = new VBox(8);
    private final Set<Integer> visited = new HashSet<>();
    private final Map<Integer, Double> scrollPositions = new HashMap<>();
    private final Map<Integer, Node> focusedInputs = new HashMap<>();
    private boolean updatingNavigation;

    public NewRetirementPlanWizard(Window owner) {

        this(owner, new NewPlanDraft());
    }

    private NewRetirementPlanWizard(Window owner, NewPlanDraft draft) {

        this(owner, draft, List.of(new HouseholdWizardStep(draft), new AccountsWizardStep(draft),
                new SocialSecurityWizardStep(draft), new IncomeWizardStep(draft), new ExpensesWizardStep(draft),
                new AssumptionsWizardStep(draft), new ReviewWizardStep(draft)));
    }

    /** Additional implemented pages can be registered without changing navigation. */
    public NewRetirementPlanWizard(Window owner, NewPlanDraft draft, List<NewPlanWizardStep> steps) {

        this.draft = Objects.requireNonNull(draft, "Draft is required.");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("At least one implemented wizard step is required.");
        }
        var keys = EnumSet.noneOf(NewPlanWizardStep.StepKey.class);
        for (var step : this.steps) {
            if (step.key() != NewPlanWizardStep.StepKey.CUSTOM && !keys.add(step.key())) {
                throw new IllegalArgumentException("Duplicate wizard step identity: " + step.key());
            }
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
        progress.setAccessibleText("Navigation position, not plan completeness");
        progress.setFocusTraversable(false);
        page.setFitToWidth(true);
        page.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        page.setMinHeight(0);
        page.setId("wizard-page");
        Label path = new Label("Navigation position, not plan completeness. Previously visited steps can be revisited.");
        path.setId("wizard-step-path");
        path.setWrapText(true);
        path.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        path.getStyleClass().add("wizard-description");
        compact.setId("wizard-compact-navigation");
        compact.setAccessibleText("Previously visited wizard steps");
        compact.setMaxWidth(Double.MAX_VALUE);
        compact.setCellFactory(list -> navigationCell());
        compact.setButtonCell(navigationCell());
        compact.setOnAction(event -> {
            if (!updatingNavigation && compact.getValue() != null) navigateToIndex(compact.getValue());
        });
        sidebar.setId("wizard-sidebar");
        sidebar.getStyleClass().add("wizard-sidebar");
        sidebar.setMinWidth(176);
        sidebar.setPrefWidth(176);
        sidebar.setMaxWidth(176);
        for (int index = 0; index < this.steps.size(); index++) {
            final int destination = index;
            Button item = new Button();
            item.setId("wizard-step-" + this.steps.get(index).key().name().toLowerCase(java.util.Locale.ROOT) + "-" + index);
            item.setWrapText(true);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            item.getStyleClass().add("wizard-navigation-step");
            item.setOnAction(event -> navigateToIndex(destination));
            navigation.add(item);
            sidebar.getChildren().add(item);
        }
        validationSummary.setId("wizard-validation-summary");
        validationSummary.setWrapText(true);
        validationSummary.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        validationSummary.getStyleClass().add("wizard-field-error");
        validationSummary.visibleProperty().bind(validationSummary.textProperty().isNotEmpty());
        validationSummary.managedProperty().bind(validationSummary.visibleProperty());
        VBox topic = new VBox(8, stepLabel, compact, progress, validationSummary, page);
        topic.setMinWidth(0);
        topic.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(page, Priority.ALWAYS);
        HBox body = new HBox(16, sidebar, topic);
        body.setMinHeight(0);
        HBox.setHgrow(topic, Priority.ALWAYS);
        // 176 sidebar + 16 gap + 540 topic + 36 padding; collapse before forms squeeze.
        Runnable responsive = () -> setCompact(body.getWidth() < 732
                || body.getHeight() > 0 && body.getHeight() < sidebar.prefHeight(176));
        body.widthProperty().addListener((o, oldWidth, width) -> responsive.run());
        body.heightProperty().addListener((o, oldHeight, height) -> responsive.run());
        setCompact(true);
        VBox content = new VBox(10, title, description, path, body);
        content.setPadding(new Insets(18));
        content.setMaxWidth(1000);
        VBox.setVgrow(body, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(980, 700);
        getDialogPane().setMinSize(460, 380);
        getDialogPane().getButtonTypes().setAll(backType, nextType, createType, ButtonType.CANCEL);
        setOnShown(event -> {
            Screen screen = owner == null ? Screen.getPrimary() : Screen.getScreensForRectangle(
                    owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()).stream()
                    .findFirst().orElse(Screen.getPrimary());
            var bounds = screen.getVisualBounds();
            var window = getDialogPane().getScene().getWindow();
            window.setWidth(Math.min(980, bounds.getWidth() - 32));
            window.setHeight(Math.min(740, bounds.getHeight() - 32));
            window.setX(Math.max(bounds.getMinX() + 16, Math.min(window.getX(), bounds.getMaxX() - window.getWidth() - 16)));
            window.setY(Math.max(bounds.getMinY() + 16, Math.min(window.getY(), bounds.getMaxY() - window.getHeight() - 16)));
            var footer = getDialogPane().lookup(".button-bar");
            if (footer != null) footer.getStyleClass().add("wizard-footer");
        });
        button(backType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (stepIndex > 0) {
                navigateToIndex(stepIndex - 1);
            }
        });
        button(nextType).addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (stepIndex == steps.size() - 1) return;
            if (steps.get(stepIndex).validateAndApply()) {
                enter(stepIndex + 1);
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

    private void navigateTo(NewPlanWizardStep.StepKey key) {
        int index = java.util.stream.IntStream.range(0, steps.size())
                .filter(i -> steps.get(i).key() == key).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unregistered wizard step."));
        navigateToIndex(index);
    }

    private void navigateToIndex(int index) {
        if (index < 0 || index >= steps.size() || !visited.contains(index)) return;
        enter(index);
    }

    private void enter(int index) {
        scrollPositions.put(stepIndex, page.getVvalue());
        if (page.getScene() != null) {
            Node focused = page.getScene().getFocusOwner();
            for (Node ancestor = focused; ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor == page.getContent()) {
                    focusedInputs.put(stepIndex, focused);
                    break;
                }
            }
        }
        if (steps.get(index).key() == NewPlanWizardStep.StepKey.REVIEW) {
            // Prior navigation may leave raw pending text on any page; never summarize stale data.
            for (int candidate = 0; candidate < steps.size(); candidate++) {
                if (steps.get(candidate).key() == NewPlanWizardStep.StepKey.REVIEW) continue;
                if (!steps.get(candidate).validateAndApply()) {
                    stepIndex = candidate;
                    showStep();
                    revealInvalidControl();
                    return;
                }
            }
        }
        stepIndex = index;
        showStep();
    }

    private void setCompact(boolean narrow) {
        sidebar.setVisible(!narrow);
        sidebar.setManaged(!narrow);
        compact.setVisible(narrow);
        compact.setManaged(narrow);
    }

    private ListCell<Integer> navigationCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Integer index, boolean empty) {
                super.updateItem(index, empty);
                setText(empty || index == null ? null : (index + 1) + ". " + steps.get(index).title()
                        + (index == stepIndex ? " — Current" : " — Previously visited"));
            }
        };
    }

    private Button button(ButtonType type) {

        return (Button) getDialogPane().lookupButton(type);
    }

    private void showStep() {

        visited.add(stepIndex);
        validationSummary.setText("");
        steps.get(stepIndex).onEntering();
        stepLabel.setText("Step " + (stepIndex + 1) + " of " + steps.size() + " — " + steps.get(stepIndex).title());
        progress.setProgress((stepIndex + 1.0) / steps.size());
        page.setContent(steps.get(stepIndex).content());
        page.setVvalue(scrollPositions.getOrDefault(stepIndex, 0.0));
        updatingNavigation = true;
        compact.getItems().setAll(visited.stream().sorted().toList());
        compact.setValue(stepIndex);
        compact.setButtonCell(navigationCell());
        updatingNavigation = false;
        for (int index = 0; index < navigation.size(); index++) {
            String state = index == stepIndex ? "Current" : visited.contains(index) ? "Previously visited" : "Not yet visited";
            var item = navigation.get(index);
            item.setText((index + 1) + ". " + steps.get(index).title() + "\n" + state);
            item.setAccessibleText(item.getText());
            item.setDisable(!visited.contains(index));
            item.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("current"), index == stepIndex);
        }
        stepLabel.setAccessibleText(stepLabel.getText());
        visible(button(backType), steps.size() > 1);
        button(backType).setDisable(stepIndex == 0);
        visible(button(nextType), stepIndex < steps.size() - 1);
        visible(button(createType), stepIndex == steps.size() - 1);
        button(nextType).setDefaultButton(stepIndex < steps.size() - 1);
        button(createType).setDefaultButton(stepIndex == steps.size() - 1);
        var focused = focusedInputs.get(stepIndex);
        if (focused != null) javafx.application.Platform.runLater(() -> {
            if (focused.getScene() == page.getScene() && focused.isVisible() && !focused.isDisabled()) focused.requestFocus();
        });
    }

    private void revealInvalidControl() {

        validationSummary.setText("Please correct the validation issues in " + steps.get(stepIndex).title() + " before continuing.");
        validationSummary.notifyAccessibleAttributeChanged(javafx.scene.AccessibleAttribute.TEXT);
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
