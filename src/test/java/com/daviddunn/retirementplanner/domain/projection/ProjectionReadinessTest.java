package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionReadinessTest {

    @Test
    void emptyNewPlanIsNotReadyWithoutHouseholdBirthDates() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        assertFalse(
                ProjectionReadiness.isReady(plan));
    }

    @Test
    void planBecomesReadyAfterBothBirthDatesAreEntered() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        plan.getHousehold()
                .getPrimaryPerson()
                .setBirthDate(LocalDate.of(1960, 1, 1));

        assertFalse(
                ProjectionReadiness.isReady(plan));

        plan.getHousehold()
                .getSpouse()
                .setBirthDate(LocalDate.of(1962, 1, 1));

        assertTrue(
                ProjectionReadiness.isReady(plan));
    }
}
