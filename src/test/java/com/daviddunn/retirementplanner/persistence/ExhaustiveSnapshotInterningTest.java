package com.daviddunn.retirementplanner.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExhaustiveSnapshotInterningTest {
    final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    final ExhaustiveSnapshotInterning codec = new ExhaustiveSnapshotInterning(mapper);

    ObjectNode document() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"baseline":{"accounts":[{"identity":0},{"identity":1}],"years":[{"value":1.2300}]},
                 "entries":[{"metrics":{"a":1.2300},"differences":{"a":1.2300},
                 "detail":{"accounts":[{"identity":0},{"identity":1}],"years":[{"value":1.2300}]}},
                 {"metrics":{"a":1.23},"differences":null,"detail":null}]}
                """);
    }

    @Test void deterministicExactTypedReferencesPreserveScaleAndOrder() throws Exception {
        var original = document();
        byte[] bytes = mapper.writeValueAsBytes(original);
        var compact = codec.encode(original.deepCopy());
        assertArrayEquals(mapper.writeValueAsBytes(compact), mapper.writeValueAsBytes(codec.encode(document())));
        assertEquals(2, compact.path("tables").path("metrics").size());
        assertEquals(1, compact.path("tables").path("accounts").size());
        assertEquals(1, compact.path("tables").path("years").size());
        assertArrayEquals(bytes, mapper.writeValueAsBytes(codec.decode(compact)));
    }

    @ParameterizedTest @ValueSource(strings={"version", "unknown", "missingTable", "duplicateValue", "unused",
            "wrongType", "negative", "outOfRange", "fraction", "extraRef", "missingRef", "nested", "missingValue"})
    void malformedTablesAndReferencesAreRejected(String defect) throws Exception {
        var compact = codec.encode(document());
        var tables = (ObjectNode) compact.get("tables");
        var entry = (ObjectNode) compact.path("document").path("entries").get(0);
        switch (defect) {
            case "version" -> compact.put("exhaustiveRepresentation", 99);
            case "unknown" -> compact.put("unknown", true);
            case "missingTable" -> tables.remove("years");
            case "duplicateValue" -> ((ArrayNode) tables.get("metrics")).add(tables.get("metrics").get(0));
            case "unused" -> ((ArrayNode) tables.get("metrics")).add(mapper.createObjectNode().put("unique", 8));
            case "wrongType" -> ((ArrayNode) tables.get("metrics")).set(0, mapper.createArrayNode());
            case "negative" -> ((ObjectNode) entry.get("metrics")).put("$ref", -1);
            case "outOfRange" -> ((ObjectNode) entry.get("metrics")).put("$ref", 100_000);
            case "fraction" -> ((ObjectNode) entry.get("metrics")).put("$ref", 0.5);
            case "extraRef" -> ((ObjectNode) entry.get("metrics")).put("type", "years");
            case "missingRef" -> entry.remove("metrics");
            case "nested" -> ((ObjectNode) tables.get("metrics").get(0)).put("$ref", 0);
            case "missingValue" -> tables.putNull("metrics");
            default -> throw new AssertionError();
        }
        assertThrows(IOException.class, () -> codec.decode(compact));
    }

    @Test void amplificationIsRejectedBeforeDtoReconstruction() throws Exception {
        var compact = codec.encode(document());
        var metrics = (ArrayNode) compact.path("tables").get("metrics");
        ((ObjectNode) metrics.get(0)).put("a", "x".repeat(200_000));
        var entries = (ArrayNode) compact.path("document").get("entries");
        var entry = entries.get(0);
        for (int i = 0; i < 1_000; i++) entries.add(entry.deepCopy());
        assertTrue(mapper.writeValueAsBytes(compact).length < AnalysisSnapshotCodec.MAX_RAW_BYTES);
        var failure = assertThrows(IOException.class, () -> codec.decode(compact));
        assertTrue(failure.getMessage().contains("work limit"));
    }

    @Test void oversizedTablesAndCollectionsAreRejected() throws Exception {
        var compact = codec.encode(document());
        var years = (ArrayNode) compact.path("tables").get("years").get(0);
        for (int i = 0; i < 100_000; i++) years.addNull();
        assertThrows(IOException.class, () -> codec.decode(compact));
        var another = codec.encode(document());
        var table = (ArrayNode) another.path("tables").get("metrics");
        for (int i = 0; i < 100_000; i++) table.addNull();
        assertThrows(IOException.class, () -> codec.decode(another));
    }
}
