package com.daviddunn.retirementplanner.app.baseline;

import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.*;

/** In-memory configured input values. No live domain references, calculations or durable schema. */
public record ProjectionInputs(Fields root) {
    public sealed interface Value permits Fields, Items, NumberValue, TextValue, BooleanValue, Empty { }
    public record Fields(Map<String, Value> values) implements Value {
        public Fields { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    public record Items(List<Value> values) implements Value {
        public Items { values = List.copyOf(values); }
    }
    public record NumberValue(BigDecimal value) implements Value {
        public NumberValue { Objects.requireNonNull(value); }
    }
    public record TextValue(String value) implements Value {
        public TextValue { Objects.requireNonNull(value); }
    }
    public record BooleanValue(boolean value) implements Value { }
    public enum Empty implements Value { NULL, ABSENT, NOT_RECORDED }

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    public ProjectionInputs { Objects.requireNonNull(root); }
    public static ProjectionInputs capture(RetirementPlan plan) {
        return capture(new RetirementPlanSnapshot(plan.getHousehold(), plan.getAccountPortfolio(),
                plan.getPlanningAssumptions(), plan.getRothConversionRequest(), plan.getNonInvestableAssets()));
    }
    public static ProjectionInputs capture(RetirementPlanSnapshot snapshot) {
        return new ProjectionInputs((Fields) freeze(JSON.valueToTree(snapshot)));
    }
    private static Value freeze(JsonNode node) {
        if (node.isNull()) return Empty.NULL;
        if (node.isObject()) {
            var fields = new LinkedHashMap<String, Value>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), freeze(entry.getValue())));
            return new Fields(fields);
        }
        if (node.isArray()) {
            var items = new ArrayList<Value>(); node.forEach(item -> items.add(freeze(item))); return new Items(items);
        }
        if (node.isNumber()) return new NumberValue(node.decimalValue());
        if (node.isBoolean()) return new BooleanValue(node.booleanValue());
        if (node.isTextual()) return new TextValue(node.textValue());
        throw new IllegalArgumentException("Unsupported configured input value.");
    }
}
