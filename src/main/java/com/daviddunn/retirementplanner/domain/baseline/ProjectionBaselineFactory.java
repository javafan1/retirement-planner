package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.time.LocalDateTime;
import java.util.Objects;

public class ProjectionBaselineFactory {

    public static ProjectionBaseline create(
            RetirementPlan plan,
            String description) {

        Objects.requireNonNull(
                plan,
                "Retirement plan is required.");

        Objects.requireNonNull(
                description,
                "Baseline description is required.");

        RetirementPlanSnapshot snapshot =
                RetirementPlanSnapshot
                        .fromRetirementPlan(plan);

        return new ProjectionBaseline(
                snapshot,
                LocalDateTime.now(),
                description);
    }

    private ProjectionBaselineFactory() {
        // Utility class.
    }
}