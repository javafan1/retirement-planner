package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;

import java.util.Optional;

/** Algorithm applicability only: never changes input identity or evaluates a projection. */
public final class PensionSelectionSemantics {
    public static final String KEY = "pension-selection";
    public static final String CORRECTED = "death-selected-v2";
    public static final String PROVENANCE = KEY + "=" + CORRECTED + "; ";

    private PensionSelectionSemantics() { }

    public static boolean potentiallyAffected(RetirementPlan plan) {
        return potentiallyAffected(plan.getHousehold(), plan.getPlanningAssumptions());
    }

    public static boolean potentiallyAffected(RetirementPlan plan, int first, int last) {
        return potentiallyAffected(plan.getHousehold(), plan.getPlanningAssumptions(), first, last);
    }

    /** A missing/invalid legacy context cannot establish unaffected financial semantics. */
    public static boolean potentiallyAffected(Household household, PlanningAssumptions assumptions) {
        try {
            int first = assumptions.getProjectionStartDate().getYear();
            return potentiallyAffected(household, assumptions, first,
                    Math.addExact(first, assumptions.getProjectionLengthYears() - 1));
        } catch (RuntimeException unavailable) {
            return true;
        }
    }

    private static boolean potentiallyAffected(Household household, PlanningAssumptions assumptions, int first, int last) {
        try {
            if (first > last || first < 1 || last > 9999) return true;
            var deaths = assumptions.getDeathScenarioAssumptions();
            if (deaths.getDeathScenario() == DeathScenario.BOTH_SURVIVE) return false;
            int death = deaths.getDeathYear();
            if (death > last) return false;
            int affectedFirst = Math.max(first, death);
            var view = EffectiveHouseholdDeathView.resolve(household, deaths, Optional.empty());
            for (var person : household.peopleByOwner().entrySet()) {
                for (var source : person.getValue().getIncomeSources()) {
                    if (!(source instanceof Pension pension)) continue;
                    if (view.isAlive(pension.getOwnership(), affectedFirst)
                            && view.isAlive(person.getKey(), affectedFirst)) continue;
                    if (pension.getStartDate().getYear() > last
                            || pension.getEndDate() != null && pension.getEndDate().getYear() < affectedFirst) continue;
                    if (pension.getMonthlyBenefit().signum() != 0
                            || pension.getSurvivorMonthlyBenefit() != null
                            && pension.getSurvivorMonthlyBenefit().signum() != 0) return true;
                }
            }
            return false;
        } catch (RuntimeException unavailable) {
            return true;
        }
    }
}
