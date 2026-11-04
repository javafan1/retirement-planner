package com.daviddunn.retirementplanner.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.MenuBar;

public final class MainWindowTestSupport {
    private MainWindowTestSupport() { }

    public static MenuBar menuBar(Node root) {
        if (root instanceof MenuBar menuBar) return menuBar;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                MenuBar found = findMenuBar(child);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Main application MenuBar was not found in the top bar.");
    }

    private static MenuBar findMenuBar(Node node) {
        if (node instanceof MenuBar menuBar) return menuBar;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                MenuBar found = findMenuBar(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
