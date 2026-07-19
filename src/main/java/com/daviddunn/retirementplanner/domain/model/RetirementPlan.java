package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class RetirementPlan {

    private final Household household;
    private final AccountPortfolio accountPortfolio;

    private final PlanningAssumptions planningAssumptions;

    @JsonCreator
    public RetirementPlan(
            @JsonProperty("household") Household household,
            @JsonProperty("accountPortfolio") AccountPortfolio accountPortfolio,
            @JsonProperty("planningAssumptions") PlanningAssumptions planningAssumptions) {

        this.household = household;
        this.accountPortfolio = accountPortfolio;
        this.planningAssumptions = planningAssumptions;
    }

    public Household getHousehold() {
        return household;
    }

    public PlanningAssumptions getPlanningAssumptions() {
        return planningAssumptions;
    }

    public AccountPortfolio getAccountPortfolio() {
        return accountPortfolio;
    }
}