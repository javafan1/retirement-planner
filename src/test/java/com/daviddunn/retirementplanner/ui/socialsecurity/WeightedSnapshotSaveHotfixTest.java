package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class WeightedSnapshotSaveHotfixTest {
    @TempDir Path directory;

    static AnalysisSnapshot completeUniverse() {
        var original = SnapshotFixtures.fullUniverseDtoOnly();
        var template = SnapshotFixtures.couple().couple();
        var couple = original.payload().couple();
        var orders = IntStream.rangeClosed(1, 5184).boxed().toList();
        var equivalence = new WeightedSnapshots.Equivalence(orders.stream().map(List::of).toList(), orders,
                5184, 5184, Duration.ofSeconds(2), template.equivalence().coverage());
        var payload = new WeightedSnapshots.Payload(new WeightedSnapshots.Couple(couple.objective(), couple.metadata(),
                couple.entries(), couple.rankedOrders(), template.baseline(), couple.work(), couple.elapsed(),
                couple.status(), equivalence, 0), null);
        var context = original.context();
        var comparisons = new ArrayList<>(context.historicalComparisons());
        comparisons.add(new SnapshotContext.HistoricalComparison(AnalysisKind.COUPLE_EXHAUSTIVE, null, null,
                "Frozen strategy-detail ranks", couple.entries().stream().map(e -> new SnapshotContext.ComparisonRow(
                        "Strategy detail", e.strategy(), e.rank(), null, null, null, null, null, null)).toList()));
        var fullContext = new SnapshotContext(context.household(), context.primary(), context.spouse(), context.startedAt(),
                context.assumptions(), context.request(), context.comparisons(), context.preferences(), context.baseline(), comparisons);
        return new AnalysisSnapshot(1, 1, original.planId(), original.kind(), original.shape(), original.runId(),
                original.completedAt(), original.applicationBuild(), AnalysisVersions.algorithms(original.kind()), original.references(),
                original.inputFingerprint(), original.contextFingerprint(), new AnalysisSnapshotCodec().digest(payload, fullContext),
                payload, fullContext, null);
    }

    @Test void complete5184CandidateSnapshotSavesAndRestoresWithoutCalculation() throws Exception {
        var snapshot = completeUniverse();
        var codec = new AnalysisSnapshotCodec();
        var bytes = codec.encode(snapshot);
        System.out.println("HOTFIX_FULL_SNAPSHOT candidates=5184 rawBytes=" + codec.raw(snapshot).length + " gzipBytes=" + bytes.length);
        assertEquals(snapshot, codec.decode(bytes));
        var repository = new JsonAnalysisSnapshotRepository(directory);
        repository.save(snapshot);
        assertEquals(snapshot, repository.load(snapshot.planId(), snapshot.kind()).orElseThrow());
        var completion = completion(snapshot);
        var store = new AnalysisResultStore(repository);
        var slot = new AnalysisResultStore.Slot(snapshot.planId(), snapshot.kind());
        store.accept(completion);
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
        assertEquals(snapshot.runId(), repository.load(snapshot.planId(), snapshot.kind()).orElseThrow().runId());
        assertEquals(snapshot, repository.loadBackup(snapshot.planId(), snapshot.kind()).orElseThrow());
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
        store.shutdownAfterSuccessfulDrain();
    }

    private AcceptedAnalysisCompletion completion(AnalysisSnapshot snapshot) {
        return new AcceptedAnalysisCompletion(snapshot.planId(), directory.resolve("plan.json"), snapshot.kind(), snapshot.runId(),
                snapshot.completedAt(), snapshot.inputFingerprint(), snapshot.contextFingerprint(), snapshot.algorithms(), snapshot.references(),
                snapshot.payload(), snapshot.context());
    }

    @Test void duplicatePlanBindingIsDistinguishedFromSnapshotEncodingFailure() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(directory.resolve("results"));
        var snapshot = completeUniverse();
        var accepted = completion(snapshot);
        var existing = directory.resolve("original.json");
        Files.writeString(existing, "disposable original");
        Files.writeString(accepted.planFile(), "disposable copy");
        repository.claimPlan(snapshot.planId(), existing);
        repository.save(snapshot);
        var store = new AnalysisResultStore(repository);
        var slot = new AnalysisResultStore.Slot(snapshot.planId(), snapshot.kind());
        store.accept(accepted);
        var failed = store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(failed.explanation().contains("Failure category: plan identity binding"));
        assertEquals(AnalysisSnapshotSaveException.Stage.PLAN_BINDING, store.failure(slot).orElseThrow().stage());
        assertEquals(-1, store.failure(slot).orElseThrow().rawBytes());
        assertEquals(snapshot, repository.load(snapshot.planId(), snapshot.kind()).orElseThrow());
        // Remove only this test's disposable duplicate to exercise the existing legitimate move/retry behavior.
        Files.delete(existing);
        store.retry(slot);
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
        store.shutdownAfterSuccessfulDrain();
    }

    @Test void originalExceptionIsRetainedAndFullPrivateSafeTraceIsLoggedOnSaveAndRetry() throws Exception {
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(AnalysisResultStore.class.getName());
        var handler = new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        var repository = new AnalysisResultStoreTest.Memory();
        var original = new IOException("private financial contents", new IllegalArgumentException("private nested contents"));
        original.addSuppressed(new IOException("private suppressed contents"));
        var calls = new int[1];
        var faulty = new AnalysisSnapshotRepository() {
            public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) { return repository.load(id, kind); }
            public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) { return Optional.empty(); }
            public void save(AnalysisSnapshot snapshot) throws IOException {
                calls[0]++;
                if (calls[0] < 3) throw original;
                repository.save(snapshot);
            }
        };
        var accepted = completion(completeUniverse());
        var slot = new AnalysisResultStore.Slot(accepted.planId(), accepted.kind());
        var store = new AnalysisResultStore(faulty);
        try {
            store.accept(accepted);
            for (int attempt = 0; attempt < 2; attempt++) {
                var status = store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, status.durability());
                assertTrue(status.explanation().contains("Failure category: transactional file writing"));
                assertSame(original, store.failure(slot).orElseThrow().getCause());
                assertSame(accepted, store.inMemory(slot).orElseThrow());
                store.retry(slot);
            }
            assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
            assertTrue(store.failure(slot).isEmpty());
            assertEquals(3, calls[0]);
            assertEquals(2, records.size());
            for (var record : records) {
                assertTrue(record.getMessage().contains("kind=COUPLE_WEIGHTED stage=ATOMIC_WRITE"));
                assertTrue(record.getMessage().contains("maxRawBytes=33554432"));
                assertTrue(record.getMessage().contains("maxTokens=2000000"));
                var trace = record.getThrown().getCause();
                assertArrayEquals(original.getStackTrace(), trace.getStackTrace());
                assertArrayEquals(original.getCause().getStackTrace(), trace.getCause().getStackTrace());
                assertEquals(1, trace.getSuppressed().length);
                String rendered = new SimpleFormatter().format(record);
                assertTrue(rendered.contains("java.io.IOException"));
                assertTrue(rendered.contains("java.lang.IllegalArgumentException"));
                assertTrue(rendered.contains("Caused by:"));
                assertTrue(rendered.contains("Suppressed:"));
                assertFalse(rendered.contains("private financial"));
                assertFalse(rendered.contains("private nested"));
                assertFalse(rendered.contains("private suppressed"));
            }
        } finally {
            logger.removeHandler(handler);
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
            store.shutdownAfterSuccessfulDrain();
        }
    }

    @Test void encodingLimitFailureReportsActualSizeAndPreservesPreviousSnapshot() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(directory.resolve("results"));
        var first = completeUniverse();
        repository.save(first);
        byte[] before = Files.readAllBytes(repository.path(first.planId(), first.kind()));
        var couple = first.payload().couple();
        var entries = new ArrayList<>(couple.entries());
        var entry = entries.getFirst();
        var aggregate = entry.aggregate();
        entries.set(0, new WeightedSnapshots.Entry(entry.order(), entry.strategy(),
                new com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyAggregate(
                        new BigDecimal("1".repeat(AnalysisSnapshotCodec.MAX_NUMBER_LENGTH + 1)), aggregate.expectedNominalEstateAtSecondDeath(),
                        aggregate.expectedInvestableAssetsAtSecondDeath(), aggregate.minimumNominalScenarioEstate(),
                        aggregate.maximumNominalScenarioEstate(), aggregate.totalEvaluatedProbability(), aggregate.originalScenarioCount(),
                        aggregate.actualProjectionRunCount()), entry.failure(), entry.details(), entry.rank(), entry.pvDifference(), entry.nominalDifference()));
        var payload = new WeightedSnapshots.Payload(new WeightedSnapshots.Couple(couple.objective(), couple.metadata(), entries,
                couple.rankedOrders(), couple.baseline(), couple.work(), couple.elapsed(), couple.status(), couple.equivalence(), 0), null);
        var accepted = new AcceptedAnalysisCompletion(first.planId(), directory.resolve("plan.json"), first.kind(), UUID.randomUUID(),
                first.completedAt(), first.inputFingerprint(), first.contextFingerprint(), first.algorithms(), first.references(), payload, first.context());
        var store = new AnalysisResultStore(repository);
        var slot = new AnalysisResultStore.Slot(first.planId(), first.kind());
        store.accept(accepted);
        assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
        var failure = store.failure(slot).orElseThrow();
        assertEquals(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT, failure.stage());
        assertTrue(failure.rawBytes() > 0);
        assertEquals(-1, failure.compressedBytes());
        assertInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class, failure.getCause().getCause());
        assertArrayEquals(before, Files.readAllBytes(repository.path(first.planId(), first.kind())));
        assertSame(accepted, store.inMemory(slot).orElseThrow());
        assertFalse(store.drain(Duration.ofSeconds(10)).safeToExit());
        // Release this test's worker by accepting the known original successful fixture after the failure assertions.
        store.resumeAfterCancelledExit();
        store.accept(completion(first));
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
        store.shutdownAfterSuccessfulDrain();
    }
}
