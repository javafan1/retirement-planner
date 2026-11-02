package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class NonInvestableAssetDialog
        extends Dialog<NonInvestableAsset> {

    private final TextField nameField;
    private final TextField valueField;
    private final TextField growthRateField;
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();

    public NonInvestableAssetDialog(
            NonInvestableAsset asset) {

        setTitle(
                asset == null
                        ? "Add Non-Investable Asset"
                        : "Edit Non-Investable Asset");

        setHeaderText(
                asset == null
                        ? "Enter asset information."
                        : "Edit asset information.");

        ButtonType saveButtonType =
                new ButtonType(
                        "Save",
                        ButtonBar.ButtonData.OK_DONE);

        getDialogPane()
                .getButtonTypes()
                .addAll(
                        saveButtonType,
                        ButtonType.CANCEL);

        nameField =
                new TextField();

        valueField =
                new TextField();

        growthRateField =
                new TextField();

        nameField.setPromptText(
                "Asset name");

        valueField.setPromptText(
                "Current value");

        growthRateField.setPromptText(
                "Annual growth rate");

        if (asset != null) {

            nameField.setText(
                    asset.getName());

            valueField.setText(
                    asset.getCurrentValue()
                            .toPlainString());

            growthRateField.setText(
                    asset.getAnnualGrowthRate()
                            .multiply(
                                    new BigDecimal("100"))
                            .stripTrailingZeros()
                            .toPlainString());
        }

        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);

        grid.setPadding(
                new Insets(15));

        Label nameLabel = new Label("Asset Name:");
        grid.add(
                nameLabel,
                0,
                0);

        grid.add(
                nameField,
                1,
                0);

        Label valueLabel = new Label("Current Value:");
        grid.add(
                valueLabel,
                0,
                1);

        grid.add(
                valueField,
                1,
                1);

        Label growthLabel = new Label("Growth Rate (%):");
        grid.add(
                growthLabel,
                0,
                2);

        grid.add(
                growthRateField,
                1,
                2);
        grid.add(RequiredFieldSupport.createLegend(), 0, 3, 2, 1);
        requiredFieldAttachments.add(RequiredFieldSupport.attach(nameLabel, nameField));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(valueLabel, valueField));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(growthLabel, growthRateField));

        getDialogPane()
                .setContent(grid);
        setOnHidden(event -> {
            requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
            requiredFieldAttachments.clear();
        });

        InputHelp.install(valueField, "Current value in dollars of property or another non-investable asset. Included in Total Net Worth, but not available for portfolio withdrawals or included in after-tax investable estate.");
        InputHelp.install(growthRateField, "Annual appreciation percentage for this asset; enter 3 for 3%. Its projected value grows separately from investment accounts and does not fund spending.");
        InputHelp.linkGridLabels(grid);
        Node saveButton =
                getDialogPane()
                        .lookupButton(
                                saveButtonType);

        saveButton.disableProperty()
                .bind(
                        nameField.textProperty()
                                .isEmpty()
                                .or(
                                        valueField
                                                .textProperty()
                                                .isEmpty())
                                .or(
                                        growthRateField
                                                .textProperty()
                                                .isEmpty()));

        setResultConverter(
                buttonType -> {

                    if (buttonType !=
                            saveButtonType) {

                        return null;
                    }

                    try {

                        String name =
                                nameField
                                        .getText()
                                        .trim();

                        BigDecimal value =
                                new BigDecimal(
                                        valueField
                                                .getText()
                                                .trim());

                        BigDecimal growthRate =
                                new BigDecimal(
                                        growthRateField
                                                .getText()
                                                .trim())
                                        .divide(
                                                new BigDecimal("100"));

                        if (name.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "Asset name is required.");
                        }

                        if (value.compareTo(
                                BigDecimal.ZERO) < 0) {

                            throw new IllegalArgumentException(
                                    "Current value cannot be negative.");
                        }

                        return new NonInvestableAsset(
                                name,
                                value,
                                growthRate);

                    } catch (Exception ex) {

                        showValidationError(
                                "Please enter valid asset values.");

                        return null;
                    }
                });
    }

    private void showValidationError(
            String message) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR);

        alert.setTitle(
                "Invalid Asset");

        alert.setHeaderText(
                null);

        alert.setContentText(
                message);

        alert.showAndWait();
    }
}
