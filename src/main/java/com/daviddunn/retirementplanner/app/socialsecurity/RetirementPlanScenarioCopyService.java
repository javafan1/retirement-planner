package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/** Creates a complete in-memory JSON round-trip copy for isolated scenarios. */
public final class RetirementPlanScenarioCopyService {

    private final ObjectMapper mapper;

    public RetirementPlanScenarioCopyService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    public RetirementPlan copy(RetirementPlan source) {
        Objects.requireNonNull(source, "Source retirement plan is required.");
        try {
            return mapper.readValue(
                    mapper.writeValueAsBytes(source),
                    RetirementPlan.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create isolated retirement-plan scenario.",
                    exception);
        }
    }
}
