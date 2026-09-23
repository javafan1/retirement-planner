package com.daviddunn.retirementplanner.ui.summary;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.util.List;

/** Captures the view's ordered results, selected object and associated asset results at opening. */
public record ProjectionYearDetailsRequest(
        List<ProjectionYear> years,
        ProjectionYear initialYear,
        List<NonInvestableAssetProjection> nonInvestableAssets) {

    public ProjectionYearDetailsRequest {
        years = years == null ? List.of() : List.copyOf(years);
        nonInvestableAssets = nonInvestableAssets == null ? List.of() : List.copyOf(nonInvestableAssets);
    }
}
