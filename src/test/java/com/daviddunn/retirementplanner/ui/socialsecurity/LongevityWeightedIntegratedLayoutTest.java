package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LongevityWeightedIntegratedLayoutTest {
    @BeforeAll
    static void startFx() throws Exception {
        FutureTask<Void> startup = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(startup);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(startup);
        }
        startup.get(20, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"1180,820", "900,650", "1366,768"})
    void fullContentCollapseKeyboardAndBoundedTable(int width, int height) throws Exception {
        FutureTask<Void> test = new FutureTask<>(() -> {
            var view = new LongevityWeightedIntegratedView();
            var entry = LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1);
            view.render(LongevityWeightedIntegratedPresentationTest.model(
                    List.of(entry), java.util.Optional.of(entry)), "Current elections");
            view.comparisons(Collections.nCopies(3, new IntegratedAnalysisComparisonPresentation.Row(
                    "Highest / Current", entry.strategy(), java.util.OptionalInt.of(1),
                    java.util.OptionalInt.of(1), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty())));
            var panes = view.rankedContent.getChildren().stream().filter(TitledPane.class::isInstance)
                    .map(TitledPane.class::cast).toList();
            assertEquals(5, panes.size());
            for (var pane : panes) {
                assertFalse(pane.getContent() instanceof ScrollPane);
                lengthenLabels(pane.getContent());
            }
            var scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(false);
            var root = new StackPane(scroll);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, width, height));
            stage.show();
            try {
            root.resize(width, height);
            layout(root);
            // The original table used computed JavaFX preferred height, min 110,
            // and the default max height. Preserve those settings and row viewport.
            double tableHeight = view.table.getHeight();
            assertEquals(110, view.table.getMinHeight());
            assertEquals(Region.USE_COMPUTED_SIZE, view.table.getPrefHeight());
            assertEquals(Region.USE_COMPUTED_SIZE, view.table.getMaxHeight());
            assertEquals(view.table.prefHeight(view.table.getWidth()), tableHeight, 1);
            assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY,
                    view.table.getColumnResizePolicy());
            if (width == 900) {
                assertTrue(view.table.lookupAll(".scroll-bar").stream()
                        .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible()));
            }
            view.table.getItems().setAll(Collections.nCopies(5184, entry));
            layout(root);
            assertEquals(tableHeight, view.table.getHeight(), 1);
            assertTrue(view.table.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                    .anyMatch(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible()));

            for (var pane : panes) {
                double collapsed = pane.getHeight();
                key(pane);
                layout(root);
                assertTrue(pane.isExpanded(), pane.getText());
                double expanded = pane.getHeight();
                assertTrue(expanded > collapsed, pane.getText());
                assertFullContent(pane);
                assertEquals(tableHeight, view.table.getHeight(), 1);
                key(pane);
                layout(root);
                assertFalse(pane.isExpanded());
                assertTrue(pane.getHeight() < expanded);
                key(pane);
                layout(root);
                assertFullContent(pane);
                assertEquals(expanded, pane.getHeight(), 1);
            }
            assertTrue(view.getHeight() > scroll.getViewportBounds().getHeight());
            assertTrue(view.getWidth() <= scroll.getViewportBounds().getWidth() + 1);
            double previousBottom = 0;
            for (Node child : view.getChildren()) {
                assertTrue(child.getBoundsInParent().getMinY() >= previousBottom - 1);
                previousBottom = child.getBoundsInParent().getMaxY();
            }
            assertEquals(tableHeight, view.table.getHeight(), 1);
            scroll.setVvalue(1);
            layout(root);
            assertTrue(scroll.localToScene(scroll.getBoundsInLocal()).getMaxY() + 1
                    >= panes.getLast().localToScene(panes.getLast().getBoundsInLocal()).getMaxY(),
                    () -> "Viewport bottom=" + scroll.localToScene(scroll.getBoundsInLocal()).getMaxY()
                            + ", detail bottom=" + panes.getLast().localToScene(panes.getLast().getBoundsInLocal()).getMaxY()
                            + ", view=" + view.getHeight() + ", tabs=" + view.resultTabs.getHeight()
                            + ", ranked=" + view.rankedContent.getHeight());
            } finally {
                stage.close();
            }
            return null;
        });
        Platform.runLater(test);
        test.get(30, TimeUnit.SECONDS);
    }

    private static void lengthenLabels(Node node) {
        if (node instanceof Label label) {
            label.setText((label.getText() + " Long wrapped result-time assumptions and strategy details. ").repeat(8));
        } else if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(LongevityWeightedIntegratedLayoutTest::lengthenLabels);
        }
    }

    private static void assertFullContent(TitledPane pane) {
        assertTrue(pane.getHeight() + 1 >= pane.prefHeight(pane.getWidth()), pane.getText());
        assertFits(pane.getContent());
    }

    private static void assertFits(Node node) {
        if (node instanceof Region region) {
            assertTrue(region.getHeight() + 1 >= region.prefHeight(region.getWidth()),
                    () -> node.getClass().getSimpleName() + " clipped: " + region.getHeight()
                            + " < " + region.prefHeight(region.getWidth()));
        }
        if (node instanceof Parent parent && !(node instanceof Control)) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                assertTrue(child.getBoundsInParent().getMaxY() <= parent.getLayoutBounds().getHeight() + 1);
                assertFits(child);
            }
        }
    }

    private static void key(TitledPane pane) {
        pane.requestFocus();
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE,
                false, false, false, false));
    }

    private static void layout(Parent root) {
        for (int pass = 0; pass < 4; pass++) {
            root.applyCss();
            root.layout();
        }
    }
}
