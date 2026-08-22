package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class RetirementPlanSnapshot {

    private final Household household;

    private final AccountPortfolio accountPortfolio;

    private final PlanningAssumptions
            planningAssumptions;

    private final RothConversionRequest
            rothConversionRequest;

    private final List<NonInvestableAsset>
            nonInvestableAssets;

    @JsonCreator
    public RetirementPlanSnapshot(
            @JsonProperty("household")
            Household household,

            @JsonProperty("accountPortfolio")
            AccountPortfolio accountPortfolio,

            @JsonProperty("planningAssumptions")
            PlanningAssumptions planningAssumptions,

            @JsonProperty("rothConversionRequest")
            RothConversionRequest rothConversionRequest,

            @JsonProperty("nonInvestableAssets")
            List<NonInvestableAsset>
                    nonInvestableAssets) {

        this.household =
                Objects.requireNonNull(
                        household);

        this.accountPortfolio =
                Objects.requireNonNull(
                        accountPortfolio);

        this.planningAssumptions =
                Objects.requireNonNull(
                        planningAssumptions);

        this.rothConversionRequest =
                rothConversionRequest;

        this.nonInvestableAssets =
                List.copyOf(
                        Objects.requireNonNull(
                                nonInvestableAssets));
    }

    public Household getHousehold() {
        return household;
    }

    public AccountPortfolio getAccountPortfolio() {
        return accountPortfolio;
    }

    public PlanningAssumptions
    getPlanningAssumptions() {

        return planningAssumptions;
    }

    public RothConversionRequest
    getRothConversionRequest() {

        return rothConversionRequest;
    }

    public List<NonInvestableAsset>
    getNonInvestableAssets() {

        return nonInvestableAssets;
    }

    public static RetirementPlanSnapshot
    fromRetirementPlan(
            RetirementPlan plan) {

        Objects.requireNonNull(
                plan,
                "Retirement plan is required.");

        return new RetirementPlanSnapshot(
                plan.getHousehold(),
                plan.getAccountPortfolio(),
                plan.getPlanningAssumptions(),
                plan.getRothConversionRequest(),
                plan.getNonInvestableAssets());
    }


}