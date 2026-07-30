package com.daviddunn.retirementplanner.application.settings;

public interface ApplicationSettingsRepository {

    /**
     * Loads the application settings.
     *
     * If no settings have been saved yet,
     * returns default settings.
     */
    ApplicationSettings load();

    /**
     * Persists the application settings.
     */
    void save(
            ApplicationSettings settings);
}