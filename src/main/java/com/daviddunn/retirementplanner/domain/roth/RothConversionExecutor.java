package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.projection.ProjectedAssetPools;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionExecutor {

    public ProjectedAssetPools execute(
            ProjectedAssetPools assetPools,
            BigDecimal conversionAmount) {

        Objects.requireNonNull(
                assetPools,
                "Projected asset pools are required.");

        Objects.requireNonNull(
                conversionAmount,
                "Conversion amount is required.");

        return assetPools.convertTaxDeferredToRoth(
                conversionAmount);
    }
}