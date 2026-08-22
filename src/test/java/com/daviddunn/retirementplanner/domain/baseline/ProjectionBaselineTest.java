package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectionBaselineTest {

    @Test
    void baselinePreservesSnapshotMetadata() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        RetirementPlanSnapshot snapshot =
                new RetirementPlanSnapshot(
                        plan.getHousehold(),
                        plan.getAccountPortfolio(),
                        plan.getPlanningAssumptions(),
                        plan.getRothConversionRequest(),
                        plan.getNonInvestableAssets());

        LocalDateTime savedAt =
                LocalDateTime.now();

        ProjectionBaseline baseline =
                new ProjectionBaseline(
                        snapshot,
                        savedAt,
                        "Original Retirement Plan");

        assertNotNull(
                baseline.getSnapshot());

        assertEquals(
                savedAt,
                baseline.getSavedAt());

        assertEquals(
                "Original Retirement Plan",
                baseline.getDescription());
    }
}