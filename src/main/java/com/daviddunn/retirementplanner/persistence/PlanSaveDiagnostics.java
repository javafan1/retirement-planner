package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.branding.ApplicationVersion;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Privacy-safe, bounded diagnostics for one plan save. Never stores paths or exception messages. */
public final class PlanSaveDiagnostics {
    private static final Logger LOG = Logger.getLogger(PlanSaveDiagnostics.class.getName());
    private static final int MAX_CAUSES = 8;
    private static final int MAX_FRAMES = 12;
    private static final long MAX_LOG_BYTES = 256 * 1024;
    private static final Object FILE_LOCK = new Object();

    public enum Operation { SAVE, SAVE_AS }

    public enum Stage {
        OWNER_CAPTURE,
        PLAN_COPY_RECONSTRUCTION,
        DESTINATION_CHECK,
        PLAN_VALIDATION,
        PROJECTION_COMPONENT_PREPARATION,
        JSON_SERIALIZATION,
        PROJECTION_MANIFEST_SERIALIZATION,
        JSON_ROUND_TRIP_VERIFICATION,
        TEMP_FILE_CREATE,
        TEMP_FILE_WRITE,
        TEMP_FILE_FORCE,
        TEMP_FILE_CLOSE,
        CANDIDATE_VERIFICATION,
        BACKUP_VERIFICATION,
        BACKUP_PUBLICATION,
        ATOMIC_TARGET_REPLACEMENT,
        PLAN_FILE_COMMITTED,
        RECEIPT_FINISH,
        PREFERENCE_WRITE,
        COMPLETE,
        UNKNOWN
    }

    private final Operation operation;
    private volatile Stage stage = Stage.OWNER_CAPTURE;
    private volatile boolean fileWritingStarted;
    private volatile boolean atomicReplacementAttempted;

    public PlanSaveDiagnostics(Operation operation) {
        this.operation = Objects.requireNonNull(operation);
    }

    public Operation operation() { return operation; }
    public Stage stage() { return stage; }
    public boolean fileWritingStarted() { return fileWritingStarted; }
    public boolean atomicReplacementAttempted() { return atomicReplacementAttempted; }

    public void stage(Stage value) { stage = Objects.requireNonNull(value); }
    public void fileWritingStartedNow() { fileWritingStarted = true; }
    public void atomicReplacementAttemptedNow() { atomicReplacementAttempted = true; }

    /** Emits only allowlisted metadata and application-owned class/method frame names. */
    public static void logFailure(PlanSaveDiagnostics diagnostics, Throwable failure) {
        Objects.requireNonNull(failure);
        var context = diagnostics == null ? new PlanSaveDiagnostics(Operation.SAVE) : diagnostics;
        String record = "timestamp=" + Instant.now() + ", " + format(context, failure);
        LOG.log(Level.SEVERE, record);
        try {
            Path directory = Path.of(System.getProperty("user.home"), ".retirement-planner", "diagnostics");
            writeRecord(directory, record);
        } catch (RuntimeException | java.io.IOException unavailable) {
            // Diagnostics must never mask the original plan-save failure.
            LOG.log(Level.WARNING, "Plan-save diagnostic file could not be updated.");
        }
    }

    static void writeRecord(Path directory, String record) throws java.io.IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(record);
        synchronized (FILE_LOCK) {
            Path normalizedDirectory = directory.toAbsolutePath().normalize();
            Path applicationDirectory = normalizedDirectory.getParent();
            if (applicationDirectory != null && Files.isSymbolicLink(applicationDirectory))
                throw new java.io.IOException("Application data directory is a symbolic link.");
            Files.createDirectories(normalizedDirectory);
            if (Files.isSymbolicLink(normalizedDirectory)) throw new java.io.IOException("Diagnostic directory is a symbolic link.");
            Path file = normalizedDirectory.resolve("plan-save-diagnostics.log");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file))) {
                throw new java.io.IOException("Diagnostic target is not a regular application log.");
            }
            try {
                Files.setPosixFilePermissions(normalizedDirectory, Set.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            } catch (UnsupportedOperationException ignored) { }
            Set<OpenOption> options = Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
            try (FileChannel channel = FileChannel.open(file, options); var ignored = channel.lock()) {
                byte[] bytes = (record + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (bytes.length >= MAX_LOG_BYTES) {
                    bytes = java.util.Arrays.copyOfRange(bytes,
                            bytes.length - (int) MAX_LOG_BYTES + 1, bytes.length);
                }
                if (channel.size() + bytes.length > MAX_LOG_BYTES) channel.truncate(0);
                channel.position(channel.size());
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) { }
        }
    }

    static String format(PlanSaveDiagnostics diagnostics, Throwable failure) {
        var causes = new ArrayList<String>();
        var frames = new ArrayList<String>();
        var seen = new IdentityHashMap<Throwable, Boolean>();
        Throwable current = failure;
        while (current != null && causes.size() < MAX_CAUSES && seen.put(current, Boolean.TRUE) == null) {
            causes.add(safeClassName(current.getClass().getName()));
            if (frames.size() < MAX_FRAMES) {
                for (var frame : current.getStackTrace()) {
                    if (!frame.getClassName().startsWith("com.daviddunn.retirementplanner.")) continue;
                    frames.add(safeToken(frame.getClassName()) + "#" + safeToken(frame.getMethodName()));
                    if (frames.size() == MAX_FRAMES) break;
                }
            }
            current = current.getCause();
        }
        String version;
        try { version = ApplicationVersion.current().productVersion(); }
        catch (RuntimeException unavailable) { version = "unavailable"; }
        return "Plan save failure diagnostics: operation=" + diagnostics.operation()
                + ", version=" + safeToken(version)
                + ", stage=" + diagnostics.stage()
                + ", fileWritingStarted=" + diagnostics.fileWritingStarted()
                + ", atomicReplacementAttempted=" + diagnostics.atomicReplacementAttempted()
                + ", exceptionClasses=" + causes
                + ", applicationFrames=" + frames;
    }

    private static String safeClassName(String value) {
        return safeToken(value);
    }

    private static String safeToken(String value) {
        if (value == null) return "unknown";
        String safe = value.replaceAll("[^A-Za-z0-9_.$-]", "_");
        return safe.length() > 180 ? safe.substring(0, 180) : safe;
    }
}
