package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetirementPlanSnapshotTest {

    @Test
    void savedSnapshotDoesNotShareMutableHouseholdWithCurrent() {
        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();
        RetirementPlanSnapshot snapshot = RetirementPlanSnapshot.fromRetirementPlan(plan);
        org.junit.jupiter.api.Assertions.assertNotSame(plan.getHousehold(), snapshot.getHousehold());
        org.junit.jupiter.api.Assertions.assertNotSame(plan.getHousehold().getPrimaryPerson(),
                snapshot.getHousehold().getPrimaryPerson());
        org.junit.jupiter.api.Assertions.assertNotSame(plan.getAccountPortfolio(), snapshot.getAccountPortfolio());
    }

    @Test
    void snapshotPreservesPlanData() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        RetirementPlanSnapshot snapshot =
                new RetirementPlanSnapshot(
                        plan.getHousehold(),
                        plan.getAccountPortfolio(),
                        plan.getPlanningAssumptions(),
                        plan.getRothConversionRequest(),
                        plan.getNonInvestableAssets());

        assertNotNull(
                snapshot.getHousehold());

        assertNotNull(
                snapshot.getAccountPortfolio());

        assertNotNull(
                snapshot.getPlanningAssumptions());

        assertNotNull(
                snapshot.getNonInvestableAssets());
    }

    @Test
    void createsSnapshotFromRetirementPlan() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        RetirementPlanSnapshot snapshot =
                RetirementPlanSnapshot
                        .fromRetirementPlan(plan);

        assertNotNull(
                snapshot);

        assertNotNull(
                snapshot.getHousehold());

        assertNotNull(
                snapshot.getAccountPortfolio());

        assertNotNull(
                snapshot.getPlanningAssumptions());

        assertNotNull(
                snapshot.getNonInvestableAssets());
    }

    @Test
    void createsBaselineFromRetirementPlan() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        ProjectionBaseline baseline =
                ProjectionBaselineFactory.create(
                        plan,
                        "Original Retirement Plan");

        assertNotNull(
                baseline);

        assertNotNull(
                baseline.getSnapshot());

        assertNotNull(
                baseline.getSavedAt());

        assertEquals(
                "Original Retirement Plan",
                baseline.getDescription());
    }
}
