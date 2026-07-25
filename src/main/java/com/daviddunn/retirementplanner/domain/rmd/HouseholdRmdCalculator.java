package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.util.Objects;

public final class HouseholdRmdCalculator {

    private final OwnerRmdCalculator ownerRmdCalculator;

    public HouseholdRmdCalculator() {
        this(new OwnerRmdCalculator());
    }

    public HouseholdRmdCalculator(
            OwnerRmdCalculator ownerRmdCalculator) {

        this.ownerRmdCalculator =
                Objects.requireNonNull(
                        ownerRmdCalculator,
                        "Owner RMD calculator is required.");
    }

    /*
     * Existing calculation path.
     *
     * Uses the current balances stored in the
     * retirement plan's AccountPortfolio.
     *
     * Retained for existing callers and tests.
     */
    public HouseholdRmdResult calculate(
            RetirementPlan plan,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                plan,
                governmentRules);

        Household household =
                plan.getHousehold();

        Person primary =
                household.getPrimaryPerson();

        Person spouse =
                household.getSpouse();

        OwnerRmdResult primaryResult =
                ownerRmdCalculator.calculate(
                        plan.getAccountPortfolio(),
                        AccountOwnership.PRIMARY,
                        primary.getBirthDate(),
                        projectionYear,
                        governmentRules);

        OwnerRmdResult spouseResult =
                ownerRmdCalculator.calculate(
                        plan.getAccountPortfolio(),
                        AccountOwnership.SPOUSE,
                        spouse.getBirthDate(),
                        projectionYear,
                        governmentRules);

        return new HouseholdRmdResult(
                primaryResult,
                spouseResult);
    }

    /*
     * Projection-safe calculation path.
     *
     * Account information comes from the
     * RetirementPlan, while balances come from
     * the applicable prior December 31 snapshot.
     */
    public HouseholdRmdResult calculate(
            RetirementPlan plan,
            RmdBalanceSnapshot balanceSnapshot,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                plan,
                governmentRules);

        Objects.requireNonNull(
                balanceSnapshot,
                "RMD balance snapshot is required.");

        Household household =
                plan.getHousehold();

        Person primary =
                household.getPrimaryPerson();

        Person spouse =
                household.getSpouse();

        OwnerRmdResult primaryResult =
                ownerRmdCalculator.calculate(
                        plan.getAccountPortfolio(),
                        balanceSnapshot,
                        AccountOwnership.PRIMARY,
                        primary.getBirthDate(),
                        projectionYear,
                        governmentRules);

        OwnerRmdResult spouseResult =
                ownerRmdCalculator.calculate(
                        plan.getAccountPortfolio(),
                        balanceSnapshot,
                        AccountOwnership.SPOUSE,
                        spouse.getBirthDate(),
                        projectionYear,
                        governmentRules);

        return new HouseholdRmdResult(
                primaryResult,
                spouseResult);
    }

    private void validateArguments(
            RetirementPlan plan,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                plan,
                "Retirement plan is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");
    }
}