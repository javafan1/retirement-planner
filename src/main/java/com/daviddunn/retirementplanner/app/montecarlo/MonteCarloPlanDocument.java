package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Frozen configured inputs using the existing allowlisted plan schema, never Java object serialization. */
final class MonteCarloPlanDocument {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(4096).maxNestingDepth(80).maxStringLength(256000).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .registerModule(new JavaTimeModule()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    static {
        var scalars = new com.fasterxml.jackson.databind.module.SimpleModule();
        scalars.addDeserializer(java.math.BigDecimal.class, new JsonDeserializer<>() {
            @Override public java.math.BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
                return MonteCarloSnapshotNumbers.check(parser.getDecimalValue());
            }
        });
        JSON.registerModule(scalars);
    }
    private MonteCarloPlanDocument() { }
    static String capture(RetirementPlan plan) {
        try {
            byte[] bytes = JSON.writeValueAsBytes(plan);
            if (plan.getProjectionState() != null) bytes = com.daviddunn.retirementplanner.persistence.PlanConfigurationTransaction.replace(bytes, "projectionState", null);
            if (plan.getAnalyzerConfigurations() != null) bytes = com.daviddunn.retirementplanner.persistence.PlanConfigurationTransaction.replace(bytes, "analyzerConfigurations", null);
            var json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8); read(json); return json;
        }
        catch (Exception e) { throw new IllegalArgumentException("Cannot freeze Monte Carlo plan inputs.", e); }
    }
    static RetirementPlan read(String json) {
        if (json == null || json.length() > 256000) throw new IllegalArgumentException("Frozen plan exceeds its bounded context limit.");
        try { return JSON.readValue(json, RetirementPlan.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid frozen Monte Carlo plan inputs.", e); }
    }
}
