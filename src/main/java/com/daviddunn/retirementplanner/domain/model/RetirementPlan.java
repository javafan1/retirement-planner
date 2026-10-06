package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaseline;
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

    private Household household;
    private final AccountPortfolio accountPortfolio;

    private PlanningAssumptions planningAssumptions;

    private RothConversionRequest rothConversionRequest;

    private ProjectionBaseline baseline;

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
        validateHouseholdReferences();
    }

    /** Recheck at persistence/admission boundaries because account and income lists are mutable. */
    public void validateHouseholdReferences() {
        household.validatePersonReferences();
        accountPortfolio.getAccounts().forEach(account ->
                household.validateOwnership(account.getOwnership(), "account " + account.getName()));
        if (!household.hasSpouse()
                && planningAssumptions.getDeathScenarioAssumptions().getDeathScenario() != DeathScenario.BOTH_SURVIVE) {
            throw new IllegalArgumentException("Couple death scenarios are not supported for a single-person plan.");
        }
        if (baseline != null) {
            baseline.getSnapshot().validateHouseholdReferences();
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

    /** Explicit membership edit. Never deletes or reassigns financial records. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setSpouse(Person spouse) {
        if (household.spouse().orElse(null) == spouse) return;
        if (household.hasSpouse()) {
            Person existing = household.getSpouse();
            List<String> dependencies = new ArrayList<>();
            existing.getAccounts().forEach(account -> dependencies.add("account " + account.getName()));
            existing.getIncomeSources().forEach(income -> dependencies.add("income " + income.getName()));
            household.getPrimaryPerson().getAccounts().stream()
                    .filter(account -> account.getOwnership() != AccountOwnership.PRIMARY)
                    .forEach(account -> dependencies.add("account " + account.getName()));
            accountPortfolio.getAccounts().stream()
                    .filter(account -> account.getOwnership() != AccountOwnership.PRIMARY)
                    .forEach(account -> dependencies.add("account " + account.getName()));
            household.getPrimaryPerson().getIncomeSources().stream()
                    .filter(income -> income instanceof com.daviddunn.retirementplanner.domain.income.Pension)
                    .map(income -> (com.daviddunn.retirementplanner.domain.income.Pension) income)
                    .filter(pension -> pension.getSurvivorMonthlyBenefit() != null
                            && pension.getSurvivorMonthlyBenefit().signum() > 0)
                    .forEach(pension -> dependencies.add("survivor pension " + pension.getName()));
            if (planningAssumptions.getDeathScenarioAssumptions().getDeathScenario() != DeathScenario.BOTH_SURVIVE) {
                dependencies.add("couple death scenario");
            }
            if (!dependencies.isEmpty()) {
                throw new IllegalArgumentException("Cannot remove or replace spouse while these records depend on them: "
                        + String.join(", ", dependencies) + ". Update these records first.");
            }
        }
        Household replacement = new Household(household.getPrimaryPerson(), spouse);
        accountPortfolio.getAccounts().forEach(account ->
                replacement.validateOwnership(account.getOwnership(), "account " + account.getName()));
        household.getExpenses().forEach(replacement::addExpense);
        // Do not alter membership of a previously captured baseline household.
        household = replacement;
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

    public ProjectionBaseline getBaseline() {
        return baseline;
    }

    public void setBaseline(
            ProjectionBaseline baseline) {

        this.baseline = baseline;
    }

}
