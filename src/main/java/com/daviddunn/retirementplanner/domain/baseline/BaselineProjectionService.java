package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;

import java.util.Objects;

public class BaselineProjectionService {

    private final ProjectionEngine projectionEngine;

    public BaselineProjectionService(
            ProjectionEngine projectionEngine) {

        this.projectionEngine =
                Objects.requireNonNull(
                        projectionEngine);
    }

    public Projection projectBaseline(
            ProjectionBaseline baseline) {

        Objects.requireNonNull(
                baseline,
                "Baseline is required.");

        RetirementPlanSnapshot snapshot =
                baseline.getSnapshot();

        RetirementPlan baselinePlan =
                new RetirementPlan(
                        snapshot.getHousehold(),
                        snapshot.getAccountPortfolio(),
                        snapshot.getPlanningAssumptions(),
                        snapshot.getRothConversionRequest(),
                        snapshot.getNonInvestableAssets());

        return projectionEngine.project(
                baselinePlan);
    }
}