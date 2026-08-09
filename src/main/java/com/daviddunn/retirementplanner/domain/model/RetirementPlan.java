package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class RetirementPlan {

    private final Household household;
    private final AccountPortfolio accountPortfolio;

    private PlanningAssumptions planningAssumptions;

    private RothConversionRequest rothConversionRequest;

    @JsonCreator
    public RetirementPlan(
            @JsonProperty("household")
            Household household,

            @JsonProperty("accountPortfolio")
            AccountPortfolio accountPortfolio,

            @JsonProperty("planningAssumptions")
            PlanningAssumptions planningAssumptions,

            @JsonProperty("rothConversionRequest")
            RothConversionRequest rothConversionRequest) {

        this.household =
                Objects.requireNonNull(
                        household);

        this.accountPortfolio =
                accountPortfolio != null
                        ? accountPortfolio
                        : new AccountPortfolio();

        this.planningAssumptions =
                Objects.requireNonNull(
                        planningAssumptions);

        this.rothConversionRequest =
                rothConversionRequest;
    }

    /*
     * Existing application compatibility constructor.
     *
     * Plans created before Roth conversion support
     * simply have no conversion request.
     */
    public RetirementPlan(
            Household household,
            AccountPortfolio accountPortfolio,
            PlanningAssumptions planningAssumptions) {

        this(
                household,
                accountPortfolio,
                planningAssumptions,
                null);
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

    public RothConversionRequest getRothConversionRequest() {
        return rothConversionRequest;
    }

    public void setPlanningAssumptions(
            PlanningAssumptions planningAssumptions) {

        this.planningAssumptions =
                Objects.requireNonNull(
                        planningAssumptions);
    }

    public void setRothConversionRequest(
            RothConversionRequest rothConversionRequest) {

        this.rothConversionRequest =
                rothConversionRequest;
    }
}