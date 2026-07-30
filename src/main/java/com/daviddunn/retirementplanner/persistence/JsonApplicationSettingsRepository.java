package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.application.settings.ApplicationSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonApplicationSettingsRepository
        implements ApplicationSettingsRepository {

    private static final Path SETTINGS_FILE =
            Path.of(
                    System.getProperty("user.home"),
                    ".retirement-planner",
                    "settings.json");

    private final ObjectMapper objectMapper;

    public JsonApplicationSettingsRepository() {

        objectMapper = new ObjectMapper();

        objectMapper.enable(
                SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public ApplicationSettings load() {

        if (!Files.exists(SETTINGS_FILE)) {
            return new ApplicationSettings();
        }

        try {

            return objectMapper.readValue(
                    SETTINGS_FILE.toFile(),
                    ApplicationSettings.class);

        } catch (IOException ex) {

            throw new RuntimeException(
                    "Failed to load application settings.",
                    ex);
        }
    }

    @Override
    public void save(
            ApplicationSettings settings) {

        try {

            Files.createDirectories(
                    SETTINGS_FILE.getParent());

            objectMapper.writeValue(
                    SETTINGS_FILE.toFile(),
                    settings);

        } catch (IOException ex) {

            throw new RuntimeException(
                    "Failed to save application settings.",
                    ex);
        }
    }
}