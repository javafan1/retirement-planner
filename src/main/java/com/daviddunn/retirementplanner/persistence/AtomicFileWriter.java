package com.daviddunn.retirementplanner.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Local replacement only. No non-atomic fallback and no directory scanning. */
final class AtomicFileWriter {
    static final Object SAVE_LOCK = new Object();

    enum Stage { CREATE, WRITE, FORCE, CLOSE, VERIFY, BACKUP, REPLACE, CLEANUP }

    @FunctionalInterface
    interface Faults {
        void before(Stage stage, Path path) throws IOException;
    }

    @FunctionalInterface
    interface Verification {
        void verify(Path path) throws IOException;
    }

    private final Faults faults;

    AtomicFileWriter() {
        this((stage, path) -> { });
    }

    AtomicFileWriter(Faults faults) {
        this.faults = java.util.Objects.requireNonNull(faults);
    }

    static Path backup(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    private static Path owner(Path target) {
        return target.resolveSibling(target.getFileName() + ".bak.rp-owner");
    }

    private static byte[] ownership(Path target) {
        return ("RetirementPlanner adjacent backup v1\n" + target.getFileName() + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static java.util.Optional<Path> originalForBackup(Path selected) throws IOException {
        Path file = selected.toAbsolutePath().normalize();
        String name = file.getFileName().toString();
        if (!name.endsWith(".bak")) return java.util.Optional.empty();
        Path original = file.resolveSibling(name.substring(0, name.length() - 4));
        Path record = owner(original);
        if (Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS)
                && java.util.Arrays.equals(Files.readAllBytes(record), ownership(original))) {
            return java.util.Optional.of(original);
        }
        return java.util.Optional.empty();
    }

    static void regularOrMissing(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A regular local file is required; symbolic links are not replaced.");
        }
    }

    List<String> write(Path selected, byte[] bytes, Verification verify, boolean planBackup)
            throws IOException {
        return write(selected, bytes, verify, planBackup, path -> { });
    }

    List<String> write(Path selected, byte[] bytes, Verification verify, boolean planBackup,
            Verification commitGuard) throws IOException {
        synchronized (SAVE_LOCK) {
            Path target = selected.toAbsolutePath().normalize();
            regularOrMissing(target);
            if (Files.exists(target)) {
                if (!Files.isWritable(target)
                        || (Files.getFileAttributeView(target, java.nio.file.attribute.DosFileAttributeView.class) != null
                        && Files.readAttributes(target, DosFileAttributes.class).isReadOnly())) {
                    throw new IOException("Destination is read-only. The existing file was preserved.");
                }
            }
            List<Path> temporary = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Path backup = backup(target);
            Path rollback = null;
            boolean backupChanged = false;
            boolean committed = false;
            try {
                Path prepared = prepare(target, bytes, temporary);
                faults.before(Stage.VERIFY, prepared);
                verify.verify(prepared);
                commitGuard.verify(target);
                if (planBackup && Files.exists(target)) {
                    // Verification of the old destination precedes ANY backup change.
                    verify.verify(target);
                    regularOrMissing(backup);
                    regularOrMissing(owner(target));
                    if (Files.exists(backup) && (!Files.exists(owner(target))
                            || !java.util.Arrays.equals(Files.readAllBytes(owner(target)), ownership(target)))) {
                        throw new IOException("An unrecognized adjacent backup exists. Preserve or rename it before saving.");
                    }
                    Path previous = prepare(target, Files.readAllBytes(target), temporary);
                    verify.verify(previous);
                    if (Files.exists(backup)) {
                        rollback = prepare(target, Files.readAllBytes(backup), temporary);
                    }
                    faults.before(Stage.BACKUP, backup);
                    if (!Files.exists(owner(target))) {
                        Files.write(owner(target), ownership(target), StandardOpenOption.CREATE_NEW);
                    } else if (!java.util.Arrays.equals(Files.readAllBytes(owner(target)), ownership(target))) {
                        throw new IOException("The adjacent backup ownership record is not recognized.");
                    }
                    Files.move(previous, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    backupChanged = true;
                }
                faults.before(Stage.REPLACE, target);
                commitGuard.verify(target);
                Files.move(prepared, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                committed = true;
                return warnings;
            } catch (IOException | RuntimeException failure) {
                if (backupChanged && !committed) {
                    try {
                        if (rollback != null) {
                            Files.move(rollback, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.delete(backup);
                        }
                    } catch (IOException restoreFailure) {
                        // Keep the rollback remnant for manual recovery, never discard its bytes.
                        temporary.remove(rollback);
                        failure.addSuppressed(restoreFailure);
                    }
                }
                if (failure instanceof AtomicMoveNotSupportedException) {
                    throw new IOException("This location cannot safely replace files atomically. Save to another local folder; no non-atomic fallback was attempted.", failure);
                }
                throw failure;
            } finally {
                for (Path path : temporary) {
                    try {
                        faults.before(Stage.CLEANUP, path);
                        Files.deleteIfExists(path);
                    } catch (IOException | SecurityException cleanupFailure) {
                        warnings.add("A save temporary file could not be removed. The saved plan, if committed, remains valid.");
                    }
                }
            }
        }
    }

    private Path prepare(Path target, byte[] bytes, List<Path> temporary) throws IOException {
        faults.before(Stage.CREATE, target);
        Path path = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".rp-", ".tmp");
        temporary.add(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            faults.before(Stage.WRITE, path);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            faults.before(Stage.FORCE, path);
            channel.force(true);
            faults.before(Stage.CLOSE, path);
        }
        return path;
    }
}
