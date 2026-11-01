package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;

/** Confirmation shown only when applying an enabled schedule requires modeled Roth IRA destinations. */
public final class RothDestinationCreationDialog {
    private RothDestinationCreationDialog() { }

    public static boolean confirm(Window owner, List<AccountOwnership> owners) {
        if (owners.isEmpty()) return true;
        Dialog<ButtonType> dialog = create(owner, owners);
        ButtonType create = dialog.getDialogPane().getButtonTypes().getFirst();
        return dialog.showAndWait().filter(create::equals).isPresent();
    }

    public static Dialog<ButtonType> create(Window owner, List<AccountOwnership> owners) {
        Objects.requireNonNull(owners);
        if (owners.isEmpty()) throw new IllegalArgumentException("At least one owner is required.");
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        dialog.setTitle("Create Modeled Roth IRA");
        dialog.setHeaderText("A Roth IRA destination is needed for this conversion schedule.");
        dialog.getDialogPane().getStyleClass().add("roth-destination-confirmation");

        String ownerText = owners.stream().map(RothDestinationCreationDialog::ownerName)
                .reduce((left, right) -> left + " and " + right).orElseThrow();
        Label message = new Label("Create a modeled Roth IRA for " + ownerText + " with an opening balance of $0?\n\n"
                + "This account exists only in your retirement plan model. No real financial account will be opened. "
                + "You can edit or remove it later in Accounts.");
        message.setId("roth-destination-confirmation-message");
        message.setWrapText(true);
        message.setMaxWidth(440);
        message.setAccessibleHelp(message.getText());
        VBox content = new VBox(message);
        content.setPadding(new Insets(8, 0, 4, 0));
        dialog.getDialogPane().setContent(content);

        ButtonType create = new ButtonType("Create Modeled Account", ButtonType.OK.getButtonData());
        ButtonType cancel = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        dialog.getDialogPane().getButtonTypes().setAll(create, cancel);
        Node createButton = dialog.getDialogPane().lookupButton(create);
        createButton.setAccessibleText("Create modeled Roth IRA destination");
        dialog.setResultConverter(button -> button);
        return dialog;
    }

    private static String ownerName(AccountOwnership owner) {
        return switch (owner) {
            case PRIMARY -> "the primary account owner";
            case SPOUSE -> "the spouse";
            case JOINT -> throw new IllegalArgumentException("A modeled Roth IRA requires an individual owner.");
        };
    }
}
