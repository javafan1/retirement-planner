package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

public final class ProjectionReadiness {

    private ProjectionReadiness() {
    }

    public static boolean isReady(
            RetirementPlan plan) {

        if (plan == null) {
            return false;
        }

        Household household =
                plan.getHousehold();

        if (household == null
                || household.getPrimaryPerson() == null
                || household.members().stream().anyMatch(person -> person.getBirthDate() == null)) {

            return false;
        }

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        return assumptions != null
                && assumptions.getProjectionStartDate() != null;
    }
}
