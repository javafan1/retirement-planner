package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RetirementPlan {

    private final Household household;
    private final AccountPortfolio accountPortfolio;

    private PlanningAssumptions planningAssumptions;

    private RothConversionRequest rothConversionRequest;

    private final List<NonInvestableAsset>
            nonInvestableAssets =
            new ArrayList<>();
    @JsonCreator
    public RetirementPlan(
            @JsonProperty("household")
            Household household,

            @JsonProperty("accountPortfolio")
            AccountPortfolio accountPortfolio,

            @JsonProperty("planningAssumptions")
            PlanningAssumptions planningAssumptions,

            @JsonProperty("rothConversionRequest")
            RothConversionRequest rothConversionRequest,

            @JsonProperty("nonInvestableAssets")
            List<NonInvestableAsset> nonInvestableAssets) {

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

        if (nonInvestableAssets != null) {
            this.nonInvestableAssets.addAll(
                    nonInvestableAssets);
        }
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
                null,
                null);
    }

    public RetirementPlan(
            Household household,
            AccountPortfolio accountPortfolio,
            PlanningAssumptions planningAssumptions,
            RothConversionRequest rothConversionRequest) {

        this(
                household,
                accountPortfolio,
                planningAssumptions,
                rothConversionRequest,
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
    public List<NonInvestableAsset>
    getNonInvestableAssets() {

        return Collections.unmodifiableList(
                nonInvestableAssets);
    }

    public void addNonInvestableAsset(
            NonInvestableAsset asset) {

        nonInvestableAssets.add(
                Objects.requireNonNull(asset));
    }

    public void removeNonInvestableAsset(
            NonInvestableAsset asset) {

        nonInvestableAssets.remove(
                Objects.requireNonNull(asset));
    }

    public void replaceNonInvestableAsset(
            NonInvestableAsset oldAsset,
            NonInvestableAsset newAsset) {

        Objects.requireNonNull(oldAsset);
        Objects.requireNonNull(newAsset);

        int index =
                nonInvestableAssets.indexOf(
                        oldAsset);

        if (index >= 0) {
            nonInvestableAssets.set(
                    index,
                    newAsset);
        }
    }

    public void setNonInvestableAssets(
            List<NonInvestableAsset> assets) {

        nonInvestableAssets.clear();

        if (assets != null) {
            nonInvestableAssets.addAll(
                    assets);
        }
    }

}