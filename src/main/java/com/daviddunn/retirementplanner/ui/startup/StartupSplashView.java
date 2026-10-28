package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import javafx.geometry.*;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.VBox;
import java.util.Optional;

/** Presentation only. Branding text remains independent of the approved symbol. */
public final class StartupSplashView extends VBox {
    public StartupSplashView(ApplicationBranding branding, Optional<Image> logo) {
        getStyleClass().add("startup-splash");
        getStylesheets().add(StartupSplashView.class.getResource("/css/startup-splash.css").toExternalForm());
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(30, 28, 30, 28));
        setPrefWidth(560);
        setMinHeight(380); // Allow valid longer deployment text to increase natural height.
        logo.ifPresent(image -> {
            var symbol = new ImageView(image);
            symbol.setId("branding-logo");
            symbol.setPreserveRatio(true);
            symbol.setFitWidth(200);
            symbol.setFitHeight(150);
            symbol.setAccessibleText("Aledrix Logo 5");
            getChildren().add(symbol);
        });
        var product = label(branding.productName(), "splash-product", "branding-product");
        var tagline = label(branding.tagline(), "splash-tagline", "branding-tagline");
        var activity = label("Initializing…", "splash-activity", "startup-activity");
        getChildren().addAll(product, tagline, activity);
    }

    private Label label(String text, String style, String id) {
        var label = new Label(text);
        label.setId(id);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
