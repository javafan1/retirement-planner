package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class RetirementPlan {

    private final Household household;

    private final PlanningAssumptions planningAssumptions;

    @JsonCreator
    public RetirementPlan(
            @JsonProperty("household") Household household,
            @JsonProperty("planningAssumptions")
            PlanningAssumptions planningAssumptions) {

        this.household = household;
        this.planningAssumptions = planningAssumptions;
    }

    public Household getHousehold() {
        return household;
    }

    public PlanningAssumptions getPlanningAssumptions() {
        return planningAssumptions;
    }
}