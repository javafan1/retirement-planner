package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.IOException;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionalPlanPersistenceTest {
    @TempDir Path directory;
    final JsonRetirementPlanRepository repository = new JsonRetirementPlanRepository();

    private RetirementPlan plan(String name) {
        var plan = RetirementPlanFactory.createSinglePersonPlan();
        plan.getHousehold().getPrimaryPerson().setFirstName(name);
        return plan;
    }

    private Path existing() throws Exception {
        Path file = directory.resolve("plan.json");
        repository.save(plan("First"), file);
        repository.save(plan("Second"), file);
        return file;
    }

    @Test void newAndRepeatedSavesKeepExactPreviousBytes() throws Exception {
        Path file = directory.resolve("plan.json");
        repository.save(plan("First"), file);
        byte[] first = Files.readAllBytes(file);
        repository.save(plan("Second"), file);
        assertArrayEquals(first, Files.readAllBytes(repository.backupPath(file)));
        byte[] second = Files.readAllBytes(file);
        repository.save(plan("Third"), file);
        assertArrayEquals(second, Files.readAllBytes(repository.backupPath(file)));
        assertEquals("Third", repository.load(file).getHousehold().getPrimaryPerson().getFirstName());
        assertNoTemps();
    }

    @ParameterizedTest
    @EnumSource(value = AtomicFileWriter.Stage.class, names = {"CREATE", "WRITE", "FORCE", "CLOSE", "VERIFY", "BACKUP", "REPLACE"})
    void failedStagePreservesBothOriginalAndBackup(AtomicFileWriter.Stage stage) throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((current, path) -> {
            if (current == stage) {
                if (stage == AtomicFileWriter.Stage.WRITE) Files.writeString(path, "partial");
                throw new IOException("Injected " + stage);
            }
        }), new ObjectMapper());
        assertThrows(IOException.class, () -> faulty.save(plan("Third"), file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
        assertNoTemps();
    }

    @Test void serializationFailurePreservesBothFiles() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        var mapper = new ObjectMapper() {
            @Override public byte[] writeValueAsBytes(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonGenerationException("Injected", (com.fasterxml.jackson.core.JsonGenerator) null);
            }
        };
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter(), mapper);
        assertThrows(IOException.class, () -> faulty.save(plan("Third"), file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
    }

    @Test void validationFailureDoesNotTouchDisk() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        var invalid = plan("Invalid");
        invalid.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.TRADITIONAL_IRA,
                "Invalid spouse", AccountOwnership.SPOUSE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> repository.save(invalid, file));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void structurallyInvalidTemporaryFileFailsVerification() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.VERIFY) Files.writeString(path, "{");
        }), new ObjectMapper());
        assertThrows(IOException.class, () -> faulty.save(plan("Third"), file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
        assertNoTemps();
    }

    @Test void unsupportedAtomicReplacementFailsWithoutFallback() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.REPLACE) throw new AtomicMoveNotSupportedException("", "", "Injected");
        }), new ObjectMapper());
        assertTrue(assertThrows(IOException.class, () -> faulty.save(plan("Third"), file))
                .getMessage().contains("atomically"));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
    }

    @Test void corruptPrimaryNeverReplacesValidBackup() throws Exception {
        Path file = existing();
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        Files.writeString(file, "broken");
        assertThrows(IOException.class, () -> repository.save(plan("Third"), file));
        assertEquals("broken", Files.readString(file));
        assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
        assertEquals("First", repository.loadBackup(file).getHousehold().getPrimaryPerson().getFirstName());
    }

    @Test void unrecognizedBackupIsNeverOverwritten() throws Exception {
        Path file = directory.resolve("plan.json");
        repository.save(plan("First"), file);
        Files.writeString(repository.backupPath(file), "user-owned file");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> repository.save(plan("Second"), file));
        assertEquals("user-owned file", Files.readString(repository.backupPath(file)));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void corruptOwnedBackupCanBeReplacedOnlyWithValidPrimary() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        Files.writeString(repository.backupPath(file), "broken");
        repository.save(plan("Third"), file);
        assertArrayEquals(original, Files.readAllBytes(repository.backupPath(file)));
    }

    @Test void missingPrimaryAndStaleTemporaryFilesDoNotPreventBackupReview() throws Exception {
        Path file = existing();
        Files.delete(file);
        Path unrelated = directory.resolve(".plan.json.user.tmp");
        Files.writeString(unrelated, "keep");
        assertEquals("First", repository.loadBackup(file).getHousehold().getPrimaryPerson().getFirstName());
        repository.save(plan("New"), file);
        assertEquals("New", repository.load(file).getHousehold().getPrimaryPerson().getFirstName());
        assertEquals("keep", Files.readString(unrelated));
    }

    @Test void cleanupFailureDoesNotTurnCommittedSaveIntoFailure() throws Exception {
        Path file = existing();
        var faulty = new JsonRetirementPlanRepository(new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.CLEANUP) throw new IOException("Injected cleanup");
        }), new ObjectMapper());
        assertDoesNotThrow(() -> faulty.save(plan("Third"), file));
        assertEquals("Third", repository.load(file).getHousehold().getPrimaryPerson().getFirstName());
        assertFalse(faulty.takeWarnings().isEmpty());
    }

    @Test void concurrentRepositoryInstancesSerializeSavesWithoutMixedJson() throws Exception {
        Path file = existing();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> saveUnchecked(new JsonRetirementPlanRepository(), plan("A"), file));
            Future<?> second = executor.submit(() -> saveUnchecked(new JsonRetirementPlanRepository(), plan("B"), file));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        var current = repository.load(file).getHousehold().getPrimaryPerson().getFirstName();
        var previous = repository.loadBackup(file).getHousehold().getPrimaryPerson().getFirstName();
        assertTrue(java.util.Set.of("A", "B").contains(current));
        assertNotEquals(current, previous);
        assertNoTemps();
    }

    private static void saveUnchecked(JsonRetirementPlanRepository repository, RetirementPlan plan, Path file) {
        try { repository.save(plan, file); } catch (IOException failure) { throw new RuntimeException(failure); }
    }

    @Test void abruptProcessExitAfterBackupPreparationLeavesLoadablePrimaryAndBackup() throws Exception {
        Path file = existing();
        byte[] before = Files.readAllBytes(file);
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), CrashWriter.class.getName(), file.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Crash fixture must terminate without sleeps");
            assertEquals(17, child.exitValue());
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
        assertArrayEquals(before, Files.readAllBytes(file));
        assertArrayEquals(before, Files.readAllBytes(repository.backupPath(file)));
        assertNotNull(repository.load(file));
        assertNotNull(repository.loadBackup(file));
        // Remnants are intentionally not scanned/deleted by ordinary loading.
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    public static final class CrashWriter {
        public static void main(String[] args) throws Exception {
            var writer = new AtomicFileWriter((stage, path) -> {
                if (stage == AtomicFileWriter.Stage.REPLACE) Runtime.getRuntime().halt(17);
            });
            new JsonRetirementPlanRepository(writer, new ObjectMapper())
                    .save(RetirementPlanFactory.createSinglePersonPlan(), Path.of(args[0]));
        }
    }

    @Test void readonlyPrimaryFailsWithoutTouchingEitherVersion() throws Exception {
        Path file = existing();
        byte[] original = Files.readAllBytes(file);
        byte[] backup = Files.readAllBytes(repository.backupPath(file));
        var dos = Files.getFileAttributeView(file, java.nio.file.attribute.DosFileAttributeView.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(dos != null);
        try {
            dos.setReadOnly(true);
            assertThrows(IOException.class, () -> repository.save(plan("Third"), file));
            assertArrayEquals(original, Files.readAllBytes(file));
            assertArrayEquals(backup, Files.readAllBytes(repository.backupPath(file)));
        } finally {
            dos.setReadOnly(false);
        }
    }

    @Test void exactSerializationPrecisionAndScaleAreUnchangedAndStaleTempsIgnored() throws Exception {
        Path file = directory.resolve("precision.json");
        var plan = plan("Precision");
        var value = new BigDecimal("123456789.123400");
        plan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.TRADITIONAL_IRA,
                "Precision account", AccountOwnership.PRIMARY, value));
        var mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        byte[] expected = mapper.writeValueAsBytes(plan);
        repository.save(plan, file);
        assertArrayEquals(expected, Files.readAllBytes(file));
        Path stale = directory.resolve(".precision.json.rp-crash.tmp");
        Files.writeString(stale, "broken");
        var loaded = repository.load(file);
        assertEquals(value, loaded.getAccountPortfolio().getAccounts().getLast().getCurrentBalance());
        assertEquals("broken", Files.readString(stale));
    }

    private void assertNoTemps() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
