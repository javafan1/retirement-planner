package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaseline;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetirementPlanTest {

    @Test
    void canStoreProjectionBaseline() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        RetirementPlanSnapshot snapshot =
                new RetirementPlanSnapshot(
                        plan.getHousehold(),
                        plan.getAccountPortfolio(),
                        plan.getPlanningAssumptions(),
                        plan.getRothConversionRequest(),
                        plan.getNonInvestableAssets());

        ProjectionBaseline baseline =
                new ProjectionBaseline(
                        snapshot,
                        LocalDateTime.now(),
                        "Test Baseline");

        plan.setBaseline(
                baseline);

        assertNotNull(
                plan.getBaseline());

        assertEquals(
                "Test Baseline",
                plan.getBaseline()
                        .getDescription());

        assertNotNull(
                plan.getBaseline()
                        .getSnapshot());
    }
}