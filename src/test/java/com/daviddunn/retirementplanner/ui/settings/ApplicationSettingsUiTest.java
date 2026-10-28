package com.daviddunn.retirementplanner.ui.settings;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationSettingsUiTest {
    @TempDir Path directory;
    @BeforeAll static void start() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException running) { }
    }
    static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
    static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(target));
    }

    @Test void previewCancelAndDurableSaveUseDetachedDraft() throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("settings.json")))) {
            fx(() -> {
                var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
                var owner = new Stage(); owner.setScene(new Scene(new VBox(), 300, 150)); owner.show();
                var dialog = new ApplicationSettingsDialog(owner, service, manager); dialog.show();
                field(dialog, "theme", ComboBox.class).setValue(ApplicationTheme.DARK);
                assertEquals(ApplicationTheme.DARK, manager.theme()); assertEquals(ApplicationTheme.LIGHT, service.snapshot().getTheme());
                field(dialog, "directory", TextField.class).setText("unaccepted");
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertEquals(ApplicationTheme.LIGHT, manager.theme()); assertNull(service.snapshot().getDefaultPlanDirectory());
                owner.close(); manager.close(); return null;
            });
            var dialog = fx(() -> {
                var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
                var d = new ApplicationSettingsDialog(null, service, manager); d.show();
                field(d, "theme", ComboBox.class).setValue(ApplicationTheme.DARK);
                field(d, "directory", TextField.class).setText(directory.toString());
                ((Button) d.getDialogPane().lookupButton(d.getDialogPane().getButtonTypes().getFirst())).fire(); return d;
            });
            for (int i = 0; i < 300 && fx(dialog::isShowing); i++) Thread.sleep(20);
            assertFalse(fx(dialog::isShowing));
            assertEquals(ApplicationTheme.DARK, new JsonApplicationSettingsRepository(directory.resolve("settings.json")).load().getTheme());
            fx(() -> { ApplicationThemeManager.start(ApplicationTheme.LIGHT).close(); return null; });
        }
    }

    @Test void invalidSaveRetainsDraftAndCloseRollsBackPreview() throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("settings.json")))) {
            var dialog = fx(() -> {
                var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
                var d = new ApplicationSettingsDialog(null, service, manager); d.show();
                field(d, "theme", ComboBox.class).setValue(ApplicationTheme.DARK);
                field(d, "directory", TextField.class).setText(directory.resolve("missing").toString());
                ((Button) d.getDialogPane().lookupButton(d.getDialogPane().getButtonTypes().getFirst())).fire(); return d;
            });
            for (int i = 0; i < 300 && !fx(() -> field(dialog, "notice", Label.class).getText().contains("not saved")); i++) Thread.sleep(20);
            fx(() -> {
                assertTrue(dialog.isShowing()); assertEquals(directory.resolve("missing").toString(), field(dialog, "directory", TextField.class).getText());
                dialog.close(); ApplicationThemeManager.start(ApplicationTheme.LIGHT).close(); return null;
            });
            assertEquals(ApplicationTheme.LIGHT, service.snapshot().getTheme()); assertFalse(Files.exists(directory.resolve("settings.json")));
        }
    }

    @Test void existingNewPopupAndReplacedRootsFollowThemeWithoutDuplicateSheets() throws Exception {
        fx(() -> {
            var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            var popup = new ContextMenu(new MenuItem("Popup choice"));
            var button = new Button("Menu"); var stage = new Stage(); stage.setScene(new Scene(new VBox(button), 300, 160)); stage.show();
            manager.apply(ApplicationTheme.DARK);
            popup.show(button, javafx.geometry.Side.BOTTOM, 0, 0);
            for (var window : javafx.stage.Window.getWindows()) {
                assertTrue(window.getScene().getRoot().getStyleClass().contains("app-theme"));
                assertEquals(1, window.getScene().getRoot().getStylesheets().stream().filter(s -> s.contains("theme-dark.css")).count());
            }
            manager.apply(ApplicationTheme.DARK); manager.apply(ApplicationTheme.LIGHT); manager.apply(ApplicationTheme.DARK);
            var old = stage.getScene().getRoot(); stage.getScene().setRoot(new VBox(new Label("Replacement")));
            assertFalse(old.getStyleClass().contains("app-theme"));
            assertEquals(1, stage.getScene().getRoot().getStylesheets().stream().filter(s -> s.contains("theme-dark.css")).count());
            popup.hide(); stage.close(); stage.show();
            var tooltip = new Tooltip("Theme-aware tooltip"); tooltip.show(stage, 100, 100);
            assertTrue(tooltip.getScene().getRoot().getStyleClass().contains("app-theme"));
            tooltip.getScene().getRoot().applyCss(); tooltip.getScene().getRoot().layout();
            Files.createDirectories(Path.of("target/phase13b-previews"));
            save(tooltip.getScene().snapshot(null), "tooltip-dark.png"); tooltip.hide();
            assertTrue(stage.getScene().getRoot().getStyleClass().contains("app-theme"));
            stage.close(); manager.close(); assertFalse(stage.getScene().getRoot().getStyleClass().contains("app-theme")); return null;
        });
    }

    @Test void darkHoverAndWizardCurrentStepKeepTextContrast() throws Exception {
        fx(() -> {
            var manager = ApplicationThemeManager.start(ApplicationTheme.DARK);
            var action = new Button("View Baseline Details"); action.getStyleClass().add("results-action-button");
            var current = new Button("Household"); current.getStyleClass().add("wizard-navigation-step");
            var wizard = new VBox(current); wizard.getStyleClass().add("new-plan-wizard");
            var root = new VBox(10, action, wizard);
            var icon = new org.kordamp.ikonli.javafx.FontIcon(); icon.getStyleClass().add("metric-icon"); root.getChildren().add(icon);
            for (var color : java.util.List.of("blue", "green", "purple", "orange")) {
                var metric = new Label("Financial metric " + color); metric.getStyleClass().add("metric-" + color); root.getChildren().add(metric);
            }
            root.getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
            root.getStylesheets().add(getClass().getResource("/css/new-plan-wizard.css").toExternalForm());
            var stage = new Stage(); stage.setScene(new Scene(root, 600, 250)); stage.show();
            try {
                action.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                current.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("current"), true);
                root.applyCss(); root.layout();
                for (var button : java.util.List.of(action, current)) {
                    assertTrue(((javafx.scene.paint.Color) button.getTextFill()).getBrightness() > 0.8);
                    assertTrue(button.getBackground().getFills().stream().allMatch(fill -> ((javafx.scene.paint.Color) fill.getFill()).getBrightness() < 0.6));
                }
                for (var node : root.getChildren()) if (node instanceof Label metric) assertTrue(((javafx.scene.paint.Color) metric.getTextFill()).getBrightness() > 0.8);
                assertTrue(((javafx.scene.paint.Color) icon.getIconColor()).getBrightness() > 0.8);
            } finally { stage.close(); manager.close(); }
            return null;
        });
    }

    @Test void settingsDoNotApplyPendingPlanDraftsOrChangeSavedPlanAndSaveAsKeepsPreferences() throws Exception {
        var source = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(
                new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("settings.json")),
                new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore(
                        new com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository(directory.resolve("analysis"))));
        try {
            var window = fx(() -> {
                var fixture = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
                fixture.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1960, 1, 1));
                fixture.getHousehold().getSpouse().setBirthDate(java.time.LocalDate.of(1962, 1, 1));
                fixture.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                        "Test funding", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, new java.math.BigDecimal("10000000")));
                var a = fixture.getPlanningAssumptions();
                fixture.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                        a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(),
                        3, java.time.LocalDate.of(2026, 1, 1)));
                source.newPlan(fixture);
                source.getCurrentProjection();
                var w = new com.daviddunn.retirementplanner.ui.MainWindow(source);
                source.getCurrentProjection();
                var refresh = w.getClass().getDeclaredMethod("refreshProjectionViews", boolean.class, boolean.class);
                refresh.setAccessible(true); refresh.invoke(w, false, true);
                var stage = new Stage(); w.setStage(stage); stage.setScene(w.createScene()); stage.show();
                source.saveAs(directory.resolve("plan.json")); return w;
            });
            var plan = source.getCurrentPlan(); var accepted = source.getAcceptedProjectionSnapshot();
            assertNotNull(accepted);
            var before = Files.readAllBytes(directory.resolve("plan.json"));
            var revision = source.getSourcePlanRevision(); var dirty = source.isModified();
            fx(() -> {
                var inputs = field(window, "assumptionsView", com.daviddunn.retirementplanner.ui.views.AssumptionsView.class);
                field(inputs, "inflationRateField", TextField.class).setText("invalid pending draft");
                var themes = field(window, "themes", ApplicationThemeManager.class);
                var stage = field(window, "stage", Stage.class);
                var root = stage.getScene().getRoot();
                Files.createDirectories(Path.of("target/phase13b-previews"));
                for (var theme : ApplicationTheme.values()) for (int width : new int[] {1180, 900}) {
                    stage.getScene().setRoot(new javafx.scene.layout.Pane());
                    stage.setScene(new Scene(root, width, width == 900 ? 650 : 820));
                    themes.apply(theme); root.applyCss(); root.layout();
                    save(root.snapshot(null, null), "main-" + width + "-" + theme + ".png");
                }
                themes.apply(ApplicationTheme.DARK);
                var dialog = new ApplicationSettingsDialog(field(window, "stage", Stage.class), source.getSettingsService(), themes);
                dialog.show(); dialog.close();
                assertEquals("invalid pending draft", field(inputs, "inflationRateField", TextField.class).getText());
                assertEquals(revision, source.getSourcePlanRevision()); assertEquals(dirty, source.isModified());
                assertSame(plan, source.getCurrentPlan()); assertSame(accepted, source.getAcceptedProjectionSnapshot());
                return null;
            });
            var preferred = Files.createDirectory(directory.resolve("preferred"));
            source.getSettingsService().savePreferences(ApplicationTheme.DARK, preferred.toString()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertArrayEquals(before, Files.readAllBytes(directory.resolve("plan.json")));
            fx(() -> {
                var chooser = new javafx.stage.FileChooser();
                var prepare = window.getClass().getDeclaredMethod("preparePlanChooser", javafx.stage.FileChooser.class); prepare.setAccessible(true);
                assertEquals(true, prepare.invoke(window, chooser)); assertEquals(preferred.toFile(), chooser.getInitialDirectory());
                return null;
            });
            Files.delete(preferred);
            fx(() -> {
                var chooser = new javafx.stage.FileChooser();
                var prepare = window.getClass().getDeclaredMethod("preparePlanChooser", javafx.stage.FileChooser.class); prepare.setAccessible(true);
                assertEquals(true, prepare.invoke(window, chooser)); assertEquals(directory.toFile(), chooser.getInitialDirectory());
                assertTrue(field(window, "statusLabel", Label.class).getText().contains("unavailable"));
                assertEquals(directory.resolve("plan.json"), source.getCurrentFile()); return null;
            });
            var identity = source.getAnalysisSessionId();
            fx(() -> { source.saveAs(directory.resolve("copy.json")); return null; });
            assertNotEquals(identity, source.getAnalysisSessionId()); assertArrayEquals(before, Files.readAllBytes(directory.resolve("plan.json")));
            assertEquals(ApplicationTheme.DARK, source.getSettingsService().snapshot().getTheme());
            // A bounded worker failure must preserve native chooser access, not silently stop Open/Save As.
            source.getSettingsService().close();
            fx(() -> {
                var chooser = new javafx.stage.FileChooser();
                var prepare = window.getClass().getDeclaredMethod("preparePlanChooser", javafx.stage.FileChooser.class); prepare.setAccessible(true);
                assertEquals(true, prepare.invoke(window, chooser)); assertNull(chooser.getInitialDirectory());
                assertTrue(field(window, "statusLabel", Label.class).getText().contains("native file chooser")); return null;
            });
            fx(() -> { field(window, "stage", Stage.class).close(); window.closeApplicationPreferences(); return null; });
        } finally {
            source.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            source.getSettingsService().close(); source.getAnalysisParameters().shutdown();
            source.getAnalysisResults().shutdownIfIdle();
        }
    }

    @ParameterizedTest @CsvSource({"1180,820,LIGHT", "900,650,LIGHT", "1180,820,DARK", "900,650,DARK"})
    void renderedSettingsAndControlsHaveThemeContrast(int width, int height, ApplicationTheme value) throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("settings.json")))) {
            fx(() -> {
                var manager = ApplicationThemeManager.start(value);
                var controls = new VBox(12, new Label("Application Settings — theme preview"), new TextField("Retirement plan directory"),
                        new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Light", "Dark")), new DatePicker(),
                        new Button("Open Retirement Plan"), new CheckBox("Show changed values only"), new ListView<>(javafx.collections.FXCollections.observableArrayList("Current projection", "Baseline projection")));
                controls.setStyle("-fx-padding:20;");
                var stage = new Stage(); stage.setScene(new Scene(controls, width, height)); stage.show();
                controls.applyCss(); controls.layout();
                var label = (Label) controls.getChildren().getFirst();
                var color = (javafx.scene.paint.Color) label.getTextFill();
                if (value == ApplicationTheme.DARK) assertTrue(color.getBrightness() > 0.8); else assertTrue(color.getBrightness() < 0.4);
                Files.createDirectories(Path.of("target/phase13b-previews"));
                save(controls.snapshot(null, null), "controls-" + width + "-" + value + ".png");
                var dialog = new ApplicationSettingsDialog(stage, service, manager); dialog.show();
                // The saved setting is Light; select the requested preview explicitly.
                field(dialog, "theme", ComboBox.class).setValue(value);
                dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                save(dialog.getDialogPane().snapshot(null, null), "settings-" + width + "-" + value + ".png");
                dialog.close(); stage.close(); manager.close(); return null;
            });
        }
    }

    static void save(javafx.scene.image.WritableImage image, String name) throws Exception {
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(bitmap, "png", Path.of("target/phase13b-previews").resolve(name).toFile());
    }
}
