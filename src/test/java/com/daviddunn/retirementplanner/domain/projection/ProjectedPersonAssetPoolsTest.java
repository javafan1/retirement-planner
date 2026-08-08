package com.daviddunn.retirementplanner.domain.projection;

import org.junit.jupiter.api.Test;

import static com.daviddunn.retirementplanner.domain.financial.TestMoney.money;
import static org.junit.jupiter.api.Assertions.*;

class ProjectedPersonAssetPoolsTest {

    @Test
    void shouldCalculateTotalBalance() {

        ProjectedPersonAssetPools pools =
                new ProjectedPersonAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        assertEquals(
                money("850000"),
                pools.getTotalBalance());
    }

    @Test
    void shouldConvertTaxDeferredToRoth() {

        ProjectedPersonAssetPools pools =
                new ProjectedPersonAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        ProjectedPersonAssetPools result =
                pools.convertTaxDeferredToRoth(
                        money("50000"));

        assertEquals(
                money("100000"),
                result.getTaxableBalance());

        assertEquals(
                money("450000"),
                result.getTaxDeferredBalance());

        assertEquals(
                money("300000"),
                result.getRothBalance());

        assertEquals(
                pools.getTotalBalance(),
                result.getTotalBalance());
    }

    @Test
    void shouldAllowZeroConversion() {

        ProjectedPersonAssetPools pools =
                new ProjectedPersonAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        ProjectedPersonAssetPools result =
                pools.convertTaxDeferredToRoth(
                        money("0"));

        assertEquals(pools, result);
    }

    @Test
    void shouldRejectNegativeConversion() {

        ProjectedPersonAssetPools pools =
                new ProjectedPersonAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> pools.convertTaxDeferredToRoth(
                                money("-1")));

        assertEquals(
                "Conversion amount cannot be negative.",
                exception.getMessage());
    }

    @Test
    void shouldRejectConversionGreaterThanAvailableBalance() {

        ProjectedPersonAssetPools pools =
                new ProjectedPersonAssetPools(
                        money("100000"),
                        money("500000"),
                        money("250000"));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> pools.convertTaxDeferredToRoth(
                                money("500001")));

        assertEquals(
                "Insufficient tax-deferred assets.",
                exception.getMessage());
    }

    @Test
    void shouldRejectNegativeTaxableBalance() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectedPersonAssetPools(
                        money("-1"),
                        money("0"),
                        money("0")));
    }

    @Test
    void shouldRejectNegativeTaxDeferredBalance() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectedPersonAssetPools(
                        money("0"),
                        money("-1"),
                        money("0")));
    }

    @Test
    void shouldRejectNegativeRothBalance() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectedPersonAssetPools(
                        money("0"),
                        money("0"),
                        money("-1")));
    }
}