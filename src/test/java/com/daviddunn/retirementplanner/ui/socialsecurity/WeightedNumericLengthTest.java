package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyAggregate;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.persistence.*;
import com.fasterxml.jackson.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class WeightedNumericLengthTest {
    @TempDir Path directory;

    static BigDecimal compounded1070() {
        return compounded("0.123456");
    }
    static BigDecimal compounded(String probability) {
        var portfolio = new ProjectedPortfolio(List.of(), new BigDecimal("10000000.00"));
        for (int year = 0; year < 62; year++) portfolio = portfolio.applyGrowth(new BigDecimal("0.00000000000000001"));
        // Same exact multiply used for probability-weighted estate/PV aggregation; no rounding or padding.
        return portfolio.getRetainedNonQualifiedAssets().multiply(new BigDecimal(probability));
    }

    static AnalysisSnapshot withNumber(AnalysisSnapshot original, BigDecimal value) {
        var couple = original.payload().couple();
        var entries = new ArrayList<>(couple.entries());
        var first = entries.getFirst();
        var a = first.aggregate();
        entries.set(0, new WeightedSnapshots.Entry(first.order(), first.strategy(), new LongevityWeightedStrategyAggregate(
                value, a.expectedNominalEstateAtSecondDeath(), a.expectedInvestableAssetsAtSecondDeath(),
                a.minimumNominalScenarioEstate(), a.maximumNominalScenarioEstate(), a.totalEvaluatedProbability(),
                a.originalScenarioCount(), a.actualProjectionRunCount()), first.failure(), first.details(), first.rank(), first.pvDifference(), first.nominalDifference()));
        var payload = new WeightedSnapshots.Payload(new WeightedSnapshots.Couple(couple.objective(), couple.metadata(), entries,
                couple.rankedOrders(), couple.baseline(), couple.work(), couple.elapsed(), couple.status(), couple.equivalence(), couple.evaluationsAvoided()), null);
        return new AnalysisSnapshot(original.schemaVersion(), original.fingerprintVersion(), original.planId(), original.kind(), original.shape(),
                original.runId(), original.completedAt(), original.applicationBuild(), original.algorithms(), original.references(),
                original.inputFingerprint(), original.contextFingerprint(), new AnalysisSnapshotCodec().digest(payload, original.context()), payload, original.context(), null);
    }

    @ParameterizedTest @ValueSource(ints = {1024, 1070, 4096})
    void exactNumbersPreserveValueAndScale(int length) throws Exception {
        var value = length == 1070 ? compounded1070() : new BigDecimal("1234567." + "1".repeat(length - 8));
        assertEquals(length, value.toString().length());
        var snapshot = withNumber(WeightedSnapshotSaveHotfixTest.completeUniverse(), value);
        var codec = new AnalysisSnapshotCodec();
        assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
        var restored = codec.decode(codec.encode(snapshot)).payload().couple().entries().getFirst().aggregate().expectedPvAfterTaxEstate();
        assertEquals(value, restored);
        assertEquals(value.scale(), restored.scale());
    }

    @Test void productionLikeTokenReproducesOldConstraintAndLogsOnlyStructuralDiagnostics() throws Exception {
        var value = compounded1070();
        assertEquals(1070, value.toString().length());
        var snapshot = withNumber(WeightedSnapshotSaveHotfixTest.completeUniverse(), value);
        var codec = new AnalysisSnapshotCodec();
        byte[] raw = codec.raw(snapshot);
        var old = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(1024).build()).build();
        try (var parser = old.createParser(raw)) {
            var failure = assertThrows(com.fasterxml.jackson.core.exc.StreamConstraintsException.class, () -> { while (parser.nextToken() != null) { } });
            // Jackson's floating-point length excludes the decimal point; our text-token limit includes it.
            assertTrue(failure.getMessage().contains("1069"), failure.getMessage());
            assertTrue(parser.getParsingContext().pathAsPointer().toString().endsWith("/entries/0/aggregate/expectedPvAfterTaxEstate"));
        }
        var capturedLength = withNumber(snapshot, compounded("0.1234560"));
        try (var parser = old.createParser(codec.raw(capturedLength))) {
            var failure = assertThrows(com.fasterxml.jackson.core.exc.StreamConstraintsException.class, () -> { while (parser.nextToken() != null) { } });
            assertTrue(failure.getMessage().contains("Number value length (1070)"), failure.getMessage());
        }
        var logger = Logger.getLogger(AnalysisSnapshotCodec.class.getName());
        var messages = new ArrayList<String>();
        var handler = new Handler() {
            public void publish(LogRecord record) { messages.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try { assertEquals(snapshot, codec.decode(codec.encode(snapshot))); }
        finally { logger.removeHandler(handler); }
        assertTrue(messages.stream().anyMatch(m -> m.contains("/payload/couple/entries/*/aggregate/expectedPvAfterTaxEstate") && m.contains("maximumLength=1070")));
        assertTrue(messages.stream().noneMatch(m -> m.contains(value.toString())));
        System.out.println("NUMERIC_PROOF path=/payload/couple/entries/0/aggregate/expectedPvAfterTaxEstate tokenLength=1070 scale=" + value.scale()
                + " rawBytes=" + raw.length + " gzipBytes=" + codec.encode(snapshot).length);
    }

    @Test void fullSnapshotRetryPersistsAcceptedLongNumberWithoutRecalculation() throws Exception {
        var snapshot = withNumber(WeightedSnapshotSaveHotfixTest.completeUniverse(), compounded1070());
        var disk = new JsonAnalysisSnapshotRepository(directory);
        var original = WeightedSnapshotSaveHotfixTest.completeUniverse().clonedTo(snapshot.planId());
        disk.save(original);
        byte[] before = Files.readAllBytes(disk.path(snapshot.planId(), snapshot.kind()));
        var fail = new boolean[] {true};
        var repository = new AnalysisSnapshotRepository() {
            public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) throws IOException { return disk.load(id, kind); }
            public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) throws IOException { return disk.loadBackup(id, kind); }
            public void claimPlan(UUID id, Path file) throws IOException { disk.claimPlan(id, file); }
            public void save(AnalysisSnapshot result) throws IOException { if (fail[0]) throw new IOException("Injected disk failure"); disk.save(result); }
        };
        var accepted = new AcceptedAnalysisCompletion(snapshot.planId(), directory.resolve("plan.json"), snapshot.kind(), snapshot.runId(),
                snapshot.completedAt(), snapshot.inputFingerprint(), snapshot.contextFingerprint(), snapshot.algorithms(), snapshot.references(), snapshot.payload(), snapshot.context());
        var slot = new AnalysisResultStore.Slot(snapshot.planId(), snapshot.kind());
        var store = new AnalysisResultStore(repository);
        store.accept(accepted);
        assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
        assertArrayEquals(before, Files.readAllBytes(disk.path(snapshot.planId(), snapshot.kind())));
        assertSame(accepted, store.inMemory(slot).orElseThrow());
        fail[0] = false; store.retry(slot);
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(slot).toCompletableFuture().get(10, TimeUnit.SECONDS).durability());
        assertEquals(accepted.presentationSnapshot(new AnalysisSnapshotCodec()), disk.load(snapshot.planId(), snapshot.kind()).orElseThrow());
        assertEquals(original, disk.loadBackup(snapshot.planId(), snapshot.kind()).orElseThrow());
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit()); store.shutdownAfterSuccessfulDrain();
    }

    @Test void all5184CandidatesWithLongExactAggregateValuesSaveVerifyAndReload() throws Exception {
        var original = WeightedSnapshotSaveHotfixTest.completeUniverse();
        var couple = original.payload().couple();
        var value = compounded("0.1234560");
        var entries = couple.entries().stream().map(e -> {
            var amount = value.subtract(BigDecimal.valueOf(e.order() - 1L));
            var a = e.aggregate();
            return new WeightedSnapshots.Entry(e.order(), e.strategy(), new LongevityWeightedStrategyAggregate(
                    amount, amount, amount, a.minimumNominalScenarioEstate(), amount, a.totalEvaluatedProbability(),
                    a.originalScenarioCount(), a.actualProjectionRunCount()), e.failure(), e.details(), e.rank(), e.pvDifference(), e.nominalDifference());
        }).toList();
        var payload = new WeightedSnapshots.Payload(new WeightedSnapshots.Couple(couple.objective(), couple.metadata(), entries,
                couple.rankedOrders(), couple.baseline(), couple.work(), couple.elapsed(), couple.status(), couple.equivalence(), couple.evaluationsAvoided()), null);
        var codec = new AnalysisSnapshotCodec();
        var snapshot = new AnalysisSnapshot(1, 1, original.planId(), original.kind(), original.shape(), original.runId(), original.completedAt(),
                original.applicationBuild(), original.algorithms(), original.references(), original.inputFingerprint(), original.contextFingerprint(),
                codec.digest(payload, original.context()), payload, original.context(), null);
        var measured = codec.encodeMeasured(snapshot);
        assertTrue(measured.rawBytes() > 27_000_000);
        assertTrue(measured.rawBytes() < AnalysisSnapshotCodec.MAX_RAW_BYTES);
        System.out.println("NUMERIC_FULL_UNIVERSE candidates=5184 longFieldsPerCandidate=4 tokenCharacters=1071 rawBytes="
                + measured.rawBytes() + " gzipBytes=" + measured.bytes().length);
        var repository = new JsonAnalysisSnapshotRepository(directory);
        repository.save(snapshot);
        assertEquals(snapshot, new JsonAnalysisSnapshotRepository(directory).load(snapshot.planId(), snapshot.kind()).orElseThrow());
    }

    @Test void aboveLimitIsRejectedWithPathAndPreviousBytesIntact() throws Exception {
        var original = WeightedSnapshotSaveHotfixTest.completeUniverse();
        var disk = new JsonAnalysisSnapshotRepository(directory); disk.save(original);
        byte[] before = Files.readAllBytes(disk.path(original.planId(), original.kind()));
        var excessive = withNumber(original, new BigDecimal("1234567." + "1".repeat(4097 - 8)));
        var failure = assertThrows(AnalysisSnapshotSaveException.class, () -> disk.save(excessive));
        assertEquals(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT, failure.stage());
        assertTrue(failure.getCause().getMessage().contains("/payload/couple/entries/0/aggregate/expectedPvAfterTaxEstate"));
        assertArrayEquals(before, Files.readAllBytes(disk.path(original.planId(), original.kind())));
        // Decode independently rejects the same token, even when an external writer bypassed encoding checks.
        var raw = new AnalysisSnapshotCodec().raw(original);
        var replacement = new String(raw, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("1000000.0000", excessive.payload().couple().entries().getFirst().aggregate().expectedPvAfterTaxEstate().toString());
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) { gzip.write(replacement.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        assertThrows(IOException.class, () -> new AnalysisSnapshotCodec().decode(out.toByteArray()));
    }
}
