package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import com.daviddunn.retirementplanner.application.branding.ApplicationVersion;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Read-only application metadata. No controller, settings writer or financial service. */
public final class AboutApplicationDialog extends Dialog<Void> {
    public AboutApplicationDialog(Window owner, ApplicationBranding branding) {
        this(owner, branding, ApplicationVersion.current());
    }

    public AboutApplicationDialog(Window owner, ApplicationBranding branding, ApplicationVersion version) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("About " + branding.productName());
        setResizable(true);
        var content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("application-about");
        content.getStylesheets().add(AboutApplicationDialog.class.getResource("/css/application-about.css").toExternalForm());
        BrandingImages.load(branding.logoResource()).ifPresent(image -> {
            var symbol = new ImageView(image);
            symbol.setId("about-logo");
            symbol.setPreserveRatio(true);
            symbol.setFitWidth(160);
            symbol.setFitHeight(120);
            symbol.setAccessibleText(branding.productName() + " logo");
            content.getChildren().add(symbol);
        });
        content.getChildren().addAll(
                label(branding.productName(), "about-product", "about-product"),
                label(branding.tagline(), "about-tagline", "about-tagline"),
                label(version.displaySummary(), "about-version", "about-version"),
                label("Git commit: " + version.commitHash(), "about-detail", "about-commit"));
        version.buildTimestamp().ifPresent(timestamp -> content.getChildren().add(
                label("Built: " + timestamp + " (informational)", "about-detail", "about-timestamp")));
        content.getChildren().add(label(ApplicationBranding.COMPANY_ATTRIBUTION, "about-attribution", "about-attribution"));
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll);
        getDialogPane().setPrefSize(560, 460);
        getDialogPane().setMinWidth(360);
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        setOnShown(event -> {
            ApplicationThemeManager.prepare(getDialogPane().getScene());
            if (owner != null && owner.isShowing()) {
                setX(owner.getX() + (owner.getWidth() - getWidth()) / 2);
                setY(owner.getY() + (owner.getHeight() - getHeight()) / 2);
            }
        });
        ApplicationThemeManager.prepare(getDialogPane().getScene());
    }

    private static Label label(String value, String style, String id) {
        var label = new Label(value);
        label.setId(id);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
