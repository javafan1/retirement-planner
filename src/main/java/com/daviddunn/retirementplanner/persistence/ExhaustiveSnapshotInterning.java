package com.daviddunn.retirementplanner.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.util.*;

/** Exhaustive wire representation v2. Only immutable storage values are shared, never domain accounts. */
final class ExhaustiveSnapshotInterning {
    static final int VERSION = 2;
    static final int MAX_TABLE_VALUES = 100_000;
    static final int MAX_REFERENCES = 100_000;
    // Logical-work budgets, independent of (and not changes to) the 32 MiB physical document limit.
    static final long MAX_EXPANDED_TOKENS = 8_000_000;
    static final long MAX_EXPANDED_CHARACTERS = 128L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("metrics", "accounts", "years");
    private final ObjectMapper mapper;

    ExhaustiveSnapshotInterning(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode encode(ObjectNode payload) throws IOException {
        var tables = mapper.createObjectNode();
        var keys = new HashMap<String, Map<String, Integer>>();
        for (String type : List.of("metrics", "accounts", "years")) {
            tables.set(type, mapper.createArrayNode());
            keys.put(type, new HashMap<>());
        }
        visitSlots(payload, (owner, field, type) -> {
            JsonNode value = owner.get(field);
            if (value == null || value.isNull()) return;
            byte[] exact = mapper.writeValueAsBytes(value);
            String key = AnalysisSnapshotCodec.sha256(exact);
            var table = (ArrayNode) tables.get(type);
            Integer index = keys.get(type).get(key);
            if (index == null) {
                if (table.size() >= MAX_TABLE_VALUES) throw invalid("Intern table limit exceeded.");
                index = table.size();
                keys.get(type).put(key, index);
                table.add(value);
            } else if (!Arrays.equals(exact, mapper.writeValueAsBytes(table.get(index)))) {
                throw invalid("Intern value digest collision.");
            }
            owner.set(field, mapper.createObjectNode().put("$ref", index));
        });
        var wrapper = mapper.createObjectNode().put("exhaustiveRepresentation", VERSION);
        wrapper.set("tables", tables);
        wrapper.set("document", payload);
        // Apply the same expansion policy to our own output before it can be committed.
        decode(wrapper.deepCopy());
        return wrapper;
    }

    ObjectNode decode(ObjectNode wrapper) throws IOException {
        fields(wrapper, Set.of("exhaustiveRepresentation", "tables", "document"));
        if (!wrapper.path("exhaustiveRepresentation").isInt()
                || wrapper.path("exhaustiveRepresentation").intValue() != VERSION)
            throw invalid("Unsupported Exhaustive representation.");
        ObjectNode tables = object(wrapper.get("tables"));
        fields(tables, TYPES);
        var used = new HashMap<String, BitSet>();
        int total = 0;
        for (String type : TYPES) {
            if (!(tables.get(type) instanceof ArrayNode table)) throw invalid("Invalid intern table.");
            total += table.size();
            if (total > MAX_TABLE_VALUES) throw invalid("Intern table limit exceeded.");
            var identities = new HashSet<String>();
            for (JsonNode value : table) {
                if (type.equals("metrics") ? !value.isObject() : !value.isArray())
                    throw invalid("Incorrect intern value type.");
                rejectReferences(value);
                if (!identities.add(AnalysisSnapshotCodec.sha256(mapper.writeValueAsBytes(value))))
                    throw invalid("Duplicate intern value.");
            }
            used.put(type, new BitSet(table.size()));
        }
        ObjectNode document = object(wrapper.get("document"));
        int[] references = {0};
        visitSlots(document, (owner, field, type) -> {
            JsonNode ref = owner.get(field);
            if (ref == null) throw invalid("Missing intern reference.");
            if (ref.isNull()) return;
            ObjectNode reference = object(ref);
            fields(reference, Set.of("$ref"));
            if (!reference.path("$ref").isInt()) throw invalid("Invalid intern index.");
            int index = reference.path("$ref").intValue();
            var table = (ArrayNode) tables.get(type);
            if (index < 0 || index >= table.size()) throw invalid("Intern reference out of range.");
            if (++references[0] > MAX_REFERENCES) throw invalid("Intern reference limit exceeded.");
            used.get(type).set(index);
            owner.set(field, table.get(index));
        });
        for (String type : TYPES) if (used.get(type).cardinality() != tables.get(type).size())
            throw invalid("Unused intern value.");
        // No recursively resolved references: pools contain plain values and only allowlisted slots accept indices.
        // Walk logical occurrences (including shared nodes) before any DTO/domain allocation.
        budget(document, new long[2], 0);
        return document;
    }

    private static void budget(JsonNode node, long[] totals, int depth) throws IOException {
        if (depth > AnalysisSnapshotCodec.MAX_DEPTH) throw invalid("Expanded nesting limit exceeded.");
        totals[0] += node.isContainerNode() ? 2 : 1;
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getKey().equals("$ref")) throw invalid("Unexpected intern reference.");
                totals[0]++;
                totals[1] += field.getKey().length();
                budget(field.getValue(), totals, depth + 1);
            }
        } else if (node.isArray()) {
            if (node.size() > AnalysisSnapshotCodec.MAX_ARRAYS) throw invalid("Expanded collection limit exceeded.");
            for (JsonNode value : node) budget(value, totals, depth + 1);
        } else {
            totals[1] += node.asText().length();
        }
        if (totals[0] > MAX_EXPANDED_TOKENS || totals[1] > MAX_EXPANDED_CHARACTERS)
            throw invalid("Expanded snapshot work limit exceeded.");
    }

    private static void rejectReferences(JsonNode node) throws IOException {
        if (node.isObject() && node.has("$ref")) throw invalid("Nested intern reference is forbidden.");
        for (JsonNode child : node) rejectReferences(child);
    }

    private interface Slot {
        void accept(ObjectNode owner, String field, String type) throws IOException;
    }

    private static void visitSlots(ObjectNode payload, Slot slot) throws IOException {
        projection(object(payload.get("baseline")), slot);
        if (!(payload.get("entries") instanceof ArrayNode entries)
                || entries.size() > AnalysisSnapshotCodec.MAX_ARRAYS) throw invalid("Invalid Exhaustive entries.");
        for (JsonNode value : entries) {
            ObjectNode entry = object(value);
            slot.accept(entry, "metrics", "metrics");
            slot.accept(entry, "differences", "metrics");
            if (entry.get("detail") == null) throw invalid("Missing retained detail state.");
            if (!entry.get("detail").isNull()) projection(object(entry.get("detail")), slot);
        }
    }

    private static void projection(ObjectNode projection, Slot slot) throws IOException {
        slot.accept(projection, "accounts", "accounts");
        slot.accept(projection, "years", "years");
    }

    private static ObjectNode object(JsonNode node) throws IOException {
        if (!(node instanceof ObjectNode object)) throw invalid("Expected an Exhaustive object.");
        return object;
    }

    private static void fields(ObjectNode node, Set<String> expected) throws IOException {
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw invalid("Unexpected or missing intern fields.");
    }

    private static IOException invalid(String message) {
        return new IOException(message);
    }
}
