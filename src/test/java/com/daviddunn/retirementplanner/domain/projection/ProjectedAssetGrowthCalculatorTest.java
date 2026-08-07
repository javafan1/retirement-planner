package com.daviddunn.retirementplanner.domain.projection;

import org.junit.jupiter.api.Test;

import static com.daviddunn.retirementplanner.domain.financial.TestMoney.money;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedAssetGrowthCalculatorTest {

    private final ProjectedAssetGrowthCalculator calculator =
            new ProjectedAssetGrowthCalculator();

    @Test
    void shouldReturnSameBalancesForZeroGrowth() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        money("0"));

        assertEquals(
                money("100000"),
                result.getTaxableBalance());

        assertEquals(
                money("500000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("250000"),
                result.getRothBalance());

        assertEquals(
                money("850000"),
                result.getTotalBalance());
    }

    @Test
    void shouldApplyEightPercentGrowth() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        money("0.08"));

        assertEquals(
                money("108000"),
                result.getTaxableBalance());

        assertEquals(
                money("540000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("270000"),
                result.getRothBalance());

        assertEquals(
                money("918000"),
                result.getTotalBalance());
    }

    @Test
    void shouldHandleEmptyAssetPools() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("0"),
                        money("0"),
                        money("0"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        money("0.10"));

        assertEquals(
                money("0"),
                result.getTaxableBalance());

        assertEquals(
                money("0"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("0"),
                result.getRothBalance());

        assertEquals(
                money("0"),
                result.getTotalBalance());
    }

    @Test
    void shouldApplyGrowthToMixedBalances() {

        ProjectedAssetPools pools =
                new ProjectedAssetPools(
                        money("250000"),
                        money("1000000"),
                        money("500000"));

        ProjectedAssetPools result =
                calculator.applyGrowth(
                        pools,
                        money("0.05"));

        assertEquals(
                money("262500"),
                result.getTaxableBalance());

        assertEquals(
                money("1050000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("525000"),
                result.getRothBalance());

        assertEquals(
                money("1837500"),
                result.getTotalBalance());
    }
}