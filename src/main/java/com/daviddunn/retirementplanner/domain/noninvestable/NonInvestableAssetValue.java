package com.daviddunn.retirementplanner.domain.noninvestable;

import java.math.BigDecimal;
import java.util.Objects;

public class NonInvestableAssetValue {

    private final String assetName;
    private final BigDecimal projectedValue;

    public NonInvestableAssetValue(
            String assetName,
            BigDecimal projectedValue) {

        this.assetName =
                Objects.requireNonNull(
                        assetName,
                        "Asset name is required.");

        this.projectedValue =
                Objects.requireNonNull(
                        projectedValue,
                        "Projected value is required.");
    }

    public String getAssetName() {
        return assetName;
    }

    public BigDecimal getProjectedValue() {
        return projectedValue;
    }
}