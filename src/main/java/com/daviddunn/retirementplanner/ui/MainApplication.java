package com.daviddunn.retirementplanner.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import com.daviddunn.retirementplanner.application.settings.ApplicationSettingsService;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.startup.*;

public class MainApplication extends Application {
    private MainWindow window;
    private ApplicationBranding branding;
    private JsonApplicationSettingsRepository settingsRepository;
    private ApplicationSettingsService settings;
    private ApplicationThemeManager themes;
    private ApplicationController controller;
    private ApplicationStartupCoordinator startup;
    private Stage splash;
    private javafx.animation.AnimationTimer firstPulse;
    private java.util.Optional<javafx.scene.image.Image> logo = java.util.Optional.empty();
    private java.util.Optional<javafx.scene.image.Image> icon = java.util.Optional.empty();
    private boolean stopped;
    private boolean failed;

    @Override public void init() {
        var loaded = new ApplicationBrandingLoader().load();
        branding = loaded.branding();
        loaded.warnings().forEach(MainApplication::warning);
        settingsRepository = new JsonApplicationSettingsRepository();
        settings = new ApplicationSettingsService(settingsRepository);
        logo = BrandingImages.load(branding.logoResource());
        icon = BrandingImages.load(BrandingImages.ICON);
        if (logo.isEmpty()) warning("Branding logo is unavailable; startup will use text only.");
    }

    private static void warning(String message) {
        System.getLogger(MainApplication.class.getName()).log(System.Logger.Level.WARNING, message);
    }

    private static final com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator
            ANALYSIS_JOBS = new com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator();

    public static com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator analysisJobs() {
        return ANALYSIS_JOBS;
    }

    @Override
    public void stop() {
        if (stopped) return;
        stopped = true;
        if (firstPulse != null) firstPulse.stop();
        if (startup != null) startup.close();
        if (splash != null) { splash.hide(); splash.setScene(null); splash = null; }
        if (controller != null) controller.getAnalysisParameters().shutdown();
        if (settings != null) settings.close();
        if (themes != null) themes.close();
        ANALYSIS_JOBS.close();
        com.daviddunn.retirementplanner.app.socialsecurity.results.ApplicationAnalysisResults.store().shutdownIfIdle();
    }

    @Override
    public void start(Stage stage) {
        themes = ApplicationThemeManager.start(settings.snapshot().getTheme());
        startup = new ApplicationStartupCoordinator(javafx.application.Platform::runLater);
        splash = new Stage(javafx.stage.StageStyle.UNDECORATED);
        splash.setTitle(branding.productName());
        icon.ifPresent(image -> { stage.getIcons().add(image); splash.getIcons().add(image); });
        var scene = new Scene(new StartupSplashView(branding, logo));
        ApplicationThemeManager.prepare(scene);
        splash.setScene(scene);
        splash.setOnCloseRequest(event -> { stop(); javafx.application.Platform.exit(); });
        splash.show();
        // Yield a real pulse, not a minimum-duration timer or fabricated progress animation.
        firstPulse = new javafx.animation.AnimationTimer() {
            @Override public void handle(long now) {
                stop();
                javafx.application.Platform.runLater(() -> beginStartup(stage));
            }
        };
        firstPulse.start();
    }

    private void beginStartup(Stage stage) {
        if (stopped || failed) return;
        try {
            // Construction stays on FX; no views subscribe until exclusive preparation finishes.
            controller = new ApplicationController(new JsonRetirementPlanRepository(), settingsRepository,
                    com.daviddunn.retirementplanner.app.socialsecurity.results.ApplicationAnalysisResults.store(), settings);
            startup.start(() -> {
                        // ResultsSummary always constructs this exporter. Its static font discovery
                        // is real startup I/O (especially on first run), not financial/PDF rendering.
                        new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter();
                        return controller.prepareStartup();
                    },
                    projectionFailure -> showMain(stage, projectionFailure), failure -> startupFailed(stage));
        } catch (RuntimeException failure) {
            startupFailed(stage);
        }
    }

    private void showMain(Stage stage, Exception projectionFailure) {
        if (stopped || failed) return;
        try {
            window = new MainWindow(controller, branding, themes, projectionFailure);
            window.setStage(stage);
            stage.setScene(window.createScene());
            stage.show(); // Keep a window alive throughout the handoff; never request focus repeatedly.
            if (splash != null) { splash.hide(); splash.setScene(null); splash = null; }
            logo = java.util.Optional.empty();
            javafx.application.Platform.runLater(() -> {
                if (!stopped && stage.isShowing()) window.showStartupPersistenceNotices();
            });
        } catch (RuntimeException failure) {
            startupFailed(stage);
        }
    }

    private void startupFailed(Stage stage) {
        if (stopped || failed) return;
        failed = true;
        if (firstPulse != null) firstPulse.stop();
        if (startup != null) startup.close();
        warning("Application initialization failed; existing plan files were preserved.");
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                "The application could not finish initialization. Existing plan files were preserved. Close and restart the application.",
                javafx.scene.control.ButtonType.CLOSE);
        alert.setTitle(branding.applicationTitle());
        alert.setHeaderText("Unable to start the application");
        if (stage.isShowing()) alert.initOwner(stage);
        alert.setOnHidden(event -> { stage.hide(); stop(); javafx.application.Platform.exit(); });
        alert.show();
        if (splash != null) { splash.hide(); splash.setScene(null); splash = null; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
