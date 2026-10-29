package com.daviddunn.retirementplanner.ui.introduction;

import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.util.function.Consumer;

/** Owner-modal education only. Durable preference work is delegated to the existing settings service. */
public final class AnalyzerIntroductionDialog extends Dialog<Void> {
    public static final ButtonType CONTINUE = new ButtonType("Continue to Analyzer", ButtonBar.ButtonData.OK_DONE);
    private final CheckBox suppress = new CheckBox("Don't show this introduction again");
    private final Label notice = wrapped("");
    private boolean saving;

    public AnalyzerIntroductionDialog(Window owner, AnalyzerIntroductionContent content, Consumer<Boolean> continued) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(content.title());
        setResizable(true);
        var title = wrapped(content.title()); title.getStyleClass().add("analyzer-introduction-title");
        var body = new VBox(12, wrapped(content.purpose()), wrapped(content.results()), wrapped(content.limitations()));
        body.setPadding(new Insets(8));
        var scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setMinHeight(0);
        var warning = wrapped(AnalyzerIntroductionContent.DURATION);
        warning.setId("analyzer-introduction-duration"); warning.getStyleClass().add("analyzer-introduction-warning");
        suppress.setId("suppress-analyzer-introduction");
        suppress.setWrapText(true);
        suppress.setMinHeight(Region.USE_PREF_SIZE);
        suppress.setAccessibleHelp("Only saved when Continue to Analyzer is selected. Cancel leaves preferences unchanged.");
        notice.setId("analyzer-introduction-notice"); notice.getStyleClass().add("settings-notice");
        var footer = new VBox(10, warning, wrapped(AnalyzerIntroductionContent.TIMING), suppress, notice);
        footer.setPadding(new Insets(12,0,0,0));
        var panel = new BorderPane(scroll); panel.setTop(title); panel.setBottom(footer); panel.setPadding(new Insets(16));
        BorderPane.setMargin(title,new Insets(0,0,12,0));
        getDialogPane().setContent(panel); getDialogPane().setPrefSize(620,450);
        getDialogPane().setMinWidth(420);
        getDialogPane().getButtonTypes().setAll(CONTINUE,ButtonType.CANCEL);
        getDialogPane().lookupButton(CONTINUE).addEventFilter(ActionEvent.ACTION,event -> {
            event.consume(); if (!saving) continued.accept(suppress.isSelected());
        });
        setOnCloseRequest(event -> { if (saving) event.consume(); });
        setOnShown(event -> {
            ApplicationThemeManager.prepare(getDialogPane().getScene());
            if (owner != null && owner.isShowing()) {
                setX(owner.getX()+(owner.getWidth()-getWidth())/2);
                setY(owner.getY()+(owner.getHeight()-getHeight())/2);
            }
        });
        ApplicationThemeManager.prepare(getDialogPane().getScene());
    }

    private static Label wrapped(String text) {
        var label = new Label(text); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
    void saving(boolean value) {
        saving=value; suppress.setDisable(value);
        for (var button:getDialogPane().getButtonTypes()) getDialogPane().lookupButton(button).setDisable(value);
        notice.setText(value ? "Saving introduction preference…" : "");
    }
    void failed() { saving(false); notice.setText("Introduction preference was not saved. Retry Continue, or uncheck the checkbox to continue without saving it."); }
    void dismiss() { saving=false; close(); }
}
