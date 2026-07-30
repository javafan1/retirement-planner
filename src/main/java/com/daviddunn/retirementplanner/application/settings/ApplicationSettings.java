package com.daviddunn.retirementplanner.application.settings;




public class ApplicationSettings {

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