package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
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
     * AccountPortfolio.
     *
     * Retained for existing callers and tests.
     */
    public HouseholdRmdResult calculate(
            RetirementPlan plan,
            int projectionYear,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                plan,
                "Retirement plan is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        Household household =
                plan.getHousehold();

        AccountPortfolio portfolio =
                plan.getAccountPortfolio();

        Person primary =
                household.getPrimaryPerson();

        Person spouse =
                household.getSpouse();

        OwnerRmdResult primaryResult =
                calculateOwnerRmd(
                        portfolio,
                        primary,
                        AccountOwnership.PRIMARY,
                        projectionYear,
                        governmentRules);

        OwnerRmdResult spouseResult =
                calculateOwnerRmd(
                        portfolio,
                        spouse,
                        AccountOwnership.SPOUSE,
                        projectionYear,
                        governmentRules);

        return new HouseholdRmdResult(
                primaryResult,
                spouseResult);
    }

    /*
     * Projection-safe calculation path.
     *
     * Uses balances captured in the applicable
     * prior December 31 snapshot.
     */
    public HouseholdRmdResult calculate(
            RetirementPlan plan,
            RmdBalanceSnapshot balanceSnapshot,
            int projectionYear,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                plan,
                "Retirement plan is required.");

        Objects.requireNonNull(
                balanceSnapshot,
                "RMD balance snapshot is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        Household household =
                plan.getHousehold();

        AccountPortfolio portfolio =
                plan.getAccountPortfolio();

        Person primary =
                household.getPrimaryPerson();

        Person spouse =
                household.getSpouse();

        OwnerRmdResult primaryResult =
                calculateOwnerRmd(
                        portfolio,
                        balanceSnapshot,
                        primary,
                        AccountOwnership.PRIMARY,
                        projectionYear,
                        governmentRules);

        OwnerRmdResult spouseResult =
                calculateOwnerRmd(
                        portfolio,
                        balanceSnapshot,
                        spouse,
                        AccountOwnership.SPOUSE,
                        projectionYear,
                        governmentRules);

        return new HouseholdRmdResult(
                primaryResult,
                spouseResult);
    }

    /*
     * Current-balance owner calculation.
     */
    private OwnerRmdResult calculateOwnerRmd(
            AccountPortfolio portfolio,
            Person person,
            AccountOwnership ownership,
            int projectionYear,
            GovernmentRules governmentRules) {

        /*
         * A newly created plan may not yet have
         * a birth date entered for this person.
         */
        if (person.getBirthDate() == null) {
            return OwnerRmdResult.zero();
        }

        return ownerRmdCalculator.calculate(
                portfolio,
                ownership,
                person.getBirthDate(),
                projectionYear,
                governmentRules);
    }

    /*
     * Snapshot-based owner calculation.
     */
    private OwnerRmdResult calculateOwnerRmd(
            AccountPortfolio portfolio,
            RmdBalanceSnapshot balanceSnapshot,
            Person person,
            AccountOwnership ownership,
            int projectionYear,
            GovernmentRules governmentRules) {

        /*
         * A newly created plan may not yet have
         * a birth date entered for this person.
         */
        if (person.getBirthDate() == null) {
            return OwnerRmdResult.zero();
        }

        return ownerRmdCalculator.calculate(
                portfolio,
                balanceSnapshot,
                ownership,
                person.getBirthDate(),
                projectionYear,
                governmentRules);
    }
}