package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.settings.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationPreferencesTest {
    @TempDir Path directory;

    @Test void oldJsonDefaultsAndNewPreferencesRoundTripIndependently() throws Exception {
        var path = directory.resolve("settings.json");
        Files.writeString(path, "{\"automaticallyOpenLastPlan\":false,\"lastOpenedPlan\":\"original.json\"}");
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            assertEquals(ApplicationTheme.LIGHT, service.snapshot().getTheme());
            assertNull(service.snapshot().getDefaultPlanDirectory());
            service.savePreferences(ApplicationTheme.DARK, directory.toString()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            service.recordLastPlan("new.json");
            var restored = new JsonApplicationSettingsRepository(path).load();
            assertFalse(restored.isAutomaticallyOpenLastPlan()); assertEquals("new.json", restored.getLastOpenedPlan());
            assertEquals(ApplicationTheme.DARK, restored.getTheme()); assertEquals(directory.toString(), restored.getDefaultPlanDirectory());
            var detached = service.snapshot(); detached.setTheme(ApplicationTheme.LIGHT);
            assertEquals(ApplicationTheme.DARK, service.snapshot().getTheme());
        }
    }

    @Test void queuedPreferenceEditsDoNotOverwriteNewerLastPlan() throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("s.json")))) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            service.background(() -> { entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); } return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var pending = service.savePreferences(ApplicationTheme.DARK, directory.toString());
            service.recordLastPlan("most-recent.json"); release.countDown(); pending.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("most-recent.json", service.snapshot().getLastOpenedPlan());
            service.recordLastPlan("after.json"); assertEquals(ApplicationTheme.DARK, service.snapshot().getTheme());
        }
    }

    @Test void corruptUnsupportedDuplicateTrailingAndOversizedDocumentsArePreserved() throws Exception {
        for (var json : List.of("{", "null", "{\"theme\":\"FUTURE\"}", "{\"theme\":null}", "{\"futureVersion\":2}",
                "{\"theme\":\"LIGHT\",\"theme\":\"DARK\"}", "{} {}", " ".repeat(65537))) {
            var path = directory.resolve("settings.json"); Files.writeString(path, json);
            var repository = new JsonApplicationSettingsRepository(path);
            try (var service = new ApplicationSettingsService(repository)) {
                assertEquals(ApplicationTheme.LIGHT, service.snapshot().getTheme());
                assertFalse(repository.takeWarnings().isEmpty());
                assertThrows(ExecutionException.class, () -> service.savePreferences(ApplicationTheme.DARK, null).toCompletableFuture().get());
                assertEquals(json, Files.readString(path));
            }
        }
    }

    @Test void invalidDirectoryAndFailedAtomicCommitLeaveAcceptedPreferencesUnchanged() throws Exception {
        var file = directory.resolve("s.json"); new JsonApplicationSettingsRepository(file).save(new ApplicationSettings());
        var before = Files.readAllBytes(file);
        var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
        var repository = new JsonApplicationSettingsRepository(file, new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.REPLACE && failing.get()) throw new java.io.IOException("injected");
        }));
        try (var service = new ApplicationSettingsService(repository)) {
            assertThrows(ExecutionException.class, () -> service.savePreferences(ApplicationTheme.DARK, directory.resolve("missing").toString()).toCompletableFuture().get());
            assertThrows(ExecutionException.class, () -> service.savePreferences(ApplicationTheme.DARK, directory.toString()).toCompletableFuture().get());
            assertEquals(ApplicationTheme.LIGHT, service.snapshot().getTheme()); assertArrayEquals(before, Files.readAllBytes(file));
            failing.set(false);
            service.savePreferences(ApplicationTheme.DARK, directory.toString()).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(ApplicationTheme.DARK, new JsonApplicationSettingsRepository(file).load().getTheme());
        }
    }

    @Test void backgroundDirectoryQueueIsBoundedAndDoesNotRunOnCaller() throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("s.json")))) {
            var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
            var queued = new ArrayList<CompletableFuture<Boolean>>();
            try {
                for (int i = 0; i < 2; i++) service.background(() -> {
                    entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return null;
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                for (int i = 0; i < 16; i++) queued.add(service.background(() -> true).toCompletableFuture());
                assertThrows(ExecutionException.class, () -> service.background(() -> true).toCompletableFuture().get(1, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            assertTrue(service.background(() -> Thread.currentThread().getName()).toCompletableFuture().get(5, TimeUnit.SECONDS).startsWith("application-directory-probe"));
        }
    }

    @Test void directoryPrecedenceFallbackAndNativeDefaultAreExact() {
        var settings = new ApplicationSettings(); settings.setDefaultPlanDirectory(directory.resolve("preferred").toString());
        settings.setLastOpenedPlan(directory.resolve("last/plan.json").toString());
        var active = directory.resolve("active/plan.json"); var home = directory.resolve("home");
        var preferred = Path.of(settings.getDefaultPlanDirectory());
        assertEquals(preferred, PlanDirectoryResolver.resolve(settings, active, home.toString(), p -> true).directory());
        var fallback = PlanDirectoryResolver.resolve(settings, active, home.toString(), p -> !p.equals(preferred));
        assertEquals(active.getParent(), fallback.directory()); assertTrue(fallback.preferredUnavailable());
        assertEquals(directory.resolve("last"), PlanDirectoryResolver.resolve(settings, active, home.toString(), p -> p.equals(directory.resolve("last"))).directory());
        assertEquals(home, PlanDirectoryResolver.resolve(settings, active, home.toString(), p -> p.equals(home)).directory());
        assertNull(PlanDirectoryResolver.resolve(settings, active, home.toString(), p -> false).directory());
        settings.setDefaultPlanDirectory("\u0000");
        assertTrue(PlanDirectoryResolver.resolve(settings, null, home.toString(), p -> true).preferredUnavailable());
        assertFalse(PlanDirectoryResolver.usable(directory.resolve("missing").toString()));
        settings.setDefaultPlanDirectory(null);
        assertFalse(PlanDirectoryResolver.resolve(settings, null, home.toString(), p -> false).preferredUnavailable());
    }

    @Test void externallyChangedUnsupportedPreferencesAreNeverReplaced() throws Exception {
        var path = directory.resolve("s.json");
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            service.recordLastPlan("first.json");
            var future = "{\"theme\":\"DARK\",\"futureVersion\":99}";
            Files.writeString(path, future);
            assertThrows(ExecutionException.class, () -> service.savePreferences(ApplicationTheme.DARK, null).toCompletableFuture().get());
            assertThrows(RuntimeException.class, () -> service.recordLastPlan("second.json"));
            assertEquals(future, Files.readString(path)); assertEquals("first.json", service.snapshot().getLastOpenedPlan());
        }
    }

    @Test void relativeDirectoryIsFrozenAsAbsolutePreference() throws Exception {
        try (var service = new ApplicationSettingsService(new JsonApplicationSettingsRepository(directory.resolve("s.json")))) {
            service.savePreferences(ApplicationTheme.DARK, ".").toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(Path.of(".").toAbsolutePath().normalize().toString(), service.snapshot().getDefaultPlanDirectory());
        }
    }
}
