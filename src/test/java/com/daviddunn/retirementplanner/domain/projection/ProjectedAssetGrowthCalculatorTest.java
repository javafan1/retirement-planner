package com.daviddunn.retirementplanner.domain.projection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedAssetGrowthCalculatorTest {

    private final ProjectedAssetGrowthCalculator calculator =
            new ProjectedAssetGrowthCalculator();

    @Test
    void shouldReturnSameBalancesForZeroGrowth() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        new BigDecimal("100000"),
                        new BigDecimal("500000"),
                        new BigDecimal("250000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        BigDecimal.ZERO);

        assertEquals(
                new BigDecimal("100000.00"),
                result.getTaxableBalance());

        assertEquals(
                new BigDecimal("500000.00"),
                result.getTaxDeferredBalance());

        assertEquals(
                new BigDecimal("250000.00"),
                result.getRothBalance());

        assertEquals(
                new BigDecimal("850000.00"),
                result.getTotalBalance());
    }

    @Test
    void shouldApplyEightPercentGrowth() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        new BigDecimal("100000"),
                        new BigDecimal("500000"),
                        new BigDecimal("250000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        new BigDecimal("0.08"));

        assertEquals(
                new BigDecimal("108000.00"),
                result.getTaxableBalance());

        assertEquals(
                new BigDecimal("540000.00"),
                result.getTaxDeferredBalance());

        assertEquals(
                new BigDecimal("270000.00"),
                result.getRothBalance());

        assertEquals(
                new BigDecimal("918000.00"),
                result.getTotalBalance());
    }

    @Test
    void shouldHandleEmptyAssetPools() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        new BigDecimal("0.10"));

        assertEquals(
                new BigDecimal("0.00"),
                result.getTaxableBalance());

        assertEquals(
                new BigDecimal("0.00"),
                result.getTaxDeferredBalance());

        assertEquals(
                new BigDecimal("0.00"),
                result.getRothBalance());

        assertEquals(
                new BigDecimal("0.00"),
                result.getTotalBalance());
    }

    @Test
    void shouldApplyGrowthToMixedBalances() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        new BigDecimal("250000"),
                        new BigDecimal("1000000"),
                        new BigDecimal("500000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        new BigDecimal("0.05"));

        assertEquals(
                new BigDecimal("262500.00"),
                result.getTaxableBalance());

        assertEquals(
                new BigDecimal("1050000.00"),
                result.getTaxDeferredBalance());

        assertEquals(
                new BigDecimal("525000.00"),
                result.getRothBalance());

        assertEquals(
                new BigDecimal("1837500.00"),
                result.getTotalBalance());
    }
}