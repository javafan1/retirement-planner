package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.socialsecurity.SnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSnapshotRepositoryTest {
    @TempDir Path dir;
    @Test void oversizedNumericTokenDuringWrittenVerificationPreservesPrimaryAndBackup() throws Exception {
        var id = UUID.randomUUID(); var good = new JsonAnalysisSnapshotRepository(dir);
        good.save(SnapshotFixtures.snapshot(id)); good.save(SnapshotFixtures.snapshot(id));
        var path = good.path(id, AnalysisKind.COUPLE_WEIGHTED);
        byte[] primary = Files.readAllBytes(path), backup = Files.readAllBytes(AtomicFileWriter.backup(path));
        var codec = new AnalysisSnapshotCodec();
        var snapshot = SnapshotFixtures.snapshot(id);
        String raw = new String(codec.raw(snapshot), java.nio.charset.StandardCharsets.UTF_8)
                .replaceFirst("100.00400", "1".repeat(AnalysisSnapshotCodec.MAX_NUMBER_LENGTH + 1));
        var output = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(output)) { gzip.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        var broken = new JsonAnalysisSnapshotRepository(dir, new AtomicFileWriter((stage, temporary) -> {
            if (stage == AtomicFileWriter.Stage.VERIFY) Files.write(temporary, output.toByteArray());
        }));
        var failure = assertThrows(AnalysisSnapshotSaveException.class, () -> broken.save(snapshot));
        assertEquals(AnalysisSnapshotSaveException.Stage.VERIFICATION, failure.stage());
        assertTrue(failure.getCause().getMessage().contains("/payload/couple/entries/0/aggregate/expectedPvAfterTaxEstate"));
        assertArrayEquals(primary, Files.readAllBytes(path));
        assertArrayEquals(backup, Files.readAllBytes(AtomicFileWriter.backup(path)));
    }
    @ParameterizedTest @EnumSource(value = AtomicFileWriter.Stage.class, names = {"CREATE", "WRITE", "FORCE", "CLOSE", "VERIFY", "BACKUP", "REPLACE"})
    void everyPrecommitFailurePreservesPrimaryAndBackup(AtomicFileWriter.Stage stage) throws Exception {
        var id = UUID.randomUUID(); var good = new JsonAnalysisSnapshotRepository(dir);
        good.save(SnapshotFixtures.snapshot(id)); good.save(SnapshotFixtures.snapshot(id));
        var path = good.path(id, AnalysisKind.COUPLE_WEIGHTED);
        byte[] primary = Files.readAllBytes(path), backup = Files.readAllBytes(AtomicFileWriter.backup(path));
        var broken = new JsonAnalysisSnapshotRepository(dir, new AtomicFileWriter((s, p) -> {
            if (s == stage) throw new IOException("injected " + stage);
        }));
        var diagnostic = assertThrows(AnalysisSnapshotSaveException.class, () -> broken.save(SnapshotFixtures.snapshot(id)));
        assertEquals(AnalysisSnapshotSaveException.Stage.ATOMIC_WRITE, diagnostic.stage());
        assertTrue(diagnostic.rawBytes() > 0); assertTrue(diagnostic.compressedBytes() > 0);
        assertEquals("injected " + stage, diagnostic.getCause().getMessage());
        assertArrayEquals(primary, Files.readAllBytes(path)); assertArrayEquals(backup, Files.readAllBytes(AtomicFileWriter.backup(path)));
    }
    @Test void corruptionRetainsRecognizedBackupAndBlocksDestructiveOverwrite() throws Exception {
        var id = UUID.randomUUID(); var repo = new JsonAnalysisSnapshotRepository(dir);
        var previous = SnapshotFixtures.snapshot(id); repo.save(previous); repo.save(SnapshotFixtures.snapshot(id));
        var p = repo.path(id, AnalysisKind.COUPLE_WEIGHTED); Files.writeString(p, "bad gzip");
        assertThrows(IOException.class, () -> repo.load(id, AnalysisKind.COUPLE_WEIGHTED));
        assertEquals(previous, repo.loadBackup(id, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
        var diagnostic = assertThrows(AnalysisSnapshotSaveException.class, () -> repo.save(SnapshotFixtures.snapshot(id)));
        assertEquals(AnalysisSnapshotSaveException.Stage.EXISTING_SNAPSHOT, diagnostic.stage());
        assertEquals("bad gzip", Files.readString(p));
        assertEquals(previous, repo.loadBackup(id, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
    }
    @Test void atomicUnsupportedPermissionFailureAndCleanupAreConservative() throws Exception {
        var id = UUID.randomUUID(); var repo = new JsonAnalysisSnapshotRepository(dir); repo.save(SnapshotFixtures.snapshot(id));
        var original = repo.load(id, AnalysisKind.COUPLE_WEIGHTED);
        for (boolean atomic : List.of(true, false)) {
            var faulty = new JsonAnalysisSnapshotRepository(dir, new AtomicFileWriter((stage, path) -> {
                if (stage == AtomicFileWriter.Stage.REPLACE) {
                    if (atomic) throw new AtomicMoveNotSupportedException("a", "b", "injected");
                    throw new AccessDeniedException("injected");
                }
            }));
            var diagnostic = assertThrows(AnalysisSnapshotSaveException.class, () -> faulty.save(SnapshotFixtures.snapshot(id)));
            assertEquals(AnalysisSnapshotSaveException.Stage.ATOMIC_WRITE, diagnostic.stage());
            if (atomic) assertInstanceOf(AtomicMoveNotSupportedException.class, diagnostic.getCause().getCause());
            else assertInstanceOf(AccessDeniedException.class, diagnostic.getCause());
            assertEquals(original, repo.load(id, AnalysisKind.COUPLE_WEIGHTED));
        }
        var cleanup = new JsonAnalysisSnapshotRepository(dir, new AtomicFileWriter((stage, p) -> {
            if (stage == AtomicFileWriter.Stage.CLEANUP) throw new IOException("cleanup");
        }));
        var next = SnapshotFixtures.snapshot(id); cleanup.save(next);
        assertEquals(next, repo.load(id, AnalysisKind.COUPLE_WEIGHTED).orElseThrow());
    }
    @Test void slotBindingAndDuplicateExternalCopyAreGuarded() throws Exception {
        var id = UUID.randomUUID(); var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        Path first = dir.resolve("first.json"), second = dir.resolve("copy.json");
        Files.writeString(first, "plan"); Files.writeString(second, "copy");
        repo.claimPlan(id, first); assertThrows(IOException.class, () -> repo.claimPlan(id, second));
        Files.delete(first); repo.claimPlan(id, second);
        var s = SnapshotFixtures.snapshot(id); repo.save(s);
        var other = UUID.randomUUID(); var wrong = repo.path(other, s.kind()); Files.createDirectories(wrong.getParent());
        Files.copy(repo.path(id, s.kind()), wrong);
        assertThrows(IOException.class, () -> repo.load(other, s.kind()));
        assertTrue(repo.load(id, AnalysisKind.INDIVIDUAL_WEIGHTED).isEmpty());
    }
    @Test void unknownSchemaCannotBeOverwrittenAndCrashRemnantDoesNotAffectLoad() throws Exception {
        var id = UUID.randomUUID(); var repo = new JsonAnalysisSnapshotRepository(dir);
        var saved = SnapshotFixtures.snapshot(id); repo.save(saved);
        var p = repo.path(id, saved.kind());
        var remnant = p.resolveSibling(".interrupted.rp-unknown.tmp"); Files.writeString(remnant, "partial");
        assertEquals(saved, repo.load(id, saved.kind()).orElseThrow());
        var codec = new AnalysisSnapshotCodec();
        String raw = new String(codec.raw(saved), java.nio.charset.StandardCharsets.UTF_8).replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        var output = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(output)) { gzip.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        Files.write(p, output.toByteArray()); byte[] unknown = Files.readAllBytes(p);
        assertThrows(IOException.class, () -> repo.save(SnapshotFixtures.snapshot(id)));
        assertArrayEquals(unknown, Files.readAllBytes(p)); assertEquals("partial", Files.readString(remnant));
    }
}
