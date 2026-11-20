package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** One acknowledgment-only projection warning. All lifecycle operations are owned by JavaFX. */
final class ProjectionFailureWarning implements AutoCloseable {
    private final Stage owner;
    private final String message;
    private final BooleanSupplier current;
    private final Supplier<Node> fallbackFocus;
    private final ListChangeListener<Window> windowsChanged = change -> Platform.runLater(this::show);
    private Alert alert;
    private boolean waiting;
    private boolean closed;

    ProjectionFailureWarning(Stage owner, String message, BooleanSupplier current, Supplier<Node> fallbackFocus) {
        this.owner = owner;
        this.message = message;
        this.current = current;
        this.fallbackFocus = fallbackFocus;
    }

    void show() {
        if (closed || alert != null) return;
        if (!owner.isShowing()) {
            close();
            return;
        }
        if (Window.getWindows().stream().anyMatch(this::blocksOwner)) {
            if (!waiting) {
                Window.getWindows().addListener(windowsChanged);
                waiting = true;
            }
            return;
        }
        stopWaiting();
        if (!current.getAsBoolean()) {
            close();
            return;
        }

        var focus = owner.getScene().getFocusOwner();
        double centerX = owner.getX() + owner.getWidth() / 2;
        double centerY = owner.getY() + owner.getHeight() / 2;
        var bounds = Screen.getScreensForRectangle(centerX, centerY, 1, 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        alert = new Alert(Alert.AlertType.WARNING, "", ButtonType.OK);
        alert.initOwner(owner);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.setTitle("Unable to Calculate Projection");
        alert.setHeaderText(null);
        var pane = alert.getDialogPane();
        pane.setId("projection-failure-warning");
        double width = Math.min(480, bounds.getWidth() - 24);
        pane.setPrefWidth(width);
        var content = new Label(message);
        content.setId("projection-failure-message");
        content.setWrapText(true);
        content.setMinWidth(0);
        content.setMinHeight(Region.USE_PREF_SIZE);
        pane.setContent(content);
        ApplicationThemeManager.prepare(pane.getScene());
        pane.applyCss();
        double availableHeight = Math.max(60, bounds.getHeight() - 132);
        if (content.prefHeight(width - 95) > availableHeight) {
            var scroller = new ScrollPane(content);
            scroller.setFitToWidth(true);
            scroller.setPrefViewportHeight(availableHeight);
            pane.setContent(scroller);
        }
        var ok = (Button) pane.lookupButton(ButtonType.OK);
        ok.setDefaultButton(true);
        ok.setCancelButton(true);
        pane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                ok.fire();
            }
        });
        alert.setOnShown(event -> {
            var window = pane.getScene().getWindow();
            window.setX(centered(centerX, window.getWidth(), bounds.getMinX(), bounds.getMaxX()));
            window.setY(centered(centerY, window.getHeight(), bounds.getMinY(), bounds.getMaxY()));
            ok.requestFocus();
        });
        alert.setOnHidden(event -> {
            if (!closed) Platform.runLater(() -> restoreFocus(focus));
        });
        alert.show();
    }

    private boolean blocksOwner(Window window) {
        if (!(window instanceof Stage stage) || stage == owner || !stage.isShowing()) return false;
        if (stage.getModality() == Modality.APPLICATION_MODAL) return true;
        if (stage.getModality() != Modality.WINDOW_MODAL) return false;
        for (Window ancestor = stage.getOwner(); ancestor instanceof Stage parent; ancestor = parent.getOwner()) {
            if (ancestor == owner) return true;
        }
        return false;
    }

    private static double centered(double center, double size, double min, double max) {
        return Math.max(min, Math.min(center - size / 2, max - size));
    }

    private void restoreFocus(Node focus) {
        if (closed || !owner.isShowing() || Window.getWindows().stream().anyMatch(this::blocksOwner)) return;
        Node target = usableFocus(focus) ? focus : fallbackFocus.get();
        if (usableFocus(target)) target.requestFocus();
    }

    private boolean usableFocus(Node focus) {
        if (focus == null || focus.getScene() != owner.getScene()) return false;
        for (Node node = focus; node != null; node = node.getParent()) {
            if (!node.isVisible() || node.isDisabled()) return false;
        }
        return true;
    }

    private void stopWaiting() {
        if (waiting) {
            Window.getWindows().removeListener(windowsChanged);
            waiting = false;
        }
    }

    @Override
    public void close() {
        closed = true;
        stopWaiting();
        if (alert != null) alert.close();
    }
}
