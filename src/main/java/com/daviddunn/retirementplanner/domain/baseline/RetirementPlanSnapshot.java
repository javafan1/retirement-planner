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
    private final List<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition> conditionalBenefits;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    public List<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition> getConditionalBenefits() { return conditionalBenefits; }

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
                    nonInvestableAssets,
            @JsonProperty("conditionalBenefits") List<com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition> conditionalBenefits) {

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
        this.conditionalBenefits = com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefits.validatedCopy(conditionalBenefits, household);
        validateHouseholdReferences();
    }

    public RetirementPlanSnapshot(Household household, AccountPortfolio portfolio, PlanningAssumptions assumptions,
            RothConversionRequest roth, List<NonInvestableAsset> assets) {
        this(household, portfolio, assumptions, roth, assets, List.of());
    }

    public void validateHouseholdReferences() {
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefits.validatedCopy(conditionalBenefits, household);
        household.validatePersonReferences();
        accountPortfolio.getAccounts().forEach(account ->
                household.validateOwnership(account.getOwnership(), "baseline account " + account.getName()));
        if (!household.hasSpouse() && planningAssumptions.getDeathScenarioAssumptions().getDeathScenario()
                != com.daviddunn.retirementplanner.domain.model.DeathScenario.BOTH_SURVIVE) {
            throw new IllegalArgumentException("Couple death scenarios are not supported for a single-person baseline.");
        }
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

        RetirementPlanSnapshot snapshot = new RetirementPlanSnapshot(
                plan.getHousehold(),
                plan.getAccountPortfolio(),
                plan.getPlanningAssumptions(),
                plan.getRothConversionRequest(),
                plan.getNonInvestableAssets(), plan.getConditionalBenefits());
        // Preserve the existing JSON shape while isolating mutable household/accounts.
        // Copy only the snapshot, never the plan's previous baseline recursively.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            return mapper.readValue(mapper.writeValueAsBytes(snapshot), RetirementPlanSnapshot.class);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to freeze baseline snapshot.", exception);
        }
    }


}
