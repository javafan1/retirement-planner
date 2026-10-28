package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Immutable bounded objects, published before the ordinary plan's atomic role-manifest commit. */
public final class ProjectionSnapshotComponents {
    // Receipts contain identities only, never retained simulation graphs. A receipt is useful only
    // after rereading and hashing the entire bounded artifact; timestamps are not trusted.
    private static final Map<AnalyzerAssociation, Boolean> verifiedAnalyzers =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<AnalyzerAssociation, Boolean> entry) {
                    return size() > 64;
                }
            });
    private final AnalysisSnapshotCodec documents = new AnalysisSnapshotCodec();
    private final AtomicFileWriter writer = new AtomicFileWriter();
    /** Pure bounded encoding validation for an accepted component not yet written by ordinary Save. */
    public void validatePendingProjection(CompleteProjectionSnapshot snapshot) throws IOException {
        documents.encodeProjectionDocument(java.util.Objects.requireNonNull(snapshot));
    }
    private String binding(Path file) { return CompleteProjectionSnapshot.digest(file.toAbsolutePath().normalize().toString()); }
    private void verifyBinding(Path file, Path directory) throws IOException {
        var marker = directory.resolve(".binding");
        AtomicFileWriter.regularOrMissing(marker);
        if (Files.size(marker) != 64 || !Files.readString(marker).equals(binding(file)))
            throw new IOException("Projection storage belongs to another plan path.");
    }
    public record Prepared(ProjectionRoleManifest manifest, Map<UUID, CompleteProjectionSnapshot> components,
            Map<UUID, com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot> analyzers) {
        public Prepared(ProjectionRoleManifest manifest, Map<UUID, CompleteProjectionSnapshot> components) { this(manifest, components, Map.of()); }
        public Prepared { components = Map.copyOf(components); analyzers = Map.copyOf(analyzers); }
    }
    private Path path(Path file, UUID owner, UUID id) throws IOException {
        Path target = file.toAbsolutePath().normalize().getParent().resolve(".retirement-planner-projections")
                .resolve(owner.toString()).resolve(id + ".json.gz");
        for (Path p = target; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic snapshot paths are not supported.");
        return target;
    }
    public CompleteProjectionSnapshot read(Path file, UUID owner, ProjectionRoleManifest.Reference ref) throws IOException {
        Path source = path(file, owner, ref.id());
        verifyBinding(file, source.getParent());
        AtomicFileWriter.regularOrMissing(source);
        if (Files.size(source) > AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES) throw new IOException("Projection component is oversized.");
        byte[] bytes;
        try (var input = Files.newInputStream(source)) { bytes = input.readNBytes(AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES + 1); }
        if (!AnalysisSnapshotCodec.sha256(bytes).equals(ref.digest())) throw new IOException("Projection component checksum mismatch.");
        var result = documents.decodeDocument(bytes, CompleteProjectionSnapshot.class);
        if (!result.id().equals(ref.id()) || !result.owner().equals(owner)) throw new IOException("Projection component ownership mismatch.");
        result.restoreInputs(); result.summary().restore(result.annual().restoreProjection());
        return result;
    }
    public Map<UUID, CompleteProjectionSnapshot> load(Path file, ProjectionRoleManifest manifest) throws IOException {
        var result = new LinkedHashMap<UUID, CompleteProjectionSnapshot>();
        for (var ref : references(manifest)) result.put(ref.id(), read(file, manifest.owner(), ref));
        return Map.copyOf(result);
    }
    private List<ProjectionRoleManifest.Reference> references(ProjectionRoleManifest manifest) {
        var refs = new ArrayList<ProjectionRoleManifest.Reference>();
        if (manifest.current() != null) refs.add(manifest.current());
        if (manifest.baseline() != null && !refs.contains(manifest.baseline())) refs.add(manifest.baseline());
        return refs;
    }
    public Prepared prepare(RetirementPlan plan, Path sourceFile, Path targetFile, UUID identity) throws IOException {
        var state = plan.getProjectionState();
        if (state == null) return new Prepared(null, Map.of());
        var completed = new LinkedHashMap<UUID, CompleteProjectionSnapshot>();
        var refs = new HashMap<UUID, ProjectionRoleManifest.Reference>();
        var inputs = new HashMap<UUID, String>();
        var shapes = new HashMap<UUID, Boolean>();
        var baseline = state.baseline();
        if (baseline != null && (plan.getBaseline() == null || !baseline.id().equals(plan.getBaseline().getProjectionSnapshotId()))) baseline = null;
        var allReferences = new LinkedHashMap<UUID, ProjectionRoleManifest.Reference>();
        references(new ProjectionRoleManifest(1, state.owner(), state.current(), baseline)).forEach(r -> allReferences.put(r.id(), r));
        state.pinnedProjections().forEach(r -> allReferences.putIfAbsent(r.id(), r));
        for (var reference : allReferences.values()) {
            var snapshot = plan.getProjectionComponents().get(reference.id());
            if (snapshot == null) snapshot = plan.getPendingPinnedProjections().get(reference.id());
            if (snapshot == null) snapshot = read(sourceFile, state.owner(), reference);
            if (!snapshot.id().equals(reference.id()) || !snapshot.owner().equals(state.owner()))
                throw new IOException("In-memory projection binding mismatch.");
            if (baseline != null && baseline.id().equals(reference.id())
                    && !snapshot.inputsDocument().equals(CompleteProjectionSnapshot.freeze(plan.getBaseline().getSnapshot())))
                throw new IOException("Baseline inputs and original results do not belong together.");
            var rebound = snapshot.ownedBy(identity);
            byte[] bytes = documents.encodeProjectionDocument(rebound).bytes();
            var ref = new ProjectionRoleManifest.Reference(rebound.id(), AnalysisSnapshotCodec.sha256(bytes));
            Path destination = path(targetFile, identity, rebound.id());
            Files.createDirectories(destination.getParent()); path(targetFile, identity, rebound.id());
            var marker = destination.getParent().resolve(".binding");
            AtomicFileWriter.regularOrMissing(marker);
            if (!Files.exists(marker)) writer.write(marker, binding(targetFile).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    candidate -> { if (!Files.readString(candidate).equals(binding(targetFile))) throw new IOException("Invalid projection binding."); }, false);
            verifyBinding(targetFile, destination.getParent());
            if (Files.exists(destination)) read(targetFile, identity, ref);
            else {
                try (var entries = Files.list(destination.getParent())) {
                    var all = entries.limit(1001).toList();
                    if (all.size() >= 1000) throw new IOException("Projection component count budget exceeded.");
                    long total = 0;
                    for (var entry : all) { AtomicFileWriter.regularOrMissing(entry); total = Math.addExact(total, Files.size(entry)); }
                    if (total + bytes.length > 128L * 1024 * 1024) throw new IOException("Projection storage budget exceeded.");
                }
                writer.write(destination, bytes, candidate -> {
                    var decoded = documents.decodeDocument(Files.readAllBytes(candidate), CompleteProjectionSnapshot.class);
                    if (!decoded.equals(rebound)) throw new IOException("Projection verification failed.");
                    decoded.annual().restoreProjection(); decoded.restoreInputs();
                }, false);
            }
            refs.put(ref.id(), ref); inputs.put(ref.id(), rebound.inputDigest());
            shapes.put(ref.id(), rebound.restoreInputs().getHousehold().hasSpouse());
            if (state.current() != null && ref.id().equals(state.current().id()) || baseline != null && ref.id().equals(baseline.id()))
                completed.put(rebound.id(), rebound);
        }
        var kept = state.withRoles(state.owner(), state.current(), baseline).associations();
        var associations = new ArrayList<AnalyzerAssociation>();
        for (var reference : kept) {
            if (!reference.projectionInputDigest().equals(inputs.get(reference.projectionId()))) throw new IOException("Analyzer projection association mismatch.");
            if ((reference.kind().shape() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.Shape.COUPLE)
                    != shapes.get(reference.projectionId())) throw new IOException("Analyzer projection household mismatch.");
            if (identity.equals(reference.owner())
                    && plan.getPendingAnalyzerAssociations().get(reference.runId()) == null
                    && plan.getAssociatedAnalyzerResults().get(reference.runId()) == null
                    && sourceFile != null && sourceFile.toAbsolutePath().normalize().equals(targetFile.toAbsolutePath().normalize())) {
                verifyUnchangedAnalyzer(targetFile, reference);
                associations.add(reference);
                continue;
            }
            var original = resolveAnalyzer(reference, plan.getPendingAnalyzerAssociations().get(reference.runId()),
                    plan.getAssociatedAnalyzerResults().get(reference.runId()), sourceFile);
            var snapshot = identity.equals(original.planId()) ? original : original.clonedTo(identity);
            byte[] bytes = documents.encode(snapshot);
            var rebound = new AnalyzerAssociation(1, identity, reference.projectionId(), reference.projectionInputDigest(),
                    snapshot.kind(), snapshot.runId(), snapshot.inputFingerprint(), snapshot.contextFingerprint(),
                    snapshot.payloadDigest(), AnalysisSnapshotCodec.sha256(bytes), reference.acceptedAt());
            var destination = analyzerPath(targetFile, identity, snapshot.runId());
            if (Files.exists(destination)) readAnalyzer(targetFile, rebound);
            else {
                checkBudget(destination.getParent(), bytes.length);
                writer.write(destination, bytes, candidate -> {
                    var decoded = documents.decode(Files.readAllBytes(candidate)); rebound.validate(decoded);
                    if (!decoded.payloadDigest().equals(snapshot.payloadDigest())) throw new IOException("Analyzer verification failed.");
                }, false);
            }
            verifiedAnalyzers.put(rebound, Boolean.TRUE);
            // Do not retain every decoded analyzer graph: stage and release one artifact at a time.
            associations.add(rebound);
        }
        return new Prepared(new ProjectionRoleManifest(associations.isEmpty() ? 1 : 2, identity,
                state.current() == null ? null : refs.get(state.current().id()),
                baseline == null ? null : refs.get(baseline.id()), associations,
                state.pinnedProjections().stream().map(r -> refs.get(r.id())).toList()), completed, Map.of());
    }
    private Path analyzerPath(Path file, UUID owner, UUID id) throws IOException {
        var base = path(file, owner, id);
        return base.resolveSibling("analysis-" + id + ".json.gz");
    }
    private void checkBudget(Path directory, long added) throws IOException {
        try (var entries = Files.list(directory)) {
            var all = entries.limit(1001).toList();
            if (all.size() >= 1000) throw new IOException("Projection component count budget exceeded.");
            long total = 0;
            for (var entry : all) { AtomicFileWriter.regularOrMissing(entry); total = Math.addExact(total, Files.size(entry)); }
            if (total + added > 128L*1024*1024) throw new IOException("Projection storage budget exceeded.");
        }
    }
    public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot readAnalyzer(Path file, AnalyzerAssociation reference) throws IOException {
        byte[] bytes = analyzerBytes(file, reference);
        var snapshot = documents.decode(bytes); reference.validate(snapshot);
        verifiedAnalyzers.put(reference, Boolean.TRUE);
        return snapshot;
    }
    private void verifyUnchangedAnalyzer(Path file, AnalyzerAssociation reference) throws IOException {
        byte[] bytes = analyzerBytes(file, reference);
        if (!verifiedAnalyzers.containsKey(reference)) {
            reference.validate(documents.decode(bytes));
            verifiedAnalyzers.put(reference, Boolean.TRUE);
        }
    }
    /** Validation-only callers need no decoded result graph. Byte/digest verification is never skipped. */
    public void verifyAnalyzer(Path file, AnalyzerAssociation reference,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion pending,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot memory) throws IOException {
        if (pending == null && memory == null) verifyUnchangedAnalyzer(file, reference);
        else resolveAnalyzer(reference, pending, memory, file);
    }
    private byte[] analyzerBytes(Path file, AnalyzerAssociation reference) throws IOException {
        Path source = analyzerPath(file, reference.owner(), reference.runId());
        verifyBinding(file, source.getParent()); AtomicFileWriter.regularOrMissing(source);
        byte[] bytes;
        try (var input = Files.newInputStream(source)) { bytes = input.readNBytes(AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES+1); }
        if (bytes.length > AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES || !AnalysisSnapshotCodec.sha256(bytes).equals(reference.componentDigest()))
            throw new IOException("Analyzer component checksum or size mismatch.");
        return bytes;
    }
    public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot resolveAnalyzer(AnalyzerAssociation reference,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion pending,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot memory, Path file) throws IOException {
        var snapshot = pending != null ? pending.presentationSnapshot(documents) : memory != null ? memory : readAnalyzer(file, reference);
        if (pending != null && reference.payloadDigest().equals("0".repeat(64))) {
            var materialized = new AnalyzerAssociation(1, reference.owner(), reference.projectionId(), reference.projectionInputDigest(),
                    reference.kind(), reference.runId(), reference.inputFingerprint(), reference.contextFingerprint(),
                    snapshot.payloadDigest(), reference.componentDigest(), reference.acceptedAt());
            materialized.validate(snapshot);
        } else reference.validate(snapshot);
        return snapshot;
    }
}
