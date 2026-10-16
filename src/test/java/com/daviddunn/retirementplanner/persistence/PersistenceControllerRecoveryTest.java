package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceControllerRecoveryTest {
    @TempDir Path directory;
    private final JsonRetirementPlanRepository plans = new JsonRetirementPlanRepository();

    private ApplicationController controller() {
        return new ApplicationController(plans, new JsonApplicationSettingsRepository(directory.resolve("settings.json")));
    }

    @Test void failedSaveAsRetainsPathDirtyFlagAndPlanIdentity() throws Exception {
        var controller = controller();
        Path active = directory.resolve("active.json");
        controller.saveAs(active);
        controller.markModified();
        var plan = controller.getCurrentPlan();
        Path bad = directory.resolve("missing/plan.json");
        assertThrows(IOException.class, () -> controller.saveAs(bad));
        assertEquals(active, controller.getCurrentFile());
        assertSame(plan, controller.getCurrentPlan());
        assertTrue(controller.isModified());
        controller.save();
        assertFalse(controller.isModified());
    }

    @Test void settingsFailureIsNonfatalAfterSaveAndOpen() throws Exception {
        var broken = new JsonApplicationSettingsRepository(directory.resolve("settings.json")) {
            @Override public synchronized void save(ApplicationSettings settings) { throw new RuntimeException("Injected"); }
        };
        var controller = new ApplicationController(plans, broken);
        controller.markModified();
        Path file = directory.resolve("plan.json");
        assertDoesNotThrow(() -> controller.saveAs(file));
        assertFalse(controller.isModified());
        assertEquals(file, controller.getCurrentFile());
        assertFalse(controller.takePersistenceWarnings().isEmpty());
        assertDoesNotThrow(() -> controller.open(file));
        assertFalse(controller.takePersistenceWarnings().isEmpty());
        assertDoesNotThrow(() -> controller.save());
    }

    @Test void startupCorruptSettingsAndUnavailableLastPlanDoNotPreventManualOpen() throws Exception {
        Path settingsFile = directory.resolve("settings.json");
        Files.writeString(settingsFile, "broken");
        var controller = controller();
        assertNotNull(controller.getCurrentPlan());
        assertFalse(controller.openLastPlan());
        assertFalse(controller.takePersistenceWarnings().isEmpty());
        Path file = directory.resolve("plan.json");
        plans.save(RetirementPlanFactory.createSinglePersonPlan(), file);
        controller.open(file);
        assertEquals("broken", Files.readString(settingsFile));
        var settings = new ApplicationSettings();
        settings.setLastOpenedPlan(directory.resolve("missing.json").toString());
        Path validSettings = directory.resolve("valid-settings.json");
        var settingsRepo = new JsonApplicationSettingsRepository(validSettings);
        settingsRepo.save(settings);
        var second = new ApplicationController(plans, settingsRepo);
        assertFalse(second.openLastPlan());
        assertNotNull(second.getFailedOpenPath());
        second.open(file);
        assertEquals(file, second.getCurrentFile());
    }

    @Test void explicitRecoveryIsUnsavedDoesNotOverwriteEitherSourceAndRequiresSaveAs() throws Exception {
        Path file = directory.resolve("plan.json");
        var plan = RetirementPlanFactory.createSinglePersonPlan();
        plan.getHousehold().getPrimaryPerson().setFirstName("Previous");
        plans.save(plan, file);
        plan.getHousehold().getPrimaryPerson().setFirstName("Current");
        plans.save(plan, file);
        byte[] backup = Files.readAllBytes(plans.backupPath(file));
        Files.writeString(file, "broken");
        var controller = controller();
        assertTrue(controller.hasValidBackup(file));
        controller.openRecoveredPlan(file);
        assertFalse(controller.hasCurrentFile());
        assertTrue(controller.isModified());
        assertEquals(file, controller.getRecoveredOriginalPath());
        assertEquals(plans.backupPath(file), controller.getRecoveredBackupPath());
        assertEquals("Previous", controller.getCurrentPlan().getHousehold().getPrimaryPerson().getFirstName());
        assertThrows(IllegalStateException.class, controller::save);
        assertThrows(IOException.class, () -> controller.saveAs(file));
        assertThrows(IOException.class, () -> controller.saveAs(plans.backupPath(file)));
        assertTrue(controller.isModified());
        assertEquals("broken", Files.readString(file));
        assertArrayEquals(backup, Files.readAllBytes(plans.backupPath(file)));
        Path recovered = directory.resolve("recovered.json");
        controller.saveAs(recovered);
        assertFalse(controller.isModified());
        assertEquals(recovered, controller.getCurrentFile());
        assertEquals("broken", Files.readString(file));
    }

    @Test void invalidRecoveryDoesNotReplaceActiveSession() throws Exception {
        Path file = directory.resolve("broken.json");
        Files.writeString(plans.backupPath(file), "broken");
        var controller = controller();
        var active = controller.getCurrentPlan();
        assertFalse(controller.hasValidBackup(file));
        assertThrows(IOException.class, () -> controller.openRecoveredPlan(file));
        assertSame(active, controller.getCurrentPlan());
    }

    @Test void backupBecomingUnavailableDuringRecoveryPreservesSession() throws Exception {
        var faulty = new JsonRetirementPlanRepository() {
            @Override public com.daviddunn.retirementplanner.domain.model.RetirementPlan loadBackup(Path file) throws IOException {
                throw new IOException("Injected recovery read failure");
            }
        };
        var controller = new ApplicationController(faulty,
                new JsonApplicationSettingsRepository(directory.resolve("settings.json")));
        var original = controller.getCurrentPlan();
        controller.markModified();
        assertThrows(IOException.class, () -> controller.openRecoveredPlan(directory.resolve("plan.json")));
        assertSame(original, controller.getCurrentPlan());
        assertTrue(controller.isModified());
        assertFalse(controller.hasCurrentFile());
    }

    @Test void directlyOpeningManagedBackupAlsoRequiresSaveAsAndCannotOverwriteSource() throws Exception {
        Path file = directory.resolve("plan.json");
        var plan = RetirementPlanFactory.createSinglePersonPlan();
        plans.save(plan, file);
        plans.save(plan, file);
        Path backup = plans.backupPath(file);
        byte[] before = Files.readAllBytes(backup);
        var controller = controller();
        controller.open(backup);
        assertTrue(controller.isModified());
        assertFalse(controller.hasCurrentFile());
        assertEquals(backup, controller.getRecoveredBackupPath());
        assertThrows(IOException.class, () -> plans.save(plan, backup));
        assertThrows(IllegalStateException.class, controller::save);
        assertArrayEquals(before, Files.readAllBytes(backup));
    }
}
