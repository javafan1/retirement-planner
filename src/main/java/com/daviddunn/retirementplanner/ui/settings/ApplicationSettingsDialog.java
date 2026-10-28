package com.daviddunn.retirementplanner.ui.settings;

import com.daviddunn.retirementplanner.application.settings.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.event.ActionEvent;

/** Detached editing session; save acknowledgement is durable, Cancel rolls back preview only. */
public final class ApplicationSettingsDialog extends Dialog<Void> {
    private final ComboBox<ApplicationTheme> theme = new ComboBox<>();
    private final TextField directory = new TextField();
    private final Label notice = new Label();
    private boolean saving;
    private boolean committed;

    public ApplicationSettingsDialog(Window owner, ApplicationSettingsService service, ApplicationThemeManager themes) {
        initOwner(owner); initModality(Modality.WINDOW_MODAL);
        setTitle("Application Settings"); setHeaderText("Application preferences"); setResizable(true);
        var initial = service.snapshot();
        theme.getItems().setAll(ApplicationTheme.values()); theme.setValue(initial.getTheme());
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ApplicationTheme value) { return value == ApplicationTheme.DARK ? "Dark" : "Light"; }
            @Override public ApplicationTheme fromString(String value) { return ApplicationTheme.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        });
        theme.setAccessibleText("Application theme");
        theme.valueProperty().addListener((o, old, next) -> { if (next != null) themes.apply(next); });
        directory.setText(initial.getDefaultPlanDirectory() == null ? "" : initial.getDefaultPlanDirectory());
        directory.setAccessibleText("Default retirement-plan directory");
        var browse = new Button("Browse…"); var clear = new Button("Use System Default");
        clear.setOnAction(e -> directory.clear());
        browse.setOnAction(e -> {
            browse.setDisable(true);
            var draft = service.snapshot();
            try { draft.setDefaultPlanDirectory(directory.getText().trim()); }
            catch (IllegalArgumentException invalid) { browse.setDisable(false); notice.setText("Directory preference is too long."); return; }
            service.background(() -> PlanDirectoryResolver.resolve(draft, null, System.getProperty("user.home")))
                    .whenComplete((resolved, failure) -> Platform.runLater(() -> {
                        browse.setDisable(false); if (!isShowing()) return;
                        if (failure != null) { notice.setText("Directory validation failed. Try another location."); return; }
                        var chooser = new DirectoryChooser(); chooser.setTitle("Default Retirement-Plan Directory");
                        if (resolved.directory() != null) chooser.setInitialDirectory(resolved.directory().toFile());
                        try { var selected = chooser.showDialog(getOwner()); if (selected != null) directory.setText(selected.getAbsolutePath()); }
                        catch (RuntimeException unavailable) { notice.setText("Directory is no longer available. Try another location."); }
                    }));
        });
        var themeLabel = new Label("Theme"); themeLabel.setLabelFor(theme);
        var folderLabel = new Label("Default retirement-plan directory"); folderLabel.setLabelFor(directory);
        var help = new Label("Used for plan Open and Save As. Existing plan Save and PDF/CSV exports keep their locations."); help.setWrapText(true);
        notice.setWrapText(true); notice.getStyleClass().add("settings-notice");
        var body = new VBox(10, themeLabel, theme, folderLabel, directory, new HBox(8, browse, clear), help, notice);
        body.setPadding(new javafx.geometry.Insets(14)); body.setMinWidth(0);
        var scroll = new ScrollPane(body); scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll); getDialogPane().setPrefSize(600, 390);
        var save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume(); if (saving) return;
            saving = true; body.setDisable(true); getDialogPane().lookupButton(save).setDisable(true);
            getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true); notice.setText("Saving application preferences…");
            service.savePreferences(theme.getValue(), directory.getText().trim()).whenComplete((ignored, failure) -> Platform.runLater(() -> {
                saving = false; body.setDisable(false); getDialogPane().lookupButton(save).setDisable(false);
                getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                if (failure != null) { notice.setText("Preferences were not saved. Check the directory, permissions or settings recovery notice, then retry or Cancel."); return; }
                committed = true; themes.apply(service.snapshot().getTheme()); close();
            }));
        });
        setOnCloseRequest(e -> { if (saving) e.consume(); });
        setOnHidden(e -> { if (!committed) themes.apply(service.snapshot().getTheme()); });
        ApplicationThemeManager.prepare(getDialogPane().getScene());
    }
}
