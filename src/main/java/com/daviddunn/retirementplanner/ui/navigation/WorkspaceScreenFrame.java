package com.daviddunn.retirementplanner.ui.navigation;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;

/** Hosts one retained workspace view with a consistent route back to Results Summary. */
public final class WorkspaceScreenFrame extends BorderPane {
    private final Node content;
    private final Button closeButton;

    public WorkspaceScreenFrame(String title, String viewId, Node content, Runnable onClose) {
        this.content = Objects.requireNonNull(content);
        Objects.requireNonNull(title);
        Objects.requireNonNull(viewId);
        Objects.requireNonNull(onClose);

        getStyleClass().add("workspace-screen-frame");
        setMinSize(0, 0);

        Label heading = new Label(title);
        heading.getStyleClass().add("workspace-screen-title");
        heading.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        FontIcon icon = new FontIcon(FontAwesomeSolid.TIMES);
        icon.setMouseTransparent(true);
        closeButton = new Button("Close", icon);
        closeButton.setId("close-workspace-" + viewId);
        closeButton.setAccessibleText("Close");
        closeButton.setAccessibleHelp("Return to Results Summary");
        closeButton.setTooltip(new Tooltip("Return to Results Summary"));
        closeButton.setFocusTraversable(true);
        closeButton.getStyleClass().add("workspace-close-button");
        closeButton.setOnAction(event -> onClose.run());

        HBox header = new HBox(12, heading, spacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 16, 8, 16));
        header.getStyleClass().add("workspace-screen-header");

        setTop(header);
        setCenter(content);
    }

    public Node getContent() {
        return content;
    }

    public Button getCloseButton() {
        return closeButton;
    }
}
