package com.daviddunn.retirementplanner.domain.rmd;

import java.util.Set;
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

        var results = new java.util.EnumMap<AccountOwnership, OwnerRmdResult>(AccountOwnership.class);
        household.peopleByOwner().forEach((owner, person) -> results.put(owner,
                calculateOwnerRmd(portfolio, person, owner, projectionYear, governmentRules)));
        return new HouseholdRmdResult(results);
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

        return calculate(plan, balanceSnapshot, projectionYear, governmentRules,
                plan.getHousehold().peopleByOwner().keySet());
    }

    public HouseholdRmdResult calculate(
            RetirementPlan plan,
            RmdBalanceSnapshot balanceSnapshot,
            int projectionYear,
            GovernmentRules governmentRules,
            Set<AccountOwnership> eligibleOwners) {
        eligibleOwners = Set.copyOf(Objects.requireNonNull(eligibleOwners));
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

        var results = new java.util.EnumMap<AccountOwnership, OwnerRmdResult>(AccountOwnership.class);
        for (var entry : household.peopleByOwner().entrySet()) {
            var owner = entry.getKey();
            results.put(owner, eligibleOwners.contains(owner)
                    ? calculateOwnerRmd(portfolio, balanceSnapshot, entry.getValue(), owner, projectionYear, governmentRules)
                    : OwnerRmdResult.zero());
        }
        return new HouseholdRmdResult(results);
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