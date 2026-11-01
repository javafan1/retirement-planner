package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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
            "yearly-detail", "Yearly Detail",
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
            MenuBar menuBar = (MenuBar) root.getTop();
            assertEquals(List.of("File", "View", "Analysis", "Help"),
                    menuBar.getMenus().stream().map(Menu::getText).toList());
            Menu viewMenu = menuBar.getMenus().get(1);
            assertEquals(List.of("Results Summary", "Yearly Detail", "|", "Household", "Accounts", "Income",
                            "Expenses", "Non-Investible Assets", "Assumptions", "Roth Conversions"),
                    viewMenu.getItems().stream().map(item -> item instanceof SeparatorMenuItem ? "|" : item.getText()).toList());
            assertEquals(9, viewMenu.getItems().stream().filter(RadioMenuItem.class::isInstance).count());
            assertTrue(viewMenu.getItems().stream().filter(item -> !(item instanceof SeparatorMenuItem))
                    .allMatch(RadioMenuItem.class::isInstance));
            assertEquals(List.of("Social Security Strategy Analyzer...", "Monte Carlo Retirement Analysis...",
                            "Monte Carlo Strategy Comparison..."), menuBar.getMenus().get(2).getItems().stream()
                    .filter(item -> !(item instanceof SeparatorMenuItem))
                    .map(MenuItem::getText).filter(text -> text.endsWith("...")).toList());

            StackPane host = (StackPane) root.getCenter();
            assertEquals(9, host.getChildren().size());
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
                assertSame(field(window, "workspaceViews") instanceof Map<?, ?> views ? views.get(entry.getKey()) : null,
                        host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
                assertEquals(9, host.getChildren().size(), "Navigation must reuse the original view instances.");
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
    void draftEditsSurviveNavigationAndPlanViewRefreshWithoutApplyingThem() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            Menu viewMenu = ((MenuBar) root.getTop()).getMenus().get(1);
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
    void planReplacementRefreshReusesWorkspaceNodesAndKeepsSelectedView() throws Exception {
        fx(() -> {
            MainWindow window = new MainWindow(new ApplicationController());
            BorderPane root = (BorderPane) window.createScene().getRoot();
            Menu viewMenu = ((MenuBar) root.getTop()).getMenus().get(1);
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
            assertSame(accountsBefore, host.getChildren().stream().filter(Node::isVisible).findFirst().orElseThrow());
            assertEquals(9, host.getChildren().size());
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
            stage.setScene(scene);
            stage.setWidth(width);
            stage.setHeight(height);
            stage.show();
            manager.apply(theme);
            Menu viewMenu = ((MenuBar) root.getTop()).getMenus().get(1);

            String variant = theme.name().toLowerCase(java.util.Locale.ROOT) + "-" + width + "x" + height;
            for (String id : LABELS.keySet()) {
                viewItem(viewMenu, id).fire();
                root.applyCss();
                root.resize(width, height);
                root.layout();
                save(root, Path.of("target", "phase15b", "previews", variant, id + ".png"));
            }
            viewItem(viewMenu, "results-summary").fire();
            viewMenu.show();
            Window.getWindows().stream().filter(PopupWindow.class::isInstance).filter(Window::isShowing)
                    .map(PopupWindow.class::cast).filter(popup -> popup.getScene() != null)
                    .findFirst().ifPresent(popup -> {
                        try { save(popup.getScene().getRoot(), Path.of("target", "phase15b", "previews", variant, "view-menu-expanded.png")); }
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
