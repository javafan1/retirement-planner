package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import com.daviddunn.retirementplanner.ui.views.ResultsView;
import com.daviddunn.retirementplanner.ui.navigation.WorkspaceScreenFrame;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.field;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.fx;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.startFx;
import static org.junit.jupiter.api.Assertions.*;

class MainWindowViewNavigationTest {
    private static final Map<String, String> LABELS = Map.of(
            "results-summary", "Results Summary",
            "household", "Household",
            "accounts", "Accounts",
            "income", "Income",
            "expenses", "Expenses",
            "non-investable-assets", "Non-Investible Assets",
            "assumptions", "Assumptions",
            "roth-conversions", "Roth Conversions");

    @BeforeAll static void initializeJavaFx() throws Exception { startFx(); }

    @Test
    void viewMenuHasApprovedOrderAndKeepsExactlyOneExistingWorkspaceViewVisible() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            MenuBar menuBar = MainWindowTestSupport.menuBar(root);
            assertTrue(root.getTop() instanceof HBox, "Menus and Settings share the horizontal top row.");
            Button settings = (Button) root.lookup("#application-settings-button");
            assertNotNull(settings);
            assertEquals("Settings", settings.getAccessibleText());
            assertEquals("Settings", settings.getTooltip().getText());
            assertTrue(settings.isFocusTraversable());
            assertNotNull(((org.kordamp.ikonli.javafx.FontIcon) settings.getGraphic()).getIconCode());
            assertFalse(menuBar.getMenus().getFirst().getItems().stream()
                    .anyMatch(item -> item.getText() != null && item.getText().contains("Settings")));
            assertEquals(List.of("File", "View", "Analysis", "Help"),
                    menuBar.getMenus().stream().map(Menu::getText).toList());
            Menu viewMenu = menuBar.getMenus().get(1);
            assertEquals(List.of("Results Summary", "|", "Household", "Accounts", "Income",
                            "Expenses", "Non-Investible Assets", "Assumptions", "Roth Conversions"),
                    viewMenu.getItems().stream().map(item -> item instanceof SeparatorMenuItem ? "|" : item.getText()).toList());
            assertEquals(8, viewMenu.getItems().stream().filter(RadioMenuItem.class::isInstance).count());
            assertTrue(viewMenu.getItems().stream().filter(item -> !(item instanceof SeparatorMenuItem))
                    .allMatch(RadioMenuItem.class::isInstance));
            assertEquals(List.of("Social Security Strategy Analyzer...", "Monte Carlo Retirement Analysis...",
                            "Monte Carlo Strategy Comparison..."), menuBar.getMenus().get(2).getItems().stream()
                    .filter(item -> !(item instanceof SeparatorMenuItem))
                    .map(MenuItem::getText).filter(text -> text.endsWith("...")).toList());

            StackPane host = (StackPane) root.getCenter();
            assertEquals(8, host.getChildren().size());
            ResultsSummaryView summary = field(window, "resultsSummaryView");
            Node keyAssumptions = summary.getRight();
            assertNotNull(keyAssumptions, "Summary must retain its right-hand Key Assumptions column.");
            assertSame(summary, host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
            assertEquals("results-summary", field(window, "selectedWorkspaceId"));

            ApplicationController controller = field(window, "controller");
            Object projectionBefore = controller.peekCurrentProjection();
            long revisionBefore = controller.getSourcePlanRevision();
            boolean dirtyBefore = controller.isModified();
            for (var entry : LABELS.entrySet()) {
                RadioMenuItem item = viewMenu.getItems().stream().filter(RadioMenuItem.class::isInstance)
                        .map(RadioMenuItem.class::cast).filter(candidate -> candidate.getId().equals("view-" + entry.getKey()))
                        .findFirst().orElseThrow();
                item.fire();
                assertEquals(entry.getKey(), field(window, "selectedWorkspaceId"));
                assertTrue(item.isSelected());
                assertEquals(1, host.getChildren().stream().filter(Node::isVisible).count());
                assertEquals(1, host.getChildren().stream().filter(Node::isManaged).count());
                Node visible = host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow();
                Node content = (Node) ((Map<?, ?>) field(window, "workspaceViews")).get(entry.getKey());
                if (visible instanceof WorkspaceScreenFrame frame) assertSame(content, frame.getContent());
                else assertSame(content, visible);
                assertEquals(8, host.getChildren().size(), "Navigation must reuse the original view instances.");
            }
            viewMenu.getItems().stream().filter(RadioMenuItem.class::isInstance).map(RadioMenuItem.class::cast)
                    .filter(item -> item.getId().equals("view-results-summary")).findFirst().orElseThrow().fire();
            assertSame(keyAssumptions, summary.getRight());
            assertSame(summary, host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
            assertSame(projectionBefore, controller.peekCurrentProjection(), "Navigation must not replace or recalculate projections.");
            assertEquals(revisionBefore, controller.getSourcePlanRevision());
            assertEquals(dirtyBefore, controller.isModified());
            window.closeApplicationPreferences();
        });
    }

    @Test
    void settingsGearOpensExistingDialogAndStaysRightAligned() throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            MainWindow window = new MainWindow(controller);
            ApplicationThemeManager themes = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            Stage owner = new Stage();
            window.setStage(owner);
            Scene scene = window.createScene();
            owner.setScene(scene);
            owner.setWidth(900);
            owner.setHeight(650);
            owner.show();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Button settings = (Button) scene.lookup("#application-settings-button");
            assertNotNull(settings);
            assertEquals("Settings", settings.getAccessibleText());
            assertEquals("Settings", settings.getTooltip().getText());
            assertTrue(settings.isFocusTraversable());
            MenuBar menuBar = MainWindowTestSupport.menuBar(scene.getRoot());
            assertEquals(List.of("File", "View", "Analysis", "Help"),
                    menuBar.getMenus().stream().map(Menu::getText).toList());
            assertFalse(menuBar.getMenus().getFirst().getItems().stream()
                    .anyMatch(item -> item.getText() != null && item.getText().contains("Settings")));
            var settingsBounds = settings.localToScene(settings.getBoundsInLocal());
            assertTrue(settingsBounds.getMaxX() >= scene.getWidth() - 12,
                    "Settings gear remains at the right edge of the menu row.");

            java.util.concurrent.atomic.AtomicReference<Stage> settingsWindow = new java.util.concurrent.atomic.AtomicReference<>();
            javafx.application.Platform.runLater(() -> Window.getWindows().stream()
                    .filter(Stage.class::isInstance).map(Stage.class::cast)
                    .filter(candidate -> candidate != owner && candidate.getOwner() == owner && candidate.isShowing())
                    .findFirst().ifPresent(candidate -> { settingsWindow.set(candidate); candidate.hide(); }));
            settings.fire();
            assertNotNull(settingsWindow.get(), "Settings button must open the existing owned settings dialog.");
            assertEquals(javafx.stage.Modality.WINDOW_MODAL, settingsWindow.get().getModality());

            owner.hide();
            window.closeApplicationPreferences();
        });
    }

    @Test
    void closeButtonsOnAllInputViewsReturnToSummaryWithoutApplyingOrRecalculating() throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            MainWindow window = new MainWindow(controller);
            BorderPane root = field(window, "root");
            StackPane host = (StackPane) root.getCenter();
            Menu viewMenu = MainWindowTestSupport.menuBar(root).getMenus().get(1);
            ResultsSummaryView summary = field(window, "resultsSummaryView");
            Node assumptionsColumn = summary.getRight();

            var assumptions = (AssumptionsView) field(window, "assumptionsView");
            TextField draft = field(assumptions, "inflationRateField");
            draft.setText("pending close draft");
            assertTrue(assumptions.isDirty());

            var originalPlan = controller.getCurrentPlan();
            Object originalProjection = controller.peekCurrentProjection();
            long originalRevision = controller.getSourcePlanRevision();
            boolean originalDirty = controller.isModified();
            var screens = (Map<String, Node>) field(window, "workspaceScreens");

            for (String id : List.of("household", "accounts", "income", "expenses",
                    "non-investable-assets", "assumptions", "roth-conversions")) {
                viewItem(viewMenu, id).fire();
                assertInstanceOf(WorkspaceScreenFrame.class, screens.get(id));
                var frame = (WorkspaceScreenFrame) screens.get(id);
                assertEquals("Close", frame.getCloseButton().getText());
                assertEquals("Close", frame.getCloseButton().getAccessibleText());
                assertTrue(frame.getCloseButton().isFocusTraversable());
                if (id.equals("accounts")) {
                    host.setDisable(true);
                    assertTrue(frame.getCloseButton().isDisabled(),
                            "Workspace Close must respect MainWindow's existing busy-state disablement.");
                    host.setDisable(false);
                }
                assertSame(frame, host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
                frame.getCloseButton().fire();
                assertEquals("results-summary", field(window, "selectedWorkspaceId"), id);
                assertSame(summary, host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
                assertSame(originalPlan, controller.getCurrentPlan());
                assertSame(originalProjection, controller.peekCurrentProjection());
                assertEquals(originalRevision, controller.getSourcePlanRevision());
                assertEquals(originalDirty, controller.isModified());
                assertEquals("pending close draft", draft.getText());
                assertTrue(assumptions.isDirty());
                assertSame(assumptionsColumn, summary.getRight());
            }

            window.closeApplicationPreferences();
        });
    }

    @Test
    void draftEditsSurviveNavigationAndPlanViewRefreshWithoutApplyingThem() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            Menu viewMenu = MainWindowTestSupport.menuBar(root).getMenus().get(1);
            AssumptionsView assumptions = field(window, "assumptionsView");
            TextField draft = field(assumptions, "inflationRateField");
            draft.setText("unapplied draft");
            var controller = (ApplicationController) field(window, "controller");
            var plan = controller.getCurrentPlan();
            long revision = controller.getSourcePlanRevision();
            viewItem(viewMenu, "household").fire();
            viewItem(viewMenu, "assumptions").fire();
            assertEquals("unapplied draft", draft.getText());
            assertTrue(assumptions.isDirty());
            assertSame(plan, controller.getCurrentPlan());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertFalse(controller.isModified(), "A view draft must not mark plan data modified by navigation alone.");
            window.closeApplicationPreferences();
        });
    }

    @Test
    void summaryActionOpensOneOwnedModelessDetailsWindowWithoutChangingPlanOrProjection() throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            MainWindow window = new MainWindow(controller);
            ApplicationThemeManager manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            Stage owner = new Stage();
            window.setStage(owner);
            owner.setScene(window.createScene());
            owner.setWidth(1180);
            owner.setHeight(820);
            owner.show();

            Projection projection = new Projection();
            var year = ProjectionYearBuilder.aProjectionYear().withCalendarYear(2027)
                    .withPrimaryPersonAge(67).withBeginningInvestableAssets(100_000).build();
            projection.addYear(year);
            var details = (com.daviddunn.retirementplanner.ui.views.ResultsView) field(window, "resultsView");
            var summary = (ResultsSummaryView) field(window, "resultsSummaryView");
            details.load(projection, List.of());
            summary.load(controller.getCurrentPlan(), projection, List.of());

            Button action = field(summary, "yearlyDetailsButton");
            assertNotNull(action);
            assertFalse(action.isDisabled());
            assertNotNull(summary.getRight(), "Key Assumptions remains on Summary's right side.");
            TableView<?> summaryProjectionTable = field(summary, "projectionTable");
            assertSame(summaryProjectionTable.getParent(), action.getParent().getParent(),
                    "The action belongs to the projection table's own heading.");
            Object projectionBefore = controller.peekCurrentProjection();
            long revisionBefore = controller.getSourcePlanRevision();
            boolean dirtyBefore = controller.isModified();

            action.fire();
            Stage detailStage = (Stage) field(window, "yearlyDetailsStage");
            assertTrue(detailStage.isShowing());
            assertSame(owner, detailStage.getOwner());
            assertEquals(javafx.stage.Modality.NONE, detailStage.getModality());
            assertSame(details, detailStage.getScene().getRoot());
            assertTrue(owner.isShowing(), "Summary remains usable beside the modeless detail window.");
            action.fire();
            assertSame(detailStage, field(window, "yearlyDetailsStage"), "Repeated activation reuses one Stage.");
            assertEquals(1, details.getTable().getItems().size());
            assertSame(year, details.getTable().getItems().getFirst());
            assertSame(projectionBefore, controller.peekCurrentProjection());
            assertEquals(revisionBefore, controller.getSourcePlanRevision());
            assertEquals(dirtyBefore, controller.isModified());

            detailStage.close();
            action.fire();
            assertSame(detailStage, field(window, "yearlyDetailsStage"));
            assertTrue(detailStage.isShowing(), "The existing detail window can be reopened.");
            owner.hide();
            assertFalse(detailStage.isShowing(), "Hiding the owner also dismisses the owned detail window.");
            manager.close();
            window.closeApplicationPreferences();
        });
    }

    @Test
    void yearlyDetailsActionIsDisabledWithoutProjectionAndViewMenuDoesNotContainDetails() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            ResultsSummaryView summary = field(window, "resultsSummaryView");
            Button action = field(summary, "yearlyDetailsButton");
            assertNotNull(action);
            assertTrue(action.isDisabled());
            Menu view = MainWindowTestSupport.menuBar(root).getMenus().get(1);
            assertFalse(view.getItems().stream().anyMatch(item -> "Yearly Detail".equals(item.getText())));
            assertEquals(8, ((StackPane) root.getCenter()).getChildren().size());
            window.closeApplicationPreferences();
        });
    }

    @Test
    void planRefreshWhileDetailsWindowIsOpenClearsOldRowsAndDisablesTheAction() throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            MainWindow window = new MainWindow(controller);
            Stage owner = new Stage();
            window.setStage(owner);
            owner.setScene(window.createScene());
            owner.show();
            ResultsView details = field(window, "resultsView");
            ResultsSummaryView summary = field(window, "resultsSummaryView");
            Projection projection = new Projection();
            projection.addYear(ProjectionYearBuilder.aProjectionYear().withCalendarYear(2028)
                    .withPrimaryPersonAge(68).withBeginningInvestableAssets(10_000).build());
            details.load(projection, List.of());
            summary.load(controller.getCurrentPlan(), projection, List.of());
            Button action = field(summary, "yearlyDetailsButton");
            action.fire();
            Stage detailStage = field(window, "yearlyDetailsStage");
            assertTrue(detailStage.isShowing());
            assertEquals(1, details.getTable().getItems().size());

            var replacement = RetirementPlanFactory.createSinglePersonPlan();
            controller.newPlan(replacement);
            var load = MainWindow.class.getDeclaredMethod("loadCurrentPlan", boolean.class);
            load.setAccessible(true);
            load.invoke(window, false);

            assertTrue(detailStage.isShowing());
            assertTrue(details.getTable().getItems().isEmpty());
            assertTrue(action.isDisabled());
            assertSame(replacement, controller.getCurrentPlan());
            owner.hide();
            window.closeApplicationPreferences();
        });
    }

    @Test
    void planReplacementRefreshReusesWorkspaceNodesAndKeepsSelectedView() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            Menu viewMenu = MainWindowTestSupport.menuBar(root).getMenus().get(1);
            viewItem(viewMenu, "accounts").fire();
            StackPane host = (StackPane) root.getCenter();
            Node accountsBefore = field(window, "accountsView");
            var controller = (ApplicationController) field(window, "controller");
            var replacement = RetirementPlanFactory.createSinglePersonPlan();
            replacement.getHousehold().getPrimaryPerson().setFirstName("Replacement");
            controller.newPlan(replacement);

            // Both Open and committed Swap refresh the retained views through this existing path.
            var load = MainWindow.class.getDeclaredMethod("loadCurrentPlan", boolean.class);
            load.setAccessible(true);
            load.invoke(window, false);

            assertEquals("accounts", field(window, "selectedWorkspaceId"));
            assertTrue(viewItem(viewMenu, "accounts").isSelected());
            Node visible = host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow();
            assertInstanceOf(WorkspaceScreenFrame.class, visible);
            assertSame(accountsBefore, ((WorkspaceScreenFrame) visible).getContent());
            assertEquals(8, host.getChildren().size());
            assertSame(replacement, controller.getCurrentPlan());
            window.closeApplicationPreferences();
        });
    }

    @ParameterizedTest
    @CsvSource({"LIGHT,1180,820", "DARK,1180,820", "LIGHT,900,650", "DARK,900,650"})
    void capturesActualJavaFxWorkspaceViewsAndViewPopup(ApplicationTheme theme, int width, int height) throws Exception {
        fx(() -> {
            ApplicationController controller = new ApplicationController();
            MainWindow window = new MainWindow(controller);
            BorderPane root = field(window, "root");
            Stage stage = new Stage();
            Scene scene = new Scene(root, width, height);
            ApplicationThemeManager manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT);
            ApplicationThemeManager.prepare(scene);
            window.setStage(stage);
            stage.setScene(scene);
            stage.setWidth(width);
            stage.setHeight(height);
            stage.show();
            manager.apply(theme);
            Menu viewMenu = MainWindowTestSupport.menuBar(root).getMenus().get(1);

            Projection previewProjection = new Projection();
            previewProjection.addYear(ProjectionYearBuilder.aProjectionYear().withCalendarYear(2027)
                    .withPrimaryPersonAge(67).withBeginningInvestableAssets(100_000).build());
            ResultsView details = field(window, "resultsView");
            ResultsSummaryView summary = field(window, "resultsSummaryView");
            details.load(previewProjection, List.of());
            summary.load(controller.getCurrentPlan(), previewProjection, List.of());

            String variant = theme.name().toLowerCase(java.util.Locale.ROOT) + "-" + width + "x" + height;
            for (String id : LABELS.keySet()) {
                viewItem(viewMenu, id).fire();
                root.applyCss();
                root.resize(width, height);
                root.layout();
            save(root, Path.of("target", "phase15c", "previews", variant, id + ".png"));
            }
            viewItem(viewMenu, "results-summary").fire();
            Button detailsButton = field(summary, "yearlyDetailsButton");
            assertFalse(detailsButton.isDisabled());
            detailsButton.fire();
            Stage detailsStage = field(window, "yearlyDetailsStage");
            detailsStage.setWidth(width);
            detailsStage.setHeight(height);
            detailsStage.centerOnScreen();
            detailsStage.getScene().getRoot().applyCss();
            detailsStage.getScene().getRoot().layout();
            save(detailsStage.getScene().getRoot(), Path.of("target", "phase15c", "previews", variant, "yearly-details.png"));
            details.getTable().scrollToColumn(details.getTable().getColumns().getLast());
            details.getTable().applyCss();
            details.getTable().layout();
            save(detailsStage.getScene().getRoot(), Path.of("target", "phase15c", "previews", variant, "yearly-details-scrolled.png"));
            detailsStage.hide();
            viewMenu.show();
            Window.getWindows().stream().filter(PopupWindow.class::isInstance).filter(Window::isShowing)
                    .map(PopupWindow.class::cast).filter(popup -> popup.getScene() != null)
                    .findFirst().ifPresent(popup -> {
                        try { save(popup.getScene().getRoot(), Path.of("target", "phase15c", "previews", variant, "view-menu-expanded.png")); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    });
            viewMenu.hide();
            stage.hide();
            manager.close();
            window.closeApplicationPreferences();
        });
    }

    private static RadioMenuItem viewItem(Menu menu, String id) {
        return menu.getItems().stream().filter(RadioMenuItem.class::isInstance).map(RadioMenuItem.class::cast)
                .filter(item -> item.getId().equals("view-" + id)).findFirst().orElseThrow();
    }

    private static void save(javafx.scene.Parent node, Path path) throws Exception {
        var image = node.snapshot(null, null);
        var output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(output, "png", path.toFile());
    }
}
