package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import java.util.List;
import java.util.Objects;

public record BreakEvenProjectionSnapshot(
        List<ProjectionYear> years,
        List<NonInvestableAssetProjection> nonInvestableAssets,
        BreakEvenPlanSummary assumptions) {
    public BreakEvenProjectionSnapshot {
        years = List.copyOf(years);
        nonInvestableAssets = List.copyOf(nonInvestableAssets);
        Objects.requireNonNull(assumptions);
    }
}
