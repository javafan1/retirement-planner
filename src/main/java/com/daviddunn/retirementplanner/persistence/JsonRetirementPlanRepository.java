package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;

public class JsonRetirementPlanRepository
        implements RetirementPlanRepository {

    private final ObjectMapper mapper;

    public JsonRetirementPlanRepository() {

        mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void save(RetirementPlan plan,
                     Path file) throws IOException {

        plan.validateHouseholdReferences();
        mapper.writeValue(file.toFile(), plan);
    }

    @Override
    public RetirementPlan load(Path file)
            throws IOException {

        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        var json = mapper.readTree(bytes);
        if (json == null) {
            throw new IOException("Plan JSON is empty.");
        }
        validateExplicitStateConfiguration(json);
        // Deserialize original numeric tokens, preserving existing BigDecimal precision and scale.
        RetirementPlan plan = mapper.readValue(bytes, RetirementPlan.class);
        plan.validateHouseholdReferences();
        return plan;
    }

    private static void validateExplicitStateConfiguration(com.fasterxml.jackson.databind.JsonNode node) throws IOException {
        if (node.isObject() && node.has("stateTaxAssumptions") && node.get("stateTaxAssumptions").isNull()) {
            throw new IOException("Explicit stateTaxAssumptions must not be null; omit the field only for legacy Michigan plans.");
        }
        for (var child : node) {
            validateExplicitStateConfiguration(child);
        }
    }

    private static ObjectMapper createObjectMapper() {

        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }

   // private final ObjectMapper mapper = createObjectMapper();
}
