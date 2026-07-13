package com.daviddunn.retirementplanner.domain.model;

import java.util.Objects;

public class RetirementPlan {

    private final Household household;

    private final PlanningAssumptions planningAssumptions;

    public RetirementPlan(Household household,
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