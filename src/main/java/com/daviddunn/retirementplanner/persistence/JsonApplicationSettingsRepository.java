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
    private byte[] expectedDocument;
    private boolean observed;

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
        objectMapper.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        objectMapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public synchronized ApplicationSettings load() {

        if (Files.notExists(settingsFile)) {
            expectedDocument = null;
            observed = true;
            damaged = false;
            loadFailure = null;
            return new ApplicationSettings();
        }

        try {

            if (Files.size(settingsFile) > 65536) throw new IOException("Settings exceed the bounded document size.");

            byte[] document;
            try (var input = Files.newInputStream(settingsFile)) { document = input.readNBytes(65537); }
            if (document.length > 65536) throw new IOException("Settings exceed the bounded document size.");
            ApplicationSettings settings = objectMapper.readValue(document, ApplicationSettings.class);
            if (settings == null) {
                throw new IOException("Empty settings.");
            }
            damaged = false;
            loadFailure = null;
            expectedDocument = document.clone();
            observed = true;
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
            // A stale process must not replace externally changed or unsupported preferences.
            byte[] existing = null;
            if (Files.exists(settingsFile)) {
                try (var input = Files.newInputStream(settingsFile)) { existing = input.readNBytes(65537); }
                if (existing.length > 65536 || objectMapper.readValue(existing, ApplicationSettings.class) == null)
                    throw new IOException("Existing settings are unsupported or invalid; preserve and reload them.");
            }
            if (observed && !java.util.Arrays.equals(existing, expectedDocument))
                throw new IOException("Application settings changed externally; restart before saving preferences.");

            Files.createDirectories(
                    settingsFile.toAbsolutePath().getParent());

            byte[] captured = objectMapper.writeValueAsBytes(settings);
            if (captured.length > 65536) throw new IOException("Settings exceed the bounded document size.");
            warnings.addAll(writer.write(settingsFile, captured, path -> {
                if (objectMapper.readValue(path.toFile(), ApplicationSettings.class) == null) {
                    throw new IOException("Invalid settings.");
                }
            }, false));
            expectedDocument = captured.clone();
            observed = true;

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
