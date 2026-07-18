package com.daviddunn.retirementplanner.domain.factory;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;

public final class RetirementPlanFactory {

    private RetirementPlanFactory() {
    }

    public static RetirementPlan createEmptyPlan() {

        Person primary = new Person("", "", null);
        Person spouse = new Person("", "", null);

        Household household =
                new Household(primary, spouse);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.03"),
                        new BigDecimal("0.08"));

        return new RetirementPlan(
                household,
                assumptions);
    }
}