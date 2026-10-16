package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionalSettingsTest {
    @TempDir Path directory;

    @Test void missingUsesDefaultsAndValidSettingsRoundTrip() {
        var repository = new JsonApplicationSettingsRepository(directory.resolve("settings.json"));
        assertTrue(repository.load().isAutomaticallyOpenLastPlan());
        assertTrue(repository.takeWarnings().isEmpty());
        var settings = new ApplicationSettings();
        settings.setLastOpenedPlan("example.json");
        repository.save(settings);
        assertEquals("example.json", repository.load().getLastOpenedPlan());
    }

    @Test void corruptAndNullSettingsUseDefaultsWithoutOverwritingEvidence() throws Exception {
        for (String broken : new String[] {"{", "null", ""}) {
            Path file = directory.resolve("settings.json");
            Files.writeString(file, broken);
            var repository = new JsonApplicationSettingsRepository(file);
            assertFalse(repository.load().hasLastOpenedPlan());
            assertNotNull(repository.loadFailure());
            assertFalse(repository.takeWarnings().isEmpty());
            assertThrows(RuntimeException.class, () -> repository.save(new ApplicationSettings()));
            assertEquals(broken, Files.readString(file));
        }
    }

    @Test void unreadableDirectoryUsesDefaults() throws Exception {
        Path file = Files.createDirectory(directory.resolve("settings.json"));
        var repository = new JsonApplicationSettingsRepository(file);
        assertFalse(repository.load().hasLastOpenedPlan());
        assertFalse(repository.takeWarnings().isEmpty());
        assertTrue(Files.isDirectory(file));
    }

    @Test void failedWriteAndAtomicMovePreserveExistingSettings() throws Exception {
        for (var stage : new AtomicFileWriter.Stage[] {AtomicFileWriter.Stage.WRITE, AtomicFileWriter.Stage.REPLACE}) {
            Path file = directory.resolve(stage + ".json");
            new JsonApplicationSettingsRepository(file).save(new ApplicationSettings());
            byte[] before = Files.readAllBytes(file);
            var faulty = new JsonApplicationSettingsRepository(file, new AtomicFileWriter((current, path) -> {
                if (current == stage) throw new IOException("Injected");
            }));
            assertThrows(RuntimeException.class, () -> faulty.save(new ApplicationSettings()));
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }
}
