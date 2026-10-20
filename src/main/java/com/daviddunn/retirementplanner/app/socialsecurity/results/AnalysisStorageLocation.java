package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.nio.file.Path;
import java.util.Map;

public final class AnalysisStorageLocation {
    private AnalysisStorageLocation() { }
    public static Path systemDefault() {
        return resolve(System.getProperty("os.name"), System.getProperty("user.home"), System.getenv());
    }
    public static Path resolve(String os, String home, Map<String, String> environment) {
        if (os.toLowerCase(java.util.Locale.ROOT).contains("win")) {
            String local = environment.get("LOCALAPPDATA");
            return (local == null || local.isBlank() ? Path.of(home, "AppData", "Local") : Path.of(local))
                    .resolve("RetirementPlanner/analysis-results/v1");
        }
        if (os.toLowerCase(java.util.Locale.ROOT).contains("mac"))
            return Path.of(home, "Library", "Application Support", "RetirementPlanner", "analysis-results", "v1");
        String xdg = environment.get("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute() ? Path.of(xdg) : Path.of(home, ".local", "share");
        return base.resolve("RetirementPlanner/analysis-results/v1");
    }
}
