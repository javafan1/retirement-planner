package com.daviddunn.retirementplanner.application.settings;




public class ApplicationSettings {
    private ApplicationTheme theme = ApplicationTheme.LIGHT;
    private String defaultPlanDirectory;

    public ApplicationTheme getTheme() { return theme; }
    public void setTheme(ApplicationTheme theme) { this.theme = java.util.Objects.requireNonNull(theme); }
    public String getDefaultPlanDirectory() { return defaultPlanDirectory; }
    public void setDefaultPlanDirectory(String directory) {
        if (directory != null && directory.length() > 4096) throw new IllegalArgumentException("Directory preference is too long.");
        defaultPlanDirectory = directory == null || directory.isBlank() ? null : directory;
    }

    public ApplicationSettings copy() {
        var result = new ApplicationSettings();
        result.theme = theme;
        result.defaultPlanDirectory = defaultPlanDirectory;
        result.automaticallyOpenLastPlan = automaticallyOpenLastPlan;
        result.lastOpenedPlan = lastOpenedPlan;
        return result;
    }

    /**
     * Automatically open the last retirement plan when
     * the application starts.
     */
    private boolean automaticallyOpenLastPlan = true;

    /**
     * Full path to the most recently opened or saved
     * retirement plan.
     */
    private String lastOpenedPlan;

    public ApplicationSettings() {
    }

    public boolean isAutomaticallyOpenLastPlan() {
        return automaticallyOpenLastPlan;
    }

    public void setAutomaticallyOpenLastPlan(
            boolean automaticallyOpenLastPlan) {

        this.automaticallyOpenLastPlan =
                automaticallyOpenLastPlan;
    }

    public String getLastOpenedPlan() {
        return lastOpenedPlan;
    }

    public void setLastOpenedPlan(
            String lastOpenedPlan) {

        this.lastOpenedPlan =
                lastOpenedPlan;
    }

    /**
     * Returns true if a last-opened plan has been recorded.
     */
    public boolean hasLastOpenedPlan() {

        return lastOpenedPlan != null
                && !lastOpenedPlan.isBlank();
    }
}
