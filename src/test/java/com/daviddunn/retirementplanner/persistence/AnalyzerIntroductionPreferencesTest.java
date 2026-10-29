package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.settings.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerIntroductionPreferencesTest {
    @TempDir Path directory;

    @Test void legacyDefaultsSixIndependentFlagsAndRestartPreserveOtherSettings() throws Exception {
        var path=directory.resolve("settings.json");
        Files.writeString(path,"{\"automaticallyOpenLastPlan\":false,\"lastOpenedPlan\":\"original.json\",\"theme\":\"DARK\"}");
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            for(var kind:AnalyzerIntroduction.values())assertTrue(service.snapshot().showsIntroduction(kind));
            for(var kind:AnalyzerIntroduction.values()) {
                service.suppressIntroduction(kind).toCompletableFuture().get(5,TimeUnit.SECONDS);
                var restored=new JsonApplicationSettingsRepository(path).load();
                assertTrue(Files.readString(path).contains(kind.name()));assertFalse(restored.showsIntroduction(kind));assertEquals(ApplicationTheme.DARK,restored.getTheme());
                assertFalse(restored.isAutomaticallyOpenLastPlan());assertEquals("original.json",restored.getLastOpenedPlan());
            }
            assertEquals(Set.of(AnalyzerIntroduction.values()),service.snapshot().getSuppressedAnalyzerIntroductions());
            assertThrows(UnsupportedOperationException.class,()->service.snapshot().getSuppressedAnalyzerIntroductions().clear());
        }
        try(var restarted=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            for(var kind:AnalyzerIntroduction.values())assertFalse(restarted.snapshot().showsIntroduction(kind));
            restarted.savePreferences(ApplicationTheme.LIGHT,null).toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(6,restarted.snapshot().getSuppressedAnalyzerIntroductions().size());
            restarted.savePreferences(ApplicationTheme.DARK,null,true).toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertTrue(restarted.snapshot().getSuppressedAnalyzerIntroductions().isEmpty());
        }
    }

    @Test void queuedFlagsResetAndLastPlanUpdatesMergeAtTheSameAtomicBoundary() throws Exception {
        var path=directory.resolve("settings.json");
        try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
            var futures=new ArrayList<CompletableFuture<?>>();
            for(var kind:AnalyzerIntroduction.values())futures.add(service.suppressIntroduction(kind).toCompletableFuture());
            futures.add(service.recordLastPlanAsync("latest.json").toCompletableFuture());
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
            assertEquals(6,service.snapshot().getSuppressedAnalyzerIntroductions().size());
            service.savePreferences(ApplicationTheme.DARK,null,true).toCompletableFuture().get(5,TimeUnit.SECONDS);
            service.suppressIntroduction(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY).toCompletableFuture().get(5,TimeUnit.SECONDS);
            var restored=new JsonApplicationSettingsRepository(path).load();
            assertEquals(Set.of(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY),restored.getSuppressedAnalyzerIntroductions());
            assertEquals("latest.json",restored.getLastOpenedPlan());assertEquals(ApplicationTheme.DARK,restored.getTheme());
        }
    }

    @Test void atomicFailureRetainsFlagsAndDurableDocumentAndRetrySucceeds() throws Exception {
        var path=directory.resolve("settings.json");new JsonApplicationSettingsRepository(path).save(new ApplicationSettings());
        var before=Files.readAllBytes(path);var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        var repository=new JsonApplicationSettingsRepository(path,new AtomicFileWriter((stage,file)->{
            if(stage==AtomicFileWriter.Stage.REPLACE&&fail.get())throw new java.io.IOException("Injected write failure");
        }));
        try(var service=new ApplicationSettingsService(repository)) {
            assertThrows(ExecutionException.class,()->service.suppressIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS).toCompletableFuture().get(5,TimeUnit.SECONDS));
            assertArrayEquals(before,Files.readAllBytes(path));assertTrue(service.snapshot().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));
            fail.set(false);service.suppressIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS).toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertFalse(new JsonApplicationSettingsRepository(path).load().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));
            fail.set(true);
            assertThrows(ExecutionException.class,()->service.savePreferences(ApplicationTheme.DARK,null,true).toCompletableFuture().get(5,TimeUnit.SECONDS));
            assertFalse(service.snapshot().showsIntroduction(AnalyzerIntroduction.MONTE_CARLO_ANALYSIS));
            fail.set(false);service.savePreferences(ApplicationTheme.DARK,null,true).toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertTrue(new JsonApplicationSettingsRepository(path).load().getSuppressedAnalyzerIntroductions().isEmpty());
        }
    }

    @Test void malformedFutureAndOversizedSettingsAreNeverOverwrittenForSuppression() throws Exception {
        for(var json:List.of("{","{\"suppressedAnalyzerIntroductions\":[\"FUTURE\"]}","{\"suppressedAnalyzerIntroductions\":[null]}"," ".repeat(65537))) {
            var path=directory.resolve("settings.json");Files.writeString(path,json);
            try(var service=new ApplicationSettingsService(new JsonApplicationSettingsRepository(path))) {
                for(var kind:AnalyzerIntroduction.values())assertTrue(service.snapshot().showsIntroduction(kind));
                assertThrows(ExecutionException.class,()->service.suppressIntroduction(AnalyzerIntroduction.SOCIAL_SECURITY_ONLY).toCompletableFuture().get(5,TimeUnit.SECONDS));
                assertEquals(json,Files.readString(path));
            }
        }
    }
}
