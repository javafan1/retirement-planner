package com.daviddunn.retirementplanner.application.settings;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** Filesystem work belongs on the preferences worker, never a JavaFX event handler. */
public final class PlanDirectoryResolver {
    public record Resolution(Path directory, boolean preferredUnavailable) { }
    private PlanDirectoryResolver() { }

    public static boolean usable(String text) {
        try { return accessible(Path.of(text)); } catch (RuntimeException invalid) { return false; }
    }

    public static boolean accessible(Path path) {
        try { return Files.isDirectory(path) && Files.isReadable(path) && Files.isWritable(path); }
        catch (SecurityException denied) { return false; }
    }

    public static Resolution resolve(ApplicationSettings settings, Path active, String home) {
        return resolve(settings, active, home, PlanDirectoryResolver::accessible);
    }

    public static Resolution resolve(ApplicationSettings settings, Path active, String home, Predicate<Path> accessible) {
        var preferred = path(settings.getDefaultPlanDirectory());
        boolean unavailable = settings.getDefaultPlanDirectory() != null;
        if (preferred != null && accessible.test(preferred)) return new Resolution(preferred, false);
        var candidates = new ArrayList<Path>();
        if (active != null) candidates.add(active.toAbsolutePath().normalize().getParent());
        var last = path(settings.getLastOpenedPlan());
        if (last != null) candidates.add(last.getParent());
        candidates.add(path(home));
        for (var candidate : candidates) if (candidate != null && accessible.test(candidate)) return new Resolution(candidate, unavailable);
        return new Resolution(null, unavailable);
    }

    private static Path path(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Path.of(text).toAbsolutePath().normalize(); } catch (RuntimeException invalid) { return null; }
    }
}
