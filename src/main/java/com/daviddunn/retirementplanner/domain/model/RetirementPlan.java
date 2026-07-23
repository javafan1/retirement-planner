package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class RetirementPlan {

    private final Household household;
    private final AccountPortfolio accountPortfolio;

    private PlanningAssumptions planningAssumptions;

    @JsonCreator
    public RetirementPlan(
            @JsonProperty("household") Household household,
            @JsonProperty("accountPortfolio") AccountPortfolio accountPortfolio,
            @JsonProperty("planningAssumptions") PlanningAssumptions planningAssumptions) {

        this.household = Objects.requireNonNull(household);

        this.accountPortfolio =
                accountPortfolio != null
                        ? accountPortfolio
                        : new AccountPortfolio();

        this.planningAssumptions =
                Objects.requireNonNull(planningAssumptions);
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

    public void setPlanningAssumptions(
            PlanningAssumptions planningAssumptions) {

        this.planningAssumptions =
                Objects.requireNonNull(
                        planningAssumptions);
    }
}