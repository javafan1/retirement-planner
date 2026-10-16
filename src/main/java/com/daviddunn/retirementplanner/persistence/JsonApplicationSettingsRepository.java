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

    private final Path settingsFile;
    private final AtomicFileWriter writer;
    private final java.util.List<String> warnings = new java.util.ArrayList<>();
    private boolean damaged;
    private Exception loadFailure;

    private final ObjectMapper objectMapper;

    public JsonApplicationSettingsRepository() {
        this(Path.of(System.getProperty("user.home"), ".retirement-planner", "settings.json"));
    }

    public JsonApplicationSettingsRepository(Path settingsFile) {
        this(settingsFile, new AtomicFileWriter());
    }

    JsonApplicationSettingsRepository(Path settingsFile, AtomicFileWriter writer) {
        this.settingsFile = java.util.Objects.requireNonNull(settingsFile);
        this.writer = java.util.Objects.requireNonNull(writer);

        objectMapper = new ObjectMapper();

        objectMapper.enable(
                SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public synchronized ApplicationSettings load() {

        if (Files.notExists(settingsFile)) {
            damaged = false;
            loadFailure = null;
            return new ApplicationSettings();
        }

        try {

            ApplicationSettings settings = objectMapper.readValue(settingsFile.toFile(), ApplicationSettings.class);
            if (settings == null) {
                throw new IOException("Empty settings.");
            }
            damaged = false;
            loadFailure = null;
            return settings;

        } catch (IOException | SecurityException ex) {
            loadFailure = ex;
            damaged = true;
            warnings.add("Application settings could not be loaded; defaults were used. The original settings were preserved. Back up and rename the damaged settings file, then restart to enable preference saving.");
            return new ApplicationSettings();
        }
    }

    @Override
    public synchronized void save(
            ApplicationSettings settings) {

        try {
            if (damaged) {
                throw new IOException("Damaged settings are preserved; preference saving is disabled until recovery.");
            }

            Files.createDirectories(
                    settingsFile.toAbsolutePath().getParent());

            byte[] captured = objectMapper.writeValueAsBytes(settings);
            warnings.addAll(writer.write(settingsFile, captured, path -> {
                if (objectMapper.readValue(path.toFile(), ApplicationSettings.class) == null) {
                    throw new IOException("Invalid settings.");
                }
            }, false));

        } catch (IOException ex) {

            throw new RuntimeException(
                    "Failed to save application settings.",
                    ex);
        }
    }

    public synchronized java.util.List<String> takeWarnings() {
        var result = java.util.List.copyOf(warnings);
        warnings.clear();
        return result;
    }

    /** Debugging only; never displayed or transmitted. */
    synchronized Exception loadFailure() {
        return loadFailure;
    }
}
