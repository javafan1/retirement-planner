package com.daviddunn.retirementplanner.domain.projection.statistics;

import java.math.BigDecimal;
import java.util.Objects;

public class ProjectionStatistics {

    private final BigDecimal endingInvestableAssets;
    private final BigDecimal lowestInvestableAssets;

    public ProjectionStatistics(BigDecimal endingInvestableAssets,
                                BigDecimal lowestInvestableAssets) {

        this.endingInvestableAssets =
                Objects.requireNonNull(endingInvestableAssets);

        this.lowestInvestableAssets =
                Objects.requireNonNull(lowestInvestableAssets);
    }

    public BigDecimal getEndingInvestableAssets() {
        return endingInvestableAssets;
    }

    public BigDecimal getLowestInvestableAssets() {
        return lowestInvestableAssets;
    }
}