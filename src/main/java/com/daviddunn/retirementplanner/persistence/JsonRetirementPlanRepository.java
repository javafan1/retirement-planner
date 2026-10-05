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

        RetirementPlan plan = mapper.readValue(file.toFile(), RetirementPlan.class);
        plan.validateHouseholdReferences();
        return plan;
    }

    private static ObjectMapper createObjectMapper() {

        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }

   // private final ObjectMapper mapper = createObjectMapper();
}
