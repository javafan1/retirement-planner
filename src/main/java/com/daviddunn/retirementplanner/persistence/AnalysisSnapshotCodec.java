package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import java.util.logging.Logger;

/** Explicit kind-dispatched v1 codec; unsupported families are refused, never partially serialized. */
public final class AnalysisSnapshotCodec {
    public static final int MAX_RAW_BYTES = 32 * 1024 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    public static final int MAX_TOKENS = 2_000_000;
    public static final int MAX_ARRAYS = 100_000;
    public static final int MAX_DEPTH = 80;
    public static final int MAX_STRING_LENGTH = 256_000;
    // Exact compounded/weighted BigDecimals can exceed the former 1,024-character cap.
    // This remains bounded independently of the unchanged total document limits.
    public static final int MAX_NUMBER_LENGTH = 4096;
    private static final Logger LOG = Logger.getLogger(AnalysisSnapshotCodec.class.getName());
    private final ObjectMapper mapper;
    private final ObjectMapper exactTrees;

    public AnalysisSnapshotCodec() {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(MAX_DEPTH).maxStringLength(MAX_STRING_LENGTH).maxNumberLength(MAX_NUMBER_LENGTH).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        mapper = new ObjectMapper(factory).registerModule(new JavaTimeModule());
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        mapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        mapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        var families = new com.fasterxml.jackson.databind.module.SimpleModule();
        families.addDeserializer(AnalysisPayload.class, new JsonDeserializer<AnalysisPayload>() {
            @Override public AnalysisPayload deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                var kind = (AnalysisKind) context.getAttribute("snapshot-kind");
                if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY)
                    return context.readValue(parser, CoupleSocialSecuritySnapshot.class);
                if (kind == AnalysisKind.COUPLE_EXHAUSTIVE)
                    return context.readValue(parser, ExhaustiveSnapshot.class);
                if (kind != AnalysisKind.COUPLE_WEIGHTED && kind != AnalysisKind.INDIVIDUAL_WEIGHTED
                        && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
                    throw new IOException("Unsupported analysis payload family.");
                // Decode one nested value; the enclosing ObjectReader enforces document trailing tokens.
                return context.readValue(parser, WeightedSnapshots.Payload.class);
            }
        });
        mapper.registerModule(families);
        exactTrees = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        exactTrees.setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    }

    public record Content(AnalysisPayload payload, SnapshotContext context) { }
    public String digest(AnalysisPayload payload, SnapshotContext context) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                mapper.writeValue(output, new Content(payload, context));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalArgumentException("Invalid snapshot content.", e); }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public byte[] raw(AnalysisSnapshot snapshot) throws IOException {
        try { validate(snapshot); }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.DTO_VALIDATION, -1, -1, failure); }
        byte[] raw;
        try {
            if (snapshot.kind() == AnalysisKind.COUPLE_EXHAUSTIVE) {
                com.fasterxml.jackson.databind.node.ObjectNode tree = exactTrees.valueToTree(snapshot);
                tree.set("payload", new ExhaustiveSnapshotInterning(exactTrees).encode(
                        (com.fasterxml.jackson.databind.node.ObjectNode) tree.get("payload")));
                raw = exactTrees.writeValueAsBytes(tree);
            } else raw = mapper.writeValueAsBytes(snapshot);
        }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.SERIALIZATION, -1, -1, failure); }
        if (snapshot.kind() == AnalysisKind.COUPLE_EXHAUSTIVE && (raw.length > MAX_RAW_BYTES
                || Boolean.getBoolean("retirementplanner.snapshot.sizeDiagnostics"))) {
            try { LOG.warning("Exhaustive snapshot size breakdown: " + AnalysisSnapshotSizeDiagnostics.measure(raw)); }
            catch (Exception diagnosticFailure) {
                LOG.warning("Exhaustive snapshot size breakdown unavailable: category=" + diagnosticFailure.getClass().getSimpleName()
                        + " rawBytes=" + raw.length + " maxRawBytes=" + MAX_RAW_BYTES);
            }
        }
        if (raw.length > MAX_RAW_BYTES) throw new AnalysisSnapshotSaveException(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT,
                raw.length, -1, new IOException("Snapshot exceeds the uncompressed size limit."));
        try { checkDocument(raw); }
        catch (IOException failure) {
            throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT, raw.length, -1, failure);
        }
        return raw;
    }
    public record Encoded(byte[] bytes, int rawBytes) { }
    public byte[] encode(AnalysisSnapshot snapshot) throws IOException {
        return encodeMeasured(snapshot).bytes();
    }
    public Encoded encodeMeasured(AnalysisSnapshot snapshot) throws IOException {
        return compress(raw(snapshot));
    }
    /** Package-local document operations for explicit DTO codecs; no additional analysis kind is enabled. */
    Encoded encodeDocument(Object document) throws IOException {
        byte[] raw = mapper.writeValueAsBytes(document);
        if (raw.length > MAX_RAW_BYTES) throw new IOException("Uncompressed snapshot is too large.");
        checkDocument(raw);
        return compress(raw);
    }
    <T> T decodeDocument(byte[] compressed, Class<T> explicitlyAllowedType) throws IOException {
        return mapper.readValue(readDocument(compressed), explicitlyAllowedType);
    }
    private Encoded compress(byte[] raw) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(raw); }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.COMPRESSION, raw.length, output.size(), failure); }
        if (output.size() > MAX_COMPRESSED_BYTES) throw new AnalysisSnapshotSaveException(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT,
                raw.length, output.size(), new IOException("Snapshot exceeds the compressed size limit."));
        return new Encoded(output.toByteArray(), raw.length);
    }
    public AnalysisSnapshot decode(byte[] compressed) throws IOException {
        byte[] raw = readDocument(compressed);
        return decodeSnapshot(raw);
    }
    private byte[] readDocument(byte[] compressed) throws IOException {
        if (compressed.length > MAX_COMPRESSED_BYTES) throw new IOException("Compressed snapshot is too large.");
        byte[] raw;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            raw = gzip.readNBytes(MAX_RAW_BYTES + 1);
        }
        if (raw.length > MAX_RAW_BYTES) throw new IOException("Uncompressed snapshot is too large.");
        checkDocument(raw);
        return raw;
    }
    private AnalysisSnapshot decodeSnapshot(byte[] raw) throws IOException {
        // Schema check is independent of financial compatibility. Never rewrite an unknown schema.
        var tree = exactTrees.readTree(raw);
        if (tree == null || tree.path("schemaVersion").asInt(-1) != AnalysisSnapshot.SCHEMA)
            throw new IOException("Unsupported analysis snapshot schema; the file was preserved.");
        for (String key : List.of("planId", "runId", "originPlanId")) {
            if (key.equals("originPlanId") && tree.path(key).isNull()) continue;
            try { com.daviddunn.retirementplanner.domain.model.RetirementPlan.validatedPlanId(tree.path(key).asText()); }
            catch (IllegalArgumentException malformed) { throw new IOException("Invalid snapshot identity.", malformed); }
        }
        try {
            var kind = AnalysisKind.valueOf(tree.path("kind").asText());
            AnalysisSnapshot snapshot;
            if (kind == AnalysisKind.COUPLE_EXHAUSTIVE) {
                // Exact decimal tree parsing is scoped to this wire representation; legacy families are untouched.
                var exact = (com.fasterxml.jackson.databind.node.ObjectNode) tree;
                if (exact.path("payload").has("exhaustiveRepresentation")) {
                    exact.set("payload", new ExhaustiveSnapshotInterning(exactTrees).decode(
                            (com.fasterxml.jackson.databind.node.ObjectNode) exact.get("payload")));
                }
                // Traverse shared immutable values directly: never materialize an expanded JSON byte document.
                try (var parser = exact.traverse(exactTrees)) {
                    snapshot = mapper.readerFor(AnalysisSnapshot.class).withAttribute("snapshot-kind", kind).readValue(parser);
                }
            } else snapshot = mapper.readerFor(AnalysisSnapshot.class).withAttribute("snapshot-kind", kind).readValue(raw);
            validate(snapshot);
            return snapshot;
        } catch (RuntimeException e) { throw new IOException("Invalid analysis snapshot; the file was preserved.", e); }
    }
    private void checkDocument(byte[] raw) throws IOException {
        // One summary per numeric field family, rather than thousands of messages for a full search.
        var longNumbers = new LinkedHashMap<String, Integer>();
        try (var parser = mapper.createParser(raw)) {
            int arrays = 0;
            int count = 0;
            while (true) {
                JsonToken token;
                try { token = parser.nextToken(); }
                catch (com.fasterxml.jackson.core.exc.StreamConstraintsException failure) {
                    // Jackson's constraint message reports lengths, never the numeric value.
                    String path = numericPath(parser.getParsingContext());
                    throw new IOException("Snapshot token constraint at JSON path " + path + ": " + failure.getOriginalMessage(), failure);
                }
                if (token == null) break;
                if (++count > MAX_TOKENS) throw new IOException("Snapshot collection/token limit exceeded.");
                if (parser.currentToken() == JsonToken.START_ARRAY && ++arrays > MAX_ARRAYS)
                    throw new IOException("Snapshot collection limit exceeded.");
                if (token.isNumeric()) {
                    int length = parser.getTextLength();
                    if (length > 1024) {
                        String path = numericPath(parser.getParsingContext());
                        if (length > MAX_NUMBER_LENGTH)
                            throw new IOException("Snapshot numeric token at JSON path " + path + " has length " + length
                                    + "; maximum permitted is " + MAX_NUMBER_LENGTH + ".");
                        longNumbers.merge(path.replaceAll("/[0-9]+(?=/|$)", "/*"), length, Math::max);
                    }
                }
            }
        }
        for (var entry : longNumbers.entrySet()) {
            LOG.info("Exact snapshot numeric tokens: JSON path=" + entry.getKey() + " maximumLength=" + entry.getValue()
                    + " permittedLength=" + MAX_NUMBER_LENGTH + " rawBytes=" + raw.length);
        }
    }
    private static final Set<String> NUMERIC_PATH_FIELDS = numericPathFields();
    private static Set<String> numericPathFields() {
        var fields = new HashSet<String>();
        fields.addAll(Set.of("exhaustiveRepresentation", "tables", "document", "$ref"));
        var types = new ArrayList<Class<?>>();
        types.addAll(List.of(WeightedSnapshots.class.getDeclaredClasses()));
        types.addAll(List.of(CoupleSocialSecuritySnapshot.class.getDeclaredClasses()));
        types.add(CoupleSocialSecuritySnapshot.class);
        types.add(ExhaustiveSnapshot.class);
        types.addAll(List.of(ExhaustiveSnapshot.class.getDeclaredClasses()));
        types.addAll(List.of(RetainedProjectionSnapshot.class.getDeclaredClasses()));
        types.addAll(List.of(RetainedProjectionSnapshot.class,
                com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement.class,
                com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult.class,
                com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics.class,
                com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts.class));
        types.addAll(List.of(
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedClaimingGridCell.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyRequest.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityClaimingElection.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate.class,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy.class));
        types.addAll(List.of(SnapshotContext.class.getDeclaredClasses()));
        types.addAll(List.of(AnalysisSnapshot.class, SnapshotContext.class,
                com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedStrategyAggregate.class,
                com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedScenarioOutcome.class,
                com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot.class));
        for (var type : types) if (type.isRecord())
            for (var field : type.getRecordComponents()) fields.add(field.getName());
        return Set.copyOf(fields);
    }
    private static String numericPath(JsonStreamContext context) {
        var parts = new LinkedList<String>();
        for (var current = context; current != null; current = current.getParent()) {
            if (current.inArray()) parts.addFirst(Integer.toString(current.getCurrentIndex()));
            else if (current.inObject() && current.getCurrentName() != null) {
                String name = current.getCurrentName();
                parts.addFirst(NUMERIC_PATH_FIELDS.contains(name) ? name : "<unrecognized-field>");
            }
        }
        return "/" + String.join("/", parts);
    }
    private void validate(AnalysisSnapshot s) throws IOException {
        s.payload().validate(s.kind());
        if (s.payload() instanceof ExhaustiveSnapshot exhaustive && !s.completedAt().equals(exhaustive.completedAt()))
            throw new IOException("Exhaustive completion timestamp does not match its envelope.");
        if (!digest(s.payload(), s.context()).equals(s.payloadDigest())) throw new IOException("Snapshot integrity check failed.");
    }
}
