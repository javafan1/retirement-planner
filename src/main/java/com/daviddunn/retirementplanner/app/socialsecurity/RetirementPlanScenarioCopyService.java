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
            byte[] bytes = mapper.writeValueAsBytes(source);
            if (source.getProjectionState() != null) bytes = com.daviddunn.retirementplanner.persistence.PlanConfigurationTransaction.replace(bytes, "projectionState", null);
            return mapper.readValue(
                    bytes,
                    RetirementPlan.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create isolated retirement-plan scenario.",
                    exception);
        }
    }

    /** A distinct scenario retains editable financial inputs and owns no results from the original plan. */
    public RetirementPlan copyAsScenario(RetirementPlan source) {
        var scenario = copy(source);
        scenario.assignPlanId(java.util.UUID.randomUUID());
        var baseline = scenario.getBaseline();
        if (baseline != null && baseline.getProjectionSnapshotId() != null) {
            scenario.setBaseline(new com.daviddunn.retirementplanner.domain.baseline.ProjectionBaseline(
                    baseline.getSnapshot(), baseline.getSavedAt(), baseline.getDescription()));
        }
        return scenario;
    }
}
