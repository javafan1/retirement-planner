package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.projection.ProjectedAssetPools;
import org.junit.jupiter.api.Test;

import static com.daviddunn.retirementplanner.domain.financial.TestMoney.money;
import static org.junit.jupiter.api.Assertions.*;

class RothConversionExecutorTest {

    private final RothConversionExecutor executor =
            new RothConversionExecutor();

    @Test
    void shouldConvertAssets() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("250000"),
                        money("1000000"),
                        money("250000"));

        ProjectedAssetPools result =
                executor.execute(
                        assetPools,
                        money("50000"));

        assertEquals(
                money("250000"),
                result.getTaxableBalance());

        assertEquals(
                money("950000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("300000"),
                result.getRothBalance());
    }

    @Test
    void shouldAllowZeroConversion() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("500000"),
                        money("200000"));

        ProjectedAssetPools result =
                executor.execute(
                        assetPools,
                        money("0"));

        assertEquals(
                money("100000"),
                result.getTaxableBalance());

        assertEquals(
                money("500000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("200000"),
                result.getRothBalance());
    }

    @Test
    void shouldAllowFullConversion() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("50000"),
                        money("25000"));

        ProjectedAssetPools result =
                executor.execute(
                        assetPools,
                        money("50000"));

        assertEquals(
                money("100000"),
                result.getTaxableBalance());

        assertEquals(
                money("0"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("75000"),
                result.getRothBalance());
    }

    @Test
    void shouldRejectConversionGreaterThanAvailableBalance() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("50000"),
                        money("25000"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> executor.execute(
                                assetPools,
                                money("50001")));

        assertEquals(
                "Insufficient tax-deferred assets to perform Roth conversion.",
                exception.getMessage());
    }

    @Test
    void shouldRejectNegativeConversionAmount() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("50000"),
                        money("25000"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> executor.execute(
                                assetPools,
                                money("-1")));

        assertEquals(
                "Conversion amount cannot be negative.",
                exception.getMessage());
    }

    @Test
    void shouldRejectNullConversionAmount() {

        ProjectedAssetPools assetPools =
                new ProjectedAssetPools(
                        money("100000"),
                        money("50000"),
                        money("25000"));

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> executor.execute(
                                assetPools,
                                null));

        assertEquals(
                "Conversion amount is required.",
                exception.getMessage());
    }

    @Test
    void shouldRejectNullAssetPools() {

        NullPointerException exception =
                assertThrows(
                        NullPointerException.class,
                        () -> executor.execute(
                                null,
                                money("50000")));

        assertEquals(
                "Projected asset pools are required.",
                exception.getMessage());
    }
}