package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ProjectedAssetGrowthCalculator {

    public ProjectedAssetPools applyGrowth(
            ProjectedAssetPools assetPools,
            BigDecimal annualReturn) {

        Objects.requireNonNull(
                assetPools,
                "Projected asset pools are required.");

        Objects.requireNonNull(
                annualReturn,
                "Annual return is required.");

        return new ProjectedAssetPools(
                applyGrowth(
                        assetPools.getTaxableBalance(),
                        annualReturn),
                applyGrowth(
                        assetPools.getTaxDeferredBalance(),
                        annualReturn),
                applyGrowth(
                        assetPools.getRothBalance(),
                        annualReturn));
    }

    private BigDecimal applyGrowth(
            BigDecimal balance,
            BigDecimal annualReturn) {

        return balance.multiply(
                        BigDecimal.ONE.add(
                                annualReturn))
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }
}