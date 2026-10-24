package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.time.Duration;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisParameterCoordinatorTest {
    @TempDir Path directory;
    final AnalysisKind kind = AnalysisKind.COUPLE_QUICK;
    @Test void ordinarySaveReceiptDoesNotFalselyMarkLaterAcceptanceDurable() throws Exception {
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path file, UUID identity, AnalysisParameterConfiguration configuration) throws IOException {
                throw new IOException("Injected background write failure");
            }
        };
        var plan = RetirementPlanFactory.createEmptyPlan(); Path file = directory.resolve("plan.json"); repository.save(plan, file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var accepted = ParameterFixtures.configuration(kind);
            coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, file), 1, accepted));
            coordinator.drain(Duration.ofSeconds(5));
            // The completed ordinary save contained no parameters; live memory has a later acceptance.
            coordinator.saved(plan, file);
            assertEquals(AnalysisParameterCoordinator.Status.SAVE_FAILED, coordinator.state(plan, kind).orElseThrow().status());
            repository.save(plan, file); coordinator.saved(plan, file);
            assertEquals(AnalysisParameterCoordinator.Status.SAVED, coordinator.state(plan, kind).orElseThrow().status());
        } finally { coordinator.shutdown(); }
    }
    @Test void unknownFutureMetadataIsProtectedWhileNewAcceptedValuesRemainInMemory() {
        var coordinator = new AnalysisParameterCoordinator(null);
        try {
            var plan = RetirementPlanFactory.createEmptyPlan();
            var future = AnalysisParameterConfigurations.parse("{\"schemaVersion\":2,\"future\":1.2300}");
            plan.setAnalyzerConfigurations(future);
            var accepted = ParameterFixtures.configuration(kind);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, null), 1, accepted)));
            assertSame(future, plan.getAnalyzerConfigurations()); assertEquals(accepted, coordinator.lastAccepted(plan, kind).orElseThrow());
            assertEquals(AnalysisParameterCoordinator.Status.SAVE_FAILED, coordinator.state(plan, kind).orElseThrow().status());
            assertTrue(coordinator.failure(plan, kind).isPresent());
        } finally { coordinator.shutdown(); }
    }
    @Test void exitFreezeRejectsLateAcceptanceAndCancelledExitCanResume() {
        var coordinator = new AnalysisParameterCoordinator(null);
        try {
            var plan = RetirementPlanFactory.createEmptyPlan(); var binding = coordinator.bind(plan, null);
            coordinator.beginExit();
            assertFalse(coordinator.accept(new AcceptedAnalysisParameters(binding, 1, ParameterFixtures.configuration(kind))));
            coordinator.resumeAfterCancelledExit();
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(binding, 2, ParameterFixtures.configuration(kind))));
        } finally { coordinator.shutdown(); }
    }
    @ParameterizedTest @EnumSource(AnalysisKind.class)
    void everyKindPersistsWithoutFinancialRequests(AnalysisKind analysisKind) throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var plan = analysisKind.shape() == AnalysisKind.Shape.COUPLE ? RetirementPlanFactory.createEmptyPlan() : RetirementPlanFactory.createSinglePersonPlan();
        Path file = directory.resolve("plan.json"); repository.save(plan, file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var configuration = ParameterFixtures.configuration(analysisKind);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, file), coordinator.nextAcceptanceSequence(), configuration)));
            var drained = coordinator.drain(Duration.ofSeconds(5)); assertTrue(drained.complete()); assertEquals(0, drained.failed());
            assertEquals(configuration, new JsonRetirementPlanRepository().load(file).getAnalyzerConfigurations().get(analysisKind).orElseThrow());
            // Empty plans deliberately have no birthdays/accounts; a financial calculation could not complete here.
            assertNull(plan.getHousehold().getPrimaryPerson().getBirthDate());
        } finally { coordinator.shutdown(); }
    }

    @Test void rapidAcceptedReplacementsAndOrdinarySaveRetainLatestConfiguration() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration config) throws IOException {
                entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
                catch (InterruptedException e) { throw new IOException(e); } super.saveParameters(path, id, config);
            }
        };
        var plan = RetirementPlanFactory.createEmptyPlan(); Path file = directory.resolve("plan.json"); repository.save(plan, file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var binding = coordinator.bind(plan, file);
            coordinator.accept(new AcceptedAnalysisParameters(binding, 1, ParameterFixtures.configuration(kind)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var newest = ParameterFixtures.configuration(kind);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(binding, 2, newest)));
            repository.save(plan, file); coordinator.saved(plan, file);
            release.countDown(); coordinator.shutdown();
            assertTrue(coordinator.drain(Duration.ofSeconds(5)).complete());
            assertEquals(newest, repository.load(file).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { release.countDown(); coordinator.shutdown(); }
    }

    @Test void boundedQueueRejectsExcessIoButRetainsAcceptedMemoryForRetry() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration config) throws IOException {
                entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
                catch (InterruptedException e) { throw new IOException(e); } super.saveParameters(path, id, config);
            }
        };
        var plan = RetirementPlanFactory.createEmptyPlan(); Path file = directory.resolve("plan.json"); repository.save(plan, file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var binding = coordinator.bind(plan, file);
            coordinator.accept(new AcceptedAnalysisParameters(binding, 1, ParameterFixtures.configuration(kind)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            AnalysisParameterConfiguration last = null;
            for (int index = 2; index <= 35; index++) {
                last = ParameterFixtures.configuration(kind); assertTrue(coordinator.accept(new AcceptedAnalysisParameters(binding, index, last)));
            }
            assertEquals(AnalysisParameterCoordinator.Status.SAVE_FAILED, coordinator.state(plan, kind).orElseThrow().status());
            assertEquals(last, plan.getAnalyzerConfigurations().get(kind).orElseThrow());
            release.countDown();
            // A shutdown drains existing queue; a rejected latest event remains honestly retryable/failed.
            coordinator.shutdown(); var result = coordinator.drain(Duration.ofSeconds(5)); assertEquals(1, result.failed());
        } finally { release.countDown(); coordinator.shutdown(); }
    }
    @Test void unsavedMemoryOnlyThenOrdinarySaveAndIndependentSaveAs() throws Exception {
        var repository = new JsonRetirementPlanRepository(); var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var plan = RetirementPlanFactory.createEmptyPlan();
            var binding = coordinator.bind(plan, null); var configuration = ParameterFixtures.configuration(kind);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(binding, 1, configuration)));
            assertEquals(AnalysisParameterCoordinator.Status.MEMORY_ONLY, coordinator.state(plan, kind).orElseThrow().status());
            Path file = directory.resolve("first.json"); repository.save(plan, file); coordinator.saved(plan, file);
            assertEquals(AnalysisParameterCoordinator.Status.SAVED, coordinator.state(plan, kind).orElseThrow().status());
            String sourceId = plan.getPlanId(); byte[] original = Files.readAllBytes(file);
            Path copy = directory.resolve("second.json"); repository.saveWithIdentity(plan, copy, UUID.randomUUID()); coordinator.saved(plan, copy);
            assertNotEquals(sourceId, plan.getPlanId()); assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(configuration, repository.load(copy).getAnalyzerConfigurations().get(kind).orElseThrow());
            assertFalse(coordinator.accept(new AcceptedAnalysisParameters(binding, 2, ParameterFixtures.configuration(kind))));
        } finally { coordinator.shutdown(); }
    }
    @Test void legacyPlanRemainsMemoryOnlyUntilExplicitMigrationSave() throws Exception {
        var repository = new JsonRetirementPlanRepository(); Path file = directory.resolve("legacy.json");
        repository.save(RetirementPlanFactory.createEmptyPlan(), file);
        Files.write(file, com.daviddunn.retirementplanner.persistence.PlanConfigurationTransaction.replace(Files.readAllBytes(file), "planId", null));
        var plan = repository.load(file); assertNull(plan.getPlanId()); byte[] original = Files.readAllBytes(file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, file), 1, ParameterFixtures.configuration(kind))));
            assertEquals(AnalysisParameterCoordinator.Status.MEMORY_ONLY, coordinator.state(plan, kind).orElseThrow().status());
            assertArrayEquals(original, Files.readAllBytes(file)); assertNull(plan.getPlanId());
            repository.save(plan, file); coordinator.saved(plan, file); assertNotNull(plan.getPlanId());
        } finally { coordinator.shutdown(); }
    }
    @Test void failureRetainsMemoryRetryNeverCalculatesAndShutdownReportsHonestly() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration config) throws IOException {
                writes.incrementAndGet(); if (fail.get()) throw new IOException("Injected persistence-only failure"); super.saveParameters(path, id, config);
            }
        };
        var plan = RetirementPlanFactory.createEmptyPlan(); Path file = directory.resolve("plan.json"); repository.save(plan, file);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var configuration = ParameterFixtures.configuration(kind);
            assertTrue(coordinator.accept(new AcceptedAnalysisParameters(coordinator.bind(plan, file), 1, configuration)));
            assertEquals(1, coordinator.drain(Duration.ofSeconds(5)).failed());
            assertEquals(configuration, plan.getAnalyzerConfigurations().get(kind).orElseThrow());
            assertNull(repository.load(file).getAnalyzerConfigurations());
            fail.set(false); assertTrue(coordinator.retry(plan, kind));
            assertEquals(0, coordinator.drain(Duration.ofSeconds(5)).failed());
            assertEquals(2, writes.get()); assertEquals(configuration, repository.load(file).getAnalyzerConfigurations().get(kind).orElseThrow());
        } finally { coordinator.shutdown(); }
    }
    @Test void pendingWritesDrainAfterClosureAndNeverRedirectOnPlanSwitch() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repository = new JsonRetirementPlanRepository() {
            @Override public void saveParameters(Path path, UUID id, AnalysisParameterConfiguration config) throws IOException {
                entered.countDown(); try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out"); }
                catch (InterruptedException e) { throw new IOException(e); } super.saveParameters(path, id, config);
            }
        };
        var first = RetirementPlanFactory.createEmptyPlan(); var second = RetirementPlanFactory.createEmptyPlan();
        Path a = directory.resolve("a.json"), b = directory.resolve("b.json"); repository.save(first, a); repository.save(second, b);
        var coordinator = new AnalysisParameterCoordinator(repository);
        try {
            var oldBinding = coordinator.bind(first, a); var configuration = ParameterFixtures.configuration(kind);
            coordinator.accept(new AcceptedAnalysisParameters(oldBinding, 2, configuration)); assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(coordinator.drain(Duration.ofMillis(1)).complete());
            assertFalse(coordinator.accept(new AcceptedAnalysisParameters(oldBinding, 1, ParameterFixtures.configuration(kind))));
            coordinator.bind(second, b);
            assertFalse(coordinator.accept(new AcceptedAnalysisParameters(oldBinding, 3, ParameterFixtures.configuration(kind))));
            coordinator.shutdown(); release.countDown(); assertTrue(coordinator.drain(Duration.ofSeconds(5)).complete());
            assertEquals(configuration, repository.load(a).getAnalyzerConfigurations().get(kind).orElseThrow());
            assertNull(repository.load(b).getAnalyzerConfigurations());
        } finally { release.countDown(); coordinator.shutdown(); }
    }
}
