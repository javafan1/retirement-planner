package com.daviddunn.retirementplanner.ui.settings;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.*;
import javafx.stage.Window;
import java.util.*;

/** FX-owned presentation only. Public window lifecycle covers Dialog, Stage and PopupControl scenes. */
public final class ApplicationThemeManager implements AutoCloseable {
    private static ApplicationThemeManager active;
    private ApplicationTheme theme;
    private final Map<Window, ChangeListener<Scene>> windows = new IdentityHashMap<>();
    private final Map<Scene, ChangeListener<Parent>> scenes = new WeakHashMap<>();
    private final Set<Parent> commonOwners = Collections.newSetFromMap(new WeakHashMap<>());
    private final ListChangeListener<Window> changes = change -> {
        while (change.next()) {
            for (var window : change.getRemoved()) remove(window);
            for (var window : change.getAddedSubList()) register(window);
        }
    };

    public ApplicationThemeManager(ApplicationTheme initial) {
        theme = Objects.requireNonNull(initial);
        Window.getWindows().addListener(changes);
        for (var window : List.copyOf(Window.getWindows())) register(window);
    }

    public static ApplicationThemeManager start(ApplicationTheme initial) {
        if (active != null) active.close();
        active = new ApplicationThemeManager(initial);
        return active;
    }

    /** Register explicitly before show where a scene is already available. */
    public static void prepare(Scene scene) { if (active != null) active.attach(scene); }
    public ApplicationTheme theme() { return theme; }
    public void apply(ApplicationTheme value) {
        theme = Objects.requireNonNull(value);
        for (var scene : List.copyOf(scenes.keySet())) style(scene.getRoot());
    }

    private void register(Window window) {
        if (windows.containsKey(window)) return;
        ChangeListener<Scene> listener = (o, old, next) -> { detach(old); attach(next); };
        windows.put(window, listener); window.sceneProperty().addListener(listener); attach(window.getScene());
    }

    private void remove(Window window) {
        var listener = windows.remove(window);
        if (listener != null) window.sceneProperty().removeListener(listener);
        detach(window.getScene());
    }

    private void attach(Scene scene) {
        if (scene == null || scenes.containsKey(scene)) return;
        ChangeListener<Parent> listener = (o, old, next) -> { unstyle(old); style(next); };
        scenes.put(scene, listener); scene.rootProperty().addListener(listener); style(scene.getRoot());
    }

    private String resource(ApplicationTheme value) {
        return Objects.requireNonNull(getClass().getResource("/css/theme-" + value.name().toLowerCase(Locale.ROOT) + ".css")).toExternalForm();
    }

    private void style(Parent root) {
        if (root == null) return;
        var common = Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm();
        if (!root.getStylesheets().contains(common)) { root.getStylesheets().add(common); commonOwners.add(root); }
        root.getStyleClass().addAll(root.getStyleClass().contains("app-theme") ? List.of() : List.of("app-theme"));
        root.getStylesheets().removeAll(resource(ApplicationTheme.LIGHT), resource(ApplicationTheme.DARK));
        root.getStylesheets().add(resource(theme));
    }

    private void unstyle(Parent root) {
        if (root == null) return;
        root.getStylesheets().removeAll(resource(ApplicationTheme.LIGHT), resource(ApplicationTheme.DARK));
        if (commonOwners.remove(root)) root.getStylesheets().remove(Objects.requireNonNull(getClass().getResource("/css/application.css")).toExternalForm());
        root.getStyleClass().remove("app-theme");
    }

    private void detach(Scene scene) {
        if (scene == null) return;
        var listener = scenes.remove(scene);
        if (listener != null) scene.rootProperty().removeListener(listener);
        unstyle(scene.getRoot());
    }

    @Override public void close() {
        Window.getWindows().removeListener(changes);
        for (var window : List.copyOf(windows.keySet())) remove(window);
        for (var scene : List.copyOf(scenes.keySet())) detach(scene);
        if (active == this) active = null;
    }
}
