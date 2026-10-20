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

/** Explicit weighted v1 codec; unsupported families are refused, never partially serialized. */
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
    }

    public record Content(WeightedSnapshots.Payload payload, SnapshotContext context) { }
    public String digest(WeightedSnapshots.Payload payload, SnapshotContext context) {
        try { return sha256(mapper.writeValueAsBytes(new Content(payload, context))); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid snapshot content.", e); }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public byte[] raw(AnalysisSnapshot snapshot) throws IOException {
        try { validate(snapshot); }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.DTO_VALIDATION, -1, -1, failure); }
        byte[] raw;
        try { raw = mapper.writeValueAsBytes(snapshot); }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.SERIALIZATION, -1, -1, failure); }
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
        byte[] raw = raw(snapshot);
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(raw); }
        catch (Exception failure) { throw AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.COMPRESSION, raw.length, output.size(), failure); }
        if (output.size() > MAX_COMPRESSED_BYTES) throw new AnalysisSnapshotSaveException(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT,
                raw.length, output.size(), new IOException("Snapshot exceeds the compressed size limit."));
        return new Encoded(output.toByteArray(), raw.length);
    }
    public AnalysisSnapshot decode(byte[] compressed) throws IOException {
        if (compressed.length > MAX_COMPRESSED_BYTES) throw new IOException("Compressed snapshot is too large.");
        byte[] raw;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            raw = gzip.readNBytes(MAX_RAW_BYTES + 1);
        }
        if (raw.length > MAX_RAW_BYTES) throw new IOException("Uncompressed snapshot is too large.");
        checkDocument(raw);
        // Schema check is independent of financial compatibility. Never rewrite an unknown schema.
        var tree = mapper.readTree(raw);
        if (tree == null || tree.path("schemaVersion").asInt(-1) != AnalysisSnapshot.SCHEMA)
            throw new IOException("Unsupported analysis snapshot schema; the file was preserved.");
        for (String key : List.of("planId", "runId", "originPlanId")) {
            if (key.equals("originPlanId") && tree.path(key).isNull()) continue;
            try { com.daviddunn.retirementplanner.domain.model.RetirementPlan.validatedPlanId(tree.path(key).asText()); }
            catch (IllegalArgumentException malformed) { throw new IOException("Invalid snapshot identity.", malformed); }
        }
        try {
            var snapshot = mapper.readValue(raw, AnalysisSnapshot.class);
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
        var types = new ArrayList<Class<?>>();
        types.addAll(List.of(WeightedSnapshots.class.getDeclaredClasses()));
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
        if (!digest(s.payload(), s.context()).equals(s.payloadDigest())) throw new IOException("Snapshot integrity check failed.");
    }
}
