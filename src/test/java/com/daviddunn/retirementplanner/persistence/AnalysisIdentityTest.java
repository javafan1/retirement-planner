package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisIdentityTest {
    @TempDir Path dir;
    final JsonRetirementPlanRepository plans = new JsonRetirementPlanRepository();

    @Test void newRepeatedLegacyExplicitSaveAndFailedMigration() throws Exception {
        var p = RetirementPlanFactory.createSinglePersonPlan();
        var id = p.getPlanId(); assertNotNull(id);
        var file = dir.resolve("plan.json"); plans.save(p, file); plans.save(p, file);
        assertEquals(id, plans.load(file).getPlanId());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var json = mapper.valueToTree(p); ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("planId");
        var legacy = dir.resolve("legacy.json"); Files.write(legacy, mapper.writeValueAsBytes(json));
        var bytes = Files.readAllBytes(legacy); var loaded = plans.load(legacy);
        assertNull(loaded.getPlanId()); assertArrayEquals(bytes, Files.readAllBytes(legacy));
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.REPLACE) throw new java.io.IOException("injected");
        }), new com.fasterxml.jackson.databind.ObjectMapper());
        assertThrows(java.io.IOException.class, () -> faulty.save(loaded, legacy));
        assertNull(loaded.getPlanId()); assertArrayEquals(bytes, Files.readAllBytes(legacy));
        plans.save(loaded, legacy); assertNotNull(loaded.getPlanId());
        assertEquals(loaded.getPlanId(), plans.load(legacy).getPlanId());
        assertNotEquals(id, loaded.getPlanId());
    }
    @ParameterizedTest @ValueSource(strings = {"../escape", "", "1-1-1-1-1", "not-a-uuid", "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"})
    void malformedIdentityIsRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> RetirementPlan.validatedPlanId(value));
    }
    @Test void saveAsForkClonesWithoutMutatingOldSnapshotAndFailureRollsBack() throws Exception {
        var snapshots = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        var store = new AnalysisResultStore(snapshots);
        var c = new ApplicationController(plans, new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        var original = dir.resolve("old.json"); c.saveAs(original);
        var oldId = UUID.fromString(c.getCurrentPlan().getPlanId());
        var oldSnapshot = SnapshotFixtures.snapshot(oldId); snapshots.save(oldSnapshot);
        byte[] oldBytes = Files.readAllBytes(original);
        assertThrows(java.io.IOException.class, () -> c.saveAs(dir.resolve("missing/fail.json")));
        assertEquals(oldId.toString(), c.getCurrentPlan().getPlanId()); assertEquals(original, c.getCurrentFile());
        var next = dir.resolve("new.json"); c.saveAs(next);
        var newId = UUID.fromString(c.getCurrentPlan().getPlanId()); assertNotEquals(oldId, newId);
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
        var clone = snapshots.load(newId, AnalysisKind.COUPLE_WEIGHTED).orElseThrow();
        assertEquals(oldId, clone.originPlanId()); assertEquals(oldSnapshot.completedAt(), clone.completedAt());
        assertEquals(oldSnapshot.payload(), clone.payload()); assertEquals(oldSnapshot.context(), clone.context());
        assertEquals(oldSnapshot, snapshots.load(oldId, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
        assertArrayEquals(oldBytes, Files.readAllBytes(original));
        assertEquals(oldId.toString(), plans.load(original).getPlanId());
        assertEquals(newId.toString(), plans.load(next).getPlanId());
        store.shutdownAfterSuccessfulDrain();
    }
    @Test void firstSaveBindsNewIdentityAndMovePreservesIt() throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var c = new ApplicationController(plans, new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        var identity = c.getCurrentPlan().getPlanId(); assertFalse(c.canPersistAnalysisResults());
        c.saveAs(dir.resolve("first.json")); assertTrue(c.canPersistAnalysisResults());
        assertEquals(identity, c.getCurrentPlan().getPlanId());
        Files.move(c.getCurrentFile(), dir.resolve("moved.json")); c.open(dir.resolve("moved.json"));
        assertEquals(identity, c.getCurrentPlan().getPlanId());
        assertNotEquals(identity, RetirementPlanFactory.createSinglePersonPlan().getPlanId());
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit()); store.shutdownAfterSuccessfulDrain();
    }
    @Test void cloneFailureDoesNotReclassifySuccessfulPlanSave() throws Exception {
        var repository = new AnalysisSnapshotRepository() {
            public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) throws java.io.IOException { throw new java.io.IOException("unavailable"); }
            public void save(AnalysisSnapshot s) { fail(); }
            public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) { return Optional.empty(); }
        };
        var store = new AnalysisResultStore(repository);
        var c = new ApplicationController(plans, new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        c.saveAs(dir.resolve("old.json")); c.markModified(); c.saveAs(dir.resolve("new.json"));
        assertFalse(c.isModified()); assertEquals(c.getCurrentPlan().getPlanId(), plans.load(c.getCurrentFile()).getPlanId());
        assertFalse(store.drain(Duration.ofSeconds(5)).failures().isEmpty());
        assertTrue(c.takePersistenceWarnings().stream().anyMatch(s -> s.contains("not copied")));
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit()); store.shutdownAfterSuccessfulDrain();
    }
    @Test void retryingHistoricalCopyCannotReplaceNewTargetsOwnSuccessfulResult() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repository);
        var original = UUID.randomUUID(); var target = UUID.randomUUID();
        var oldSnapshot = SnapshotFixtures.snapshot(original); var newSnapshot = SnapshotFixtures.snapshot(target);
        repository.save(oldSnapshot); repository.save(newSnapshot);
        store.cloneSnapshots(original, target, dir.resolve("target.json")).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(newSnapshot, repository.load(target, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
        assertEquals(oldSnapshot, repository.load(original, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit()); store.shutdownAfterSuccessfulDrain();
    }
}
