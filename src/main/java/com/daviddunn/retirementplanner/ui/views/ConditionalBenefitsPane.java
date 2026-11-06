package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefits;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.benefits.ConditionalBenefitPresentation;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Current-plan list transaction; historical definitions are never edited here. */
public final class ConditionalBenefitsPane extends VBox {
    private final TableView<ConditionalBenefitDefinition> table = new TableView<>();
    private final Button add = new Button("Add Conditional Benefit");
    private final Button edit = new Button("Edit");
    private final Button duplicate = new Button("Duplicate");
    private final Button remove = new Button("Remove");
    private final Label notice = label("");
    private final Label count = label("");
    private final Label definition = label("Select a benefit to inspect its complete definition, or choose Edit.");
    private final TitledPane details = new TitledPane("Selected benefit details", definition);
    private RetirementPlan plan;
    private Runnable onChanged;

    public ConditionalBenefitsPane() {
        setId("conditional-benefits-section");
        setSpacing(8);
        setPadding(new Insets(10));
        var heading = label("Conditional Benefits");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        notice.setId("conditional-benefits-notice");
        notice.getStyleClass().add("analysis-stale");
        notice.managedProperty().bind(notice.textProperty().isNotEmpty());
        notice.visibleProperty().bind(notice.managedProperty());
        table.setId("conditional-benefits-table");
        table.setAccessibleText("Configured conditional benefits");
        table.setPrefHeight(190);
        table.setMinHeight(140);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        var empty = label("No conditional benefits are configured. Add recurring income or a one-time payment that begins when a specified date, age, or death event occurs.");
        empty.setMaxWidth(560);
        table.setPlaceholder(empty);
        column("Benefit", ConditionalBenefitDefinition::label, 150);
        column("Payment", ConditionalBenefitPresentation::payment, 140);
        column("Trigger", value -> ConditionalBenefitPresentation.trigger(value.trigger(), plan.getHousehold()), 155);
        column("Recipient", value -> ConditionalBenefitPresentation.person(value.recipient(), plan.getHousehold()), 120);
        column("Timing", ConditionalBenefitPresentation::timing, 140);
        column("Type", value -> ConditionalBenefitPresentation.category(value.category()), 100);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> {
            showDetails(value);
            details.setExpanded(value != null);
        });
        table.setRowFactory(view -> {
            var row = new TableRow<ConditionalBenefitDefinition>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openEditor(row.getItem(), false);
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER && selected() != null) {
                openEditor(selected(), false);
                event.consume();
            }
        });
        add.setId("conditional-benefit-add");
        edit.setId("conditional-benefit-edit");
        duplicate.setId("conditional-benefit-duplicate");
        remove.setId("conditional-benefit-remove");
        add.setOnAction(event -> openEditor(null, false));
        edit.setOnAction(event -> openEditor(selected(), false));
        duplicate.setOnAction(event -> openEditor(selected(), true));
        remove.setOnAction(event -> confirmRemove());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(edit.disableProperty());
        var buttons = new FlowPane(8, 8, add, edit, duplicate, remove);
        details.setId("conditional-benefit-details");
        details.setExpanded(false);
        details.setAnimated(false);
        getChildren().addAll(heading, table, count, buttons, notice, details);
        load(null);
    }

    private void column(String title, Function<ConditionalBenefitDefinition, String> display, double width) {
        var column = new TableColumn<ConditionalBenefitDefinition, String>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display.apply(cell.getValue())));
        column.setCellFactory(value -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null ? null : HelpIcon.createTooltip(item));
                setAccessibleText(empty ? null : item);
            }
        });
        table.getColumns().add(column);
    }

    public void load(RetirementPlan value) {
        var previous = selected();
        plan = value;
        table.getItems().setAll(value == null ? List.of() : value.getConditionalBenefits());
        if (previous != null) table.getItems().stream().filter(item -> item.id().equals(previous.id()))
                .findFirst().ifPresent(item -> table.getSelectionModel().select(item));
        boolean full = value != null && value.getConditionalBenefits().size() >= ConditionalBenefits.MAX_DEFINITIONS;
        add.setDisable(value == null || full);
        duplicate.disableProperty().unbind();
        duplicate.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(add.disableProperty()));
        count.setText(value == null ? "" : value.getConditionalBenefits().size() + " of " + ConditionalBenefits.MAX_DEFINITIONS
                + " benefits configured." + (full ? " Remove a benefit before adding another." : ""));
        notice.setText("");
        if (value != null && !value.getConditionalBenefits().isEmpty()) {
            try { ConditionalBenefitAdmission.requireDeterministicSupported(value); }
            catch (ConditionalBenefitAdmission.UnsupportedExecutionException unsupported) {
                notice.setText("Definitions are saved, but conditional-benefit projections are not yet supported for "
                        + value.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction().displayName() + ".");
            }
        }
        showDetails(selected());
    }

    public void setOnChanged(Runnable callback) {
        onChanged = callback;
    }

    private ConditionalBenefitDefinition selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private void showDetails(ConditionalBenefitDefinition value) {
        definition.setText(value == null || plan == null ? "Select a benefit to inspect its complete definition, or choose Edit."
                : String.join("\n\n", ConditionalBenefitPresentation.details(value, plan.getHousehold())));
    }

    private void openEditor(ConditionalBenefitDefinition original, boolean copy) {
        if (plan == null || original == null && copy) return;
        var target = plan;
        var dialog = new ConditionalBenefitDialog(target, original, copy);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.showAndWait().ifPresent(value -> {
            try { commit(target, copy ? null : original, value); }
            catch (IllegalArgumentException | IllegalStateException invalid) { notice.setText(invalid.getMessage()); }
        });
    }

    /** One whole-list replacement, preserving exact IDs and all unrelated plan data. */
    void commit(RetirementPlan expectedPlan, ConditionalBenefitDefinition original, ConditionalBenefitDefinition value) {
        if (plan != expectedPlan) throw new IllegalStateException("The active plan changed. Reopen the benefit editor.");
        var updated = new ArrayList<>(plan.getConditionalBenefits());
        if (original == null) {
            updated.add(value);
        } else {
            int index = updated.indexOf(original);
            if (index < 0 || !original.id().equals(value.id()))
                throw new IllegalStateException("This benefit changed. Reopen the editor before saving.");
            if (sameDefinition(original, value)) return;
            updated.set(index, value);
        }
        plan.setConditionalBenefits(updated);
        load(plan);
        table.getItems().stream().filter(item -> item.id().equals(value.id())).findFirst()
                .ifPresent(item -> table.getSelectionModel().select(item));
        if (onChanged != null) onChanged.run();
    }

    private static boolean sameDefinition(ConditionalBenefitDefinition left, ConditionalBenefitDefinition right) {
        if (left.grossAmount().compareTo(right.grossAmount()) != 0
                || left.annualEscalationRate().compareTo(right.annualEscalationRate()) != 0) return false;
        return left.equals(new ConditionalBenefitDefinition(right.id(), right.label(), right.category(), right.trigger(),
                right.recipient(), right.paymentPattern(), left.grossAmount(), right.notBefore(), right.endDate(),
                right.coverageStart(), right.coverageEnd(), left.annualEscalationRate(), right.escalationOrigin(),
                right.taxCharacter(), right.destination()));
    }

    private void confirmRemove() {
        var original = selected();
        if (original == null) return;
        var target = plan;
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove Conditional Benefit");
        confirmation.setHeaderText("Remove " + original.label() + "?");
        confirmation.setContentText("This removes the definition from the current plan. Previously completed projections and analyses keep their original definitions.");
        if (getScene() != null) confirmation.initOwner(getScene().getWindow());
        confirmation.showAndWait().filter(response -> response == ButtonType.OK).ifPresent(response -> {
            if (plan != target || !plan.getConditionalBenefits().contains(original)) {
                notice.setText("The current definition changed. Select it again before removing it.");
                return;
            }
            var updated = new ArrayList<>(plan.getConditionalBenefits());
            updated.remove(original);
            plan.setConditionalBenefits(updated);
            load(plan);
            if (onChanged != null) onChanged.run();
        });
    }

    private static Label label(String value) {
        var label = new Label(value);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
