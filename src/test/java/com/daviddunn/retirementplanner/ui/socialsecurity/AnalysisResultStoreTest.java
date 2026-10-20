package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultStoreTest {
    @TempDir Path dir;
    static class Memory implements AnalysisSnapshotRepository {
        volatile AnalysisSnapshot saved;
        volatile boolean fail;
        public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) { return Optional.ofNullable(saved).filter(s -> s.planId().equals(id) && s.kind() == kind); }
        public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) { return Optional.empty(); }
        public void save(AnalysisSnapshot s) throws IOException { if (fail) throw new IOException("injected"); saved = s; }
    }
    static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> work = new ArrayDeque<>(); boolean closed;
        public void execute(Runnable r) { work.add(r); }
        public void shutdown() { closed = true; }
        public List<Runnable> shutdownNow() { closed = true; return List.of(); }
        public boolean isShutdown() { return closed; }
        public boolean isTerminated() { return closed && work.isEmpty(); }
        public boolean awaitTermination(long n, TimeUnit u) { return isTerminated(); }
    }
    @Test void failureRetainsLastSuccessAndCanRetryWithoutRecalculation() throws Exception {
        var repo = new Memory(); var store = new AnalysisResultStore(repo); var id = UUID.randomUUID();
        var first = SnapshotFixtures.completion(id, dir.resolve("plan.json")); store.accept(first);
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit());
        var saved = repo.saved; store.resumeAfterCancelledExit(); repo.fail = true;
        var second = SnapshotFixtures.completion(id, dir.resolve("plan.json")); store.accept(second);
        assertFalse(store.drain(Duration.ofSeconds(5)).safeToExit()); assertEquals(saved, repo.saved);
        var slot = new AnalysisResultStore.Slot(id, first.kind());
        assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, store.status(slot).orElseThrow().durability());
        assertEquals(second, store.inMemory(slot).orElseThrow());
        store.resumeAfterCancelledExit(); repo.fail = false; store.retry(slot);
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit());
        assertEquals(second.runId(), repo.saved.runId()); store.shutdownAfterSuccessfulDrain();
    }
    @Test void writesAreSerializedOriginalBindingIsFrozenAndLatestWins() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repo = new Memory() {
            @Override public void save(AnalysisSnapshot s) throws IOException {
                if (saved == null) {
                    entered.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                }
                super.save(s);
            }
        };
        var store = new AnalysisResultStore(repo); var id = UUID.randomUUID();
        var first = SnapshotFixtures.completion(id, dir.resolve("old.json")); store.accept(first);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var obsolete = SnapshotFixtures.completion(id, dir.resolve("old.json")); store.accept(obsolete);
        var latest = SnapshotFixtures.completion(id, dir.resolve("old.json")); store.accept(latest);
        assertEquals(id, store.inMemory(new AnalysisResultStore.Slot(id, latest.kind())).orElseThrow().planId());
        assertFalse(store.drain(Duration.ofMillis(1)).complete());
        release.countDown(); assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit());
        assertEquals(latest.runId(), repo.saved.runId());
        assertThrows(IllegalStateException.class, () -> store.accept(first));
        store.shutdownAfterSuccessfulDrain();
    }
    @Test void boundedQueueReportsBackpressureAndPreservesAcceptedMemory() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repo = new Memory() {
            @Override public void save(AnalysisSnapshot s) throws IOException {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                super.save(s);
            }
        };
        var store = new AnalysisResultStore(repo); var id = UUID.randomUUID();
        store.accept(SnapshotFixtures.completion(id, dir.resolve("p.json"))); assertTrue(entered.await(5, TimeUnit.SECONDS));
        AnalysisResultStore.Status status = null;
        for (int i = 0; i < 12; i++) status = store.accept(SnapshotFixtures.completion(id, dir.resolve("p.json")));
        assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, status.durability());
        var slot = new AnalysisResultStore.Slot(id, AnalysisKind.COUPLE_WEIGHTED);
        var retained = store.inMemory(slot).orElseThrow(); release.countDown();
        assertFalse(store.drain(Duration.ofSeconds(5)).safeToExit()); store.resumeAfterCancelledExit(); store.retry(slot);
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit()); assertEquals(retained.runId(), repo.saved.runId());
        store.shutdownAfterSuccessfulDrain();
    }
    @Test void originalPublicationGatesExcludeCancelFailureStaleAndClosedCallbacks() throws Exception {
        for (String mode : List.of("cancel", "stale", "close", "fatal", "accepted")) {
            var worker = new ManualExecutor(); var coordinator = new SocialSecurityAnalysisJobCoordinator(worker);
            var ui = new ArrayDeque<Runnable>(); var jobs = new SocialSecurityAnalyzerJobController(coordinator, ui::add);
            var repo = new Memory(); var store = new AnalysisResultStore(repo); var id = UUID.randomUUID();
            var completion = SnapshotFixtures.completion(id, dir.resolve("p.json"));
            jobs.start(SocialSecurityAnalyzerJobController.Mode.WEIGHTED, (p, c) -> {
                if (mode.equals("fatal")) throw new IllegalStateException("fatal");
                return completion;
            }, value -> { store.accept(value); jobs.close(); }, failure -> { });
            worker.work.remove().run();
            switch (mode) {
                case "cancel" -> jobs.cancel();
                case "stale" -> jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
                case "close" -> jobs.close();
                default -> { }
            }
            while (!ui.isEmpty()) ui.remove().run();
            assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit());
            assertEquals(mode.equals("accepted"), repo.saved != null);
            if (repo.saved != null) assertEquals(completion.runId(), repo.saved.runId());
            jobs.close(); coordinator.close(); store.shutdownAfterSuccessfulDrain();
        }
    }
    @Test void partialSuccessIsAcceptedButAllFailedCannotReplace() {
        var payload = SnapshotFixtures.couple(); var result = payload.couple().restore();
        assertTrue(AnalysisSuccessPolicy.accepted(result)); assertTrue(payload.successful());
        var failure = result.orderedEntries().getLast();
        var failed = new LongevityWeightedIntegratedStrategyComparisonEntry(1, failure.strategy(), failure.aggregate(), failure.failure(),
                failure.scenarioDetails(), failure.rank(), failure.pvDifferenceFromBaseline(), failure.nominalDifferenceFromBaseline());
        var allFailed = new LongevityWeightedIntegratedStrategyComparisonResult(result.objective(), result.metadata(), List.of(failed),
                List.of(), Optional.empty(), result.work(), result.elapsedTime(), result.status());
        assertFalse(AnalysisSuccessPolicy.accepted(allFailed));
        var bad = new WeightedSnapshots.Payload(WeightedSnapshots.Couple.from(allFailed), null);
        assertThrows(IllegalArgumentException.class, () -> new AcceptedAnalysisCompletion(UUID.randomUUID(), dir.resolve("p"),
                AnalysisKind.COUPLE_WEIGHTED, UUID.randomUUID(), java.time.Instant.now(), SnapshotFixtures.HASH, SnapshotFixtures.HASH,
                Map.of(), Map.of(), bad, SnapshotFixtures.context()));
    }
}
