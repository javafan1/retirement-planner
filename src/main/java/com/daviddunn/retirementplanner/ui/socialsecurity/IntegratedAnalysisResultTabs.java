package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

/** Shared result navigation and natural-height sizing inside the analyzer's outer scroller. */
final class IntegratedAnalysisResultTabs extends TabPane {
    IntegratedAnalysisResultTabs(Tab ranked, Tab heatMap) {
        super(ranked, heatMap);
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        setMinHeight(Region.USE_PREF_SIZE);
        for (Tab tab : getTabs()) {
            if (tab.getContent() instanceof Region content) {
                content.setMinHeight(Region.USE_PREF_SIZE);
                content.needsLayoutProperty().addListener((observable, before, needsLayout) -> {
                    if (needsLayout) requestLayout();
                });
            }
        }
        getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            requestLayout();
            if (after == heatMap) Platform.runLater(() -> reveal(this));
        });
    }

    @Override protected double computePrefHeight(double width) {
        var selected = getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getContent() instanceof Region content)) return super.computePrefHeight(width);
        var header = lookup(".tab-header-area");
        double headerHeight = header instanceof Region region ? region.prefHeight(width) : 0;
        return snappedTopInset() + headerHeight + content.prefHeight(
                Math.max(0, width - snappedLeftInset() - snappedRightInset())) + snappedBottomInset();
    }

    static void reveal(Node node) {
        if (node.getScene() == null) return;
        node.getScene().getRoot().applyCss();
        node.getScene().getRoot().layout();
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll && scroll.getContent() != null) {
                double available = scroll.getContent().getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight();
                if (available > 0) {
                    double target = scroll.getContent().sceneToLocal(node.localToScene(0, 0)).getY();
                    double fraction = Math.max(0, Math.min(1, target / available));
                    scroll.setVvalue(scroll.getVmin() + fraction * (scroll.getVmax() - scroll.getVmin()));
                }
                return;
            }
        }
    }
}
