package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Dialog-local containment only. Choices retain their original workspace nodes and callbacks. */
final class AnalyzerNavigationView extends BorderPane {
    record Choice(SocialSecurityAnalyzerJobController.Mode mode, String title, String description, Node workspace) {
        Choice {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(title);
            Objects.requireNonNull(description);
            Objects.requireNonNull(workspace);
        }
        @Override public String toString() { return title; }
    }

    private final List<Choice> choices;
    private final ComboBox<Choice> compact = new ComboBox<>();
    private final VBox sidebar = new VBox(8);
    private final VBox workspace = new VBox();
    private final ScrollPane scroll;
    private final Label description = new Label();
    private final Label activity = new Label();
    private final EnumMap<SocialSecurityAnalyzerJobController.Mode, ToggleButton> buttons =
            new EnumMap<>(SocialSecurityAnalyzerJobController.Mode.class);
    private final EnumMap<SocialSecurityAnalyzerJobController.Mode, Double> positions =
            new EnumMap<>(SocialSecurityAnalyzerJobController.Mode.class);
    private Choice selected;
    private java.util.function.Consumer<SocialSecurityAnalyzerJobController.Mode> selectedAction = mode -> { };

    AnalyzerNavigationView(Node header, List<Choice> choices) {
        this.choices = List.copyOf(choices);
        if (this.choices.isEmpty() || this.choices.stream().map(Choice::mode).distinct().count() != this.choices.size()) {
            throw new IllegalArgumentException("Analysis choices must be nonempty and unique.");
        }
        setMinSize(0, 0);
        workspace.setMinWidth(0);
        VBox body = header == null ? new VBox(workspace) : new VBox(header, workspace);
        body.setMinWidth(0);
        scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        setCenter(scroll);

        compact.getItems().setAll(this.choices);
        compact.setMaxWidth(Double.MAX_VALUE);
        compact.setAccessibleText("Analysis type");
        compact.setAccessibleHelp("Choose an analysis. Selection does not run or cancel analysis.");
        com.daviddunn.retirementplanner.ui.controls.InputHelp.install(compact,
                "Choose an analysis workspace. Selecting does not run or cancel an analysis; pending inputs and completed results are retained.");
        compact.valueProperty().addListener((o, before, after) -> { if (after != null) select(after.mode()); });
        description.setWrapText(true);
        description.setMinHeight(Region.USE_PREF_SIZE);
        activity.setWrapText(true);
        activity.setAccessibleText("Active analysis progress");
        activity.visibleProperty().bind(activity.textProperty().isNotEmpty());
        activity.managedProperty().bind(activity.visibleProperty());
        VBox top = new VBox(5, compact, description, activity);
        top.setPadding(new Insets(10));
        setTop(top);

        ToggleGroup group = new ToggleGroup();
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(260);
        sidebar.setMinWidth(260);
        sidebar.setMaxWidth(260);
        sidebar.setAccessibleText("Analysis choices");
        for (Choice choice : this.choices) {
            ToggleButton button = new ToggleButton(choice.title() + "\n" + choice.description());
            button.setWrapText(true);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(Region.USE_PREF_SIZE);
            button.setToggleGroup(group);
            button.setAccessibleText(choice.title());
            button.setAccessibleHelp(choice.description() + " Selecting does not run analysis.");
            button.setOnAction(event -> select(choice.mode()));
            button.selectedProperty().addListener((o, before, after) -> button.setStyle(after
                    ? "-fx-border-color: #2466af; -fx-border-width: 2; -fx-font-weight: bold;" : ""));
            button.setOnKeyPressed(event -> {
                int index = this.choices.indexOf(selected);
                int target = switch (event.getCode()) {
                    case UP -> Math.max(0, index - 1);
                    case DOWN -> Math.min(this.choices.size() - 1, index + 1);
                    case HOME -> 0;
                    case END -> this.choices.size() - 1;
                    default -> -1;
                };
                if (target >= 0) {
                    select(this.choices.get(target).mode());
                    buttons.get(selected.mode()).requestFocus();
                    event.consume();
                }
            });
            buttons.put(choice.mode(), button);
            sidebar.getChildren().add(button);
        }
        widthProperty().addListener((o, before, after) -> updateLayout());
        select(this.choices.getFirst().mode());
        updateLayout();
    }

    List<Choice> choices() { return choices; }
    Choice selected() { return selected; }
    boolean isCompact() { return compact.isManaged(); }

    void select(SocialSecurityAnalyzerJobController.Mode mode) {
        Choice choice = choices.stream().filter(item -> item.mode() == mode).findFirst().orElseThrow();
        boolean changed = selected != choice;
        if (changed) {
            if (selected != null) positions.put(selected.mode(), scroll.getVvalue());
            selected = choice;
            workspace.getChildren().setAll(choice.workspace());
            scroll.setVvalue(positions.getOrDefault(mode, 0.0));
        }
        if (compact.getValue() != choice) compact.setValue(choice);
        buttons.get(mode).setSelected(true);
        description.setText(choice.title() + " — " + choice.description());
        description.setAccessibleText("Selected analysis: " + description.getText());
        if (changed) selectedAction.accept(mode);
    }

    void onSelected(java.util.function.Consumer<SocialSecurityAnalyzerJobController.Mode> action) {
        selectedAction = Objects.requireNonNull(action);
        selectedAction.accept(selected.mode());
    }

    void addExecutionStatus(Node status) {
        ((VBox) getTop()).getChildren().add(status);
    }

    void focusSelector() {
        if (isCompact()) compact.requestFocus(); else buttons.get(selected.mode()).requestFocus();
    }

    void showActivity(SocialSecurityAnalyzerJobController.Mode mode, String status) {
        activity.setText(mode == null ? "" : choices.stream().filter(choice -> choice.mode() == mode)
                .map(choice -> choice.title() + " — " + status).findFirst().orElse(status));
    }

    private void updateLayout() {
        // Reserve the existing dialog's 900-logical-unit usable workspace plus measured sidebar width.
        // Result tables keep their existing horizontal scrolling; no columns are resized here.
        boolean wide = getWidth() >= sidebar.prefWidth(-1) + 900 + snappedLeftInset() + snappedRightInset();
        Node focus = getScene() == null ? null : getScene().getFocusOwner();
        boolean navigationFocused = focus == compact || buttons.containsValue(focus);
        compact.setVisible(!wide);
        compact.setManaged(!wide);
        if (wide && getLeft() != sidebar) setLeft(sidebar);
        if (!wide && getLeft() != null) setLeft(null);
        if (navigationFocused) focusSelector();
    }
}
