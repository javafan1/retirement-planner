package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.dialogs.NonInvestableAssetDialog;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;

public class NonInvestableAssetsView
        extends BorderPane {

    private final TableView<NonInvestableAsset> table;

    private final Button addButton;
    private final Button editButton;
    private final Button removeButton;

    private RetirementPlan currentPlan;

    private Runnable onAssetsChanged;

    public NonInvestableAssetsView() {

        table =
                new TableView<>();

        table.setRowFactory(tv -> {

            TableRow<NonInvestableAsset> row =
                    new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (event.getClickCount() == 2
                        && !row.isEmpty()) {

                    table.getSelectionModel()
                            .select(row.getItem());

                    onEdit();
                }
            });

            return row;
        });

        createColumns();

        addButton =
                new Button("Add");

        editButton =
                new Button("Edit");

        removeButton =
                new Button("Remove");

        editButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        removeButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        addButton.setOnAction(
                e -> onAdd());

        editButton.setOnAction(
                e -> onEdit());

        removeButton.setOnAction(
                e -> onRemove());

        HBox buttonBar =
                new HBox(10);

        buttonBar.setPadding(
                new Insets(10));

        buttonBar.getChildren().addAll(
                addButton,
                editButton,
                removeButton);

        setCenter(table);
        setBottom(buttonBar);
    }


    // ============================================================
    // Selection
    // ============================================================

    private NonInvestableAsset
    getSelectedAsset() {

        return table
                .getSelectionModel()
                .getSelectedItem();
    }


    // ============================================================
    // Add
    // ============================================================

    private void onAdd() {

        NonInvestableAssetDialog dialog =
                new NonInvestableAssetDialog(
                        null);

        dialog.showAndWait()
                .ifPresent(asset -> {

                    currentPlan
                            .addNonInvestableAsset(
                                    asset);

                    refreshTable();

                    notifyAssetsChanged();
                });
    }


    // ============================================================
    // Edit
    // ============================================================

    private void onEdit() {

        NonInvestableAsset selected =
                getSelectedAsset();

        if (selected == null) {
            return;
        }

        NonInvestableAssetDialog dialog =
                new NonInvestableAssetDialog(
                        selected);

        dialog.showAndWait()
                .ifPresent(updated -> {

                    currentPlan
                            .replaceNonInvestableAsset(
                                    selected,
                                    updated);

                    refreshTable();

                    notifyAssetsChanged();
                });
    }


    // ============================================================
    // Remove
    // ============================================================

    private void onRemove() {

        NonInvestableAsset selected =
                getSelectedAsset();

        if (selected == null) {
            return;
        }

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION);

        confirmation.setTitle(
                "Remove Non-Investable Asset");

        confirmation.setHeaderText(
                "Remove "
                        + selected.getName()
                        + "?");

        confirmation.setContentText(
                "This asset will be removed "
                        + "from the retirement plan.");

        confirmation.showAndWait()
                .filter(
                        response ->
                                response ==
                                        ButtonType.OK)
                .ifPresent(
                        response -> {

                            currentPlan
                                    .removeNonInvestableAsset(
                                            selected);

                            refreshTable();

                            notifyAssetsChanged();
                        });
    }


    // ============================================================
    // Columns
    // ============================================================

    private void createColumns() {

        TableColumn<
                NonInvestableAsset,
                String> nameColumn =
                new TableColumn<>(
                        "Asset");

        nameColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyStringWrapper(
                                cellData
                                        .getValue()
                                        .getName()));

        TableColumn<
                NonInvestableAsset,
                String> valueColumn =
                new TableColumn<>(
                        "Current Value");

        valueColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyStringWrapper(
                                formatMoney(
                                        cellData
                                                .getValue()
                                                .getCurrentValue())));

        TableColumn<
                NonInvestableAsset,
                String> growthColumn =
                new TableColumn<>(
                        "Growth Rate");

        growthColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyStringWrapper(
                                formatPercent(
                                        cellData
                                                .getValue()
                                                .getAnnualGrowthRate())));

        table.getColumns().addAll(
                nameColumn,
                valueColumn,
                growthColumn);

        table.setColumnResizePolicy(
                TableView
                        .CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }


    // ============================================================
    // Formatting
    // ============================================================

    private String formatMoney(
            BigDecimal value) {

        if (value == null) {
            return "";
        }

        return "$"
                + String.format(
                "%,.2f",
                value);
    }

    private String formatPercent(
            BigDecimal rate) {

        if (rate == null) {
            return "";
        }

        return rate
                .multiply(
                        new BigDecimal("100"))
                .stripTrailingZeros()
                .toPlainString()
                + "%";
    }


    // ============================================================
    // Load / Refresh
    // ============================================================

    public void load(
            RetirementPlan plan) {

        currentPlan = plan;

        refreshTable();
    }

    private void refreshTable() {

        table.getItems().clear();

        if (currentPlan == null) {
            return;
        }

        table.getItems().addAll(
                currentPlan
                        .getNonInvestableAssets());
    }


    public void setOnAssetsChanged(
            Runnable onAssetsChanged) {

        this.onAssetsChanged =
                onAssetsChanged;
    }

    // ============================================================
    // Callback
    // ============================================================



    private void notifyAssetsChanged() {

        if (onAssetsChanged != null) {
            onAssetsChanged.run();
        }
    }


    // ============================================================
    // Access
    // ============================================================

    public TableView<NonInvestableAsset>
    getTable() {

        return table;
    }
}