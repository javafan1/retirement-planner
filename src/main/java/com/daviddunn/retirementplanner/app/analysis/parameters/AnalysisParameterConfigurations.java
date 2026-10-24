package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded immutable metadata, with explicit kind dispatch and opaque future-version retention. */
@JsonDeserialize(using = AnalysisParameterConfigurations.Reader.class)
public final class AnalysisParameterConfigurations {
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_ENTRIES = 32;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12)
                    .maxStringLength(2048).maxNameLength(128).maxNumberLength(512).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .registerModule(new JavaTimeModule()).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    static { JSON.configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false); }
    private final String json;

    private AnalysisParameterConfigurations(String json) { this.json = json; }
    public static AnalysisParameterConfigurations empty() { return parse("{\"schemaVersion\":1,\"configurations\":{}}"); }

    public static AnalysisParameterConfigurations parse(String text) {
        try {
            if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Analysis configurations exceed size limit.");
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Analysis configurations exceed size limit.");
            int tokens = 0;
            try (var parser = JSON.createParser(bytes)) { while (parser.nextToken() != null) if (++tokens > 10_000) throw new IllegalArgumentException("Analysis configuration token limit."); }
            JsonNode root = JSON.readTree(bytes);
            if (!(root instanceof ObjectNode) || !root.path("schemaVersion").isInt()
                    || root.path("schemaVersion").intValue() < 1) throw new IllegalArgumentException("Invalid configuration container.");
            if (root.path("schemaVersion").intValue() == 1) {
                if (!root.path("configurations").isObject() || root.path("configurations").size() > MAX_ENTRIES) throw new IllegalArgumentException("Invalid configuration entries.");
                var names = root.path("configurations").fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!name.matches("[A-Z][A-Z0-9_]{0,95}")) throw new IllegalArgumentException("Invalid configuration identity.");
                    JsonNode entry = root.path("configurations").get(name);
                    if (!entry.isObject() || !entry.path("configurationVersion").isInt()
                            || entry.path("configurationVersion").intValue() < 1) throw new IllegalArgumentException("Invalid configuration version.");
                    AnalysisKind kind = kind(name);
                    if (kind != null && entry.path("configurationVersion").intValue() == 1) decode(kind, entry);
                }
            }
            return new AnalysisParameterConfigurations(text);
        } catch (IOException exception) { throw new IllegalArgumentException("Invalid analysis configurations.", exception); }
    }

    public boolean supported() { return tree().path("schemaVersion").intValue() == 1; }
    public Optional<AnalysisParameterConfiguration> get(AnalysisKind kind) {
        if (!supported()) return Optional.empty();
        JsonNode entry = tree().path("configurations").get(kind.name());
        return entry == null || entry.path("configurationVersion").intValue() != 1 ? Optional.empty() : Optional.of(decode(kind, entry));
    }
    public AnalysisParameterConfigurations with(AnalysisParameterConfiguration configuration) {
        if (!supported()) throw new IllegalStateException("Future configuration container cannot be overwritten.");
        ObjectNode root = tree();
        ObjectNode entries = (ObjectNode) root.get("configurations");
        JsonNode prior = entries.get(configuration.kind().name());
        if (prior != null && prior.path("configurationVersion").intValue() != 1) throw new IllegalStateException("Future configuration entry cannot be overwritten.");
        entries.set(configuration.kind().name(), JSON.valueToTree(configuration));
        try { return parse(JSON.writeValueAsString(root)); }
        catch (IOException exception) { throw new IllegalArgumentException(exception); }
    }
    @JsonValue @JsonRawValue public String json() { return json; }

    private ObjectNode tree() {
        try { return (ObjectNode) JSON.readTree(json); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }
    private static AnalysisKind kind(String name) {
        try { return AnalysisKind.valueOf(name); } catch (IllegalArgumentException future) { return null; }
    }
    private static AnalysisParameterConfiguration decode(AnalysisKind kind, JsonNode entry) {
        try {
            Set<String> fields = Set.of("configurationVersion", "kind", "shape", "acceptedRunId", "acceptedAt", "parameters", "provenance");
            entry.fieldNames().forEachRemaining(name -> { if (!fields.contains(name)) throw new IllegalArgumentException("Unknown configuration field."); });
            if (!kind.name().equals(entry.path("kind").textValue())) throw new IllegalArgumentException("Configuration kind mismatch.");
            Class<? extends AnalysisParameterConfiguration.Parameters> type = kind.isMonteCarlo() ? AnalysisParameterConfiguration.MonteCarlo.class
                    : switch (kind) {
                        case COUPLE_QUICK -> AnalysisParameterConfiguration.Quick.class;
                        case COUPLE_EXHAUSTIVE, INDIVIDUAL_DETERMINISTIC -> AnalysisParameterConfiguration.Search.class;
                        default -> AnalysisParameterConfiguration.Mortality.class;
                    };
            var parameters = JSON.treeToValue(entry.get("parameters"), type);
            Map<String, String> provenance = new LinkedHashMap<>();
            var metadata = entry.get("provenance");
            if (metadata == null || !metadata.isObject()) throw new IllegalArgumentException("Missing provenance.");
            metadata.fields().forEachRemaining(e -> { if (!e.getValue().isTextual()) throw new IllegalArgumentException("Invalid provenance."); provenance.put(e.getKey(), e.getValue().textValue()); });
            return new AnalysisParameterConfiguration(1, kind,
                    AnalysisKind.Shape.valueOf(entry.path("shape").asText()),
                    UUID.fromString(entry.path("acceptedRunId").asText()), java.time.Instant.parse(entry.path("acceptedAt").asText()), parameters, provenance);
        } catch (IOException exception) { throw new IllegalArgumentException("Invalid typed analysis parameters.", exception); }
    }

    /** Copy exactly one nested JSON value; root trailing-token checks remain with the enclosing reader. */
    public static final class Reader extends JsonDeserializer<AnalysisParameterConfigurations> {
        @Override public AnalysisParameterConfigurations deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            var out = new ByteArrayOutputStream();
            try (JsonGenerator generator = JSON.createGenerator(out)) {
                int depth = 0, tokens = 0;
                do {
                    JsonToken token = parser.currentToken();
                    if (token == null || ++tokens > 10_000) throw new IOException("Invalid configuration tokens.");
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) depth++;
                    if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) depth--;
                    if (depth > 12) throw new IOException("Configuration nesting limit.");
                    if (token.isNumeric()) generator.writeNumber(parser.getText()); else generator.copyCurrentEvent(parser);
                    generator.flush();
                    if (out.size() > MAX_BYTES) throw new IOException("Configuration byte limit.");
                    if (depth > 0) parser.nextToken();
                } while (depth > 0);
            }
            return parse(out.toString(StandardCharsets.UTF_8));
        }
        @Override public AnalysisParameterConfigurations getNullValue(DeserializationContext context) throws JsonMappingException {
            throw JsonMappingException.from(context, "Explicit null analysis configurations are not supported; omit legacy section.");
        }
    }
}
