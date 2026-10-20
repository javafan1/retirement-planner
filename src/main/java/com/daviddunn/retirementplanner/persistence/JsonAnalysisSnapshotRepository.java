package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Cache-specific validation with the same transactional guarantees as primary plan saves. */
public final class JsonAnalysisSnapshotRepository implements AnalysisSnapshotRepository {
    private final Path root;
    private final AtomicFileWriter writer;
    private final AnalysisSnapshotCodec codec = new AnalysisSnapshotCodec();
    public JsonAnalysisSnapshotRepository(Path root) { this(root, new AtomicFileWriter()); }
    JsonAnalysisSnapshotRepository(Path root, AtomicFileWriter writer) {
        this.root = root.toAbsolutePath().normalize(); this.writer = Objects.requireNonNull(writer);
    }
    public Path path(UUID id, AnalysisKind kind) throws IOException {
        Objects.requireNonNull(id); Objects.requireNonNull(kind);
        Path target = root.resolve(id.toString()).resolve(kind.shape().name()).resolve(kind.name() + ".json.gz");
        for (Path p = target; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic links are not supported in snapshot storage.");
        }
        return target;
    }
    private AnalysisSnapshot read(Path path, UUID id, AnalysisKind kind) throws IOException {
        AtomicFileWriter.regularOrMissing(path);
        if (Files.size(path) > AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES) throw new IOException("Snapshot is too large.");
        byte[] compressed;
        try (var input = Files.newInputStream(path)) {
            compressed = input.readNBytes(AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES + 1);
        }
        var s = codec.decode(compressed);
        if (!s.planId().equals(id) || s.kind() != kind) throw new IOException("Snapshot binding does not match its storage slot.");
        return s;
    }
    @Override public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) throws IOException {
        var p = path(id, kind);
        return Files.notExists(p, LinkOption.NOFOLLOW_LINKS) ? Optional.empty() : Optional.of(read(p, id, kind));
    }
    @Override public void save(AnalysisSnapshot s) throws IOException {
        write(s, false);
    }
    @Override public boolean saveIfAbsent(AnalysisSnapshot s) throws IOException { return write(s, true); }
    private boolean write(AnalysisSnapshot s, boolean onlyIfAbsent) throws IOException {
        var encoded = codec.encodeMeasured(s);
        byte[] bytes = encoded.bytes();
        var stage = AnalysisSnapshotSaveException.Stage.FILE_LOCK;
        try {
            var p = path(s.planId(), s.kind());
            Files.createDirectories(p.getParent());
            path(s.planId(), s.kind());
            Path lockPath = p.resolveSibling(p.getFileName() + ".lock");
            AtomicFileWriter.regularOrMissing(lockPath);
            try (var channel = java.nio.channels.FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Another process is saving these analysis results; retry later.");
                stage = AnalysisSnapshotSaveException.Stage.EXISTING_SNAPSHOT;
                if (!Files.notExists(p, LinkOption.NOFOLLOW_LINKS)) {
                    var previous = read(p, s.planId(), s.kind());
                    if (onlyIfAbsent) return false;
                    var readability = new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisCompatibilityPolicy().assess(previous,
                            new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService.Assessment(
                                    previous.inputFingerprint(), previous.contextFingerprint(), null),
                            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisVersions.algorithms(s.kind()), previous.references());
                    if (readability.state() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE)
                        throw new IOException("Historical results have unsupported financial meaning and were preserved. Explicit recovery is required before replacement.");
                }
                stage = AnalysisSnapshotSaveException.Stage.ATOMIC_WRITE;
                writer.write(p, bytes, candidate -> {
                    try { read(candidate, s.planId(), s.kind()); }
                    catch (Exception failure) {
                        throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.VERIFICATION,
                                encoded.rawBytes(), bytes.length, failure);
                    }
                }, true);
                return true;
            } catch (java.nio.channels.OverlappingFileLockException busy) {
                throw new IOException("Another result save is active; retry later.", busy);
            }
        } catch (Exception failure) {
            throw AnalysisSnapshotSaveException.at(stage, encoded.rawBytes(), bytes.length, failure);
        }
    }
    @Override public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) throws IOException {
        var primary = path(id, kind);
        var backup = AtomicFileWriter.backup(primary);
        if (AtomicFileWriter.originalForBackup(backup).isEmpty()) return Optional.empty();
        return Optional.of(read(backup, id, kind));
    }
    @Override public void claimPlan(UUID id, Path planFile) throws IOException {
        var directory = path(id, AnalysisKind.COUPLE_WEIGHTED).getParent().getParent();
        Files.createDirectories(directory);
        var binding = directory.resolve("plan-binding.txt");
        var lockFile = directory.resolve("plan-binding.lock");
        var normalized = planFile.toAbsolutePath().normalize();
        synchronized (AtomicFileWriter.SAVE_LOCK) {
            AtomicFileWriter.regularOrMissing(binding);
            AtomicFileWriter.regularOrMissing(lockFile);
            try (var channel = java.nio.channels.FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Plan identity is being bound by another process; retry later.");
                if (!Files.notExists(binding)) {
                    if (Files.size(binding) > 16_384) throw new IOException("Invalid plan binding.");
                    Path previous = Path.of(Files.readString(binding));
                    if (!previous.isAbsolute()) throw new IOException("Invalid plan binding path.");
                    if (!previous.equals(normalized) && Files.exists(previous))
                        throw new IOException("A second existing plan file has this UUID. Use application Save As for an independent plan.");
                }
                byte[] value = normalized.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                writer.write(binding, value, p -> {
                    if (!Files.readString(p).equals(normalized.toString())) throw new IOException("Invalid binding write.");
                }, false);
            } catch (java.nio.channels.OverlappingFileLockException busy) {
                throw new IOException("Plan identity binding is busy; retry later.", busy);
            }
        }
    }
}
