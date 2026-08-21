package com.daviddunn.retirementplanner.domain.noninvestable;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NonInvestableAssetProjectionServiceTest {

    @Test
    void projectsAssetValueUsingAnnualGrowthRate() {

        NonInvestableAsset asset =
                new NonInvestableAsset(
                        "Primary Residence",
                        new BigDecimal("800000"),
                        new BigDecimal("0.03"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        BigDecimal projectedValue =
                service.projectValue(
                        asset,
                        10);

        assertEquals(
                new BigDecimal("1075133.10"),
                projectedValue);
    }

    @Test
    void returnsCurrentValueWhenProjectingZeroYears() {

        NonInvestableAsset asset =
                new NonInvestableAsset(
                        "Comic Collection",
                        new BigDecimal("100000"),
                        new BigDecimal("0.04"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        BigDecimal projectedValue =
                service.projectValue(
                        asset,
                        0);

        assertEquals(
                new BigDecimal("100000.00"),
                projectedValue);
    }

    @Test
    void supportsNegativeGrowthRates() {

        NonInvestableAsset vehicle =
                new NonInvestableAsset(
                        "Vehicle",
                        new BigDecimal("50000"),
                        new BigDecimal("-0.05"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        BigDecimal projectedValue =
                service.projectValue(
                        vehicle,
                        5);

        assertEquals(
                new BigDecimal("38689.05"),
                projectedValue);
    }

    @Test
    void rejectsNegativeYears() {

        NonInvestableAsset asset =
                new NonInvestableAsset(
                        "Home",
                        new BigDecimal("800000"),
                        new BigDecimal("0.03"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.projectValue(
                                asset,
                                -1));
    }

    @Test
    void rejectsNullAsset() {

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        assertThrows(
                NullPointerException.class,
                () ->
                        service.projectValue(
                                null,
                                10));
    }


    @Test
    void projectsMultipleAssetsAcrossMultipleYears() {

        NonInvestableAsset home =
                new NonInvestableAsset(
                        "Primary Residence",
                        new BigDecimal("800000"),
                        new BigDecimal("0.03"));

        NonInvestableAsset comics =
                new NonInvestableAsset(
                        "Comic Collection",
                        new BigDecimal("100000"),
                        new BigDecimal("0.04"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        var projections =
                service.project(
                        List.of(home, comics),
                        2026,
                        2028);

        assertEquals(
                3,
                projections.size());

        /*
         * 2026
         */
        var projection2026 =
                projections.get(0);

        assertEquals(
                2026,
                projection2026.getCalendarYear());

        assertEquals(
                new BigDecimal("900000.00"),
                projection2026.getTotalValue());

        /*
         * 2027
         */
        var projection2027 =
                projections.get(1);

        assertEquals(
                2027,
                projection2027.getCalendarYear());

        assertEquals(
                new BigDecimal("928000.00"),
                projection2027.getTotalValue());

        /*
         * 2028
         */
        var projection2028 =
                projections.get(2);

        assertEquals(
                2028,
                projection2028.getCalendarYear());

        assertEquals(
                new BigDecimal("956880.00"),
                projection2028.getTotalValue());
    }

    @Test
    void preservesIndividualAssetValuesInProjection() {

        NonInvestableAsset home =
                new NonInvestableAsset(
                        "Primary Residence",
                        new BigDecimal("800000"),
                        new BigDecimal("0.03"));

        NonInvestableAsset comics =
                new NonInvestableAsset(
                        "Comic Collection",
                        new BigDecimal("100000"),
                        new BigDecimal("0.04"));

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        var projections =
                service.project(
                        List.of(home, comics),
                        2026,
                        2027);

        var projection2027 =
                projections.get(1);

        assertEquals(
                new BigDecimal("824000.00"),
                projection2027
                        .getAssetValues()
                        .get(0)
                        .getProjectedValue());

        assertEquals(
                new BigDecimal("104000.00"),
                projection2027
                        .getAssetValues()
                        .get(1)
                        .getProjectedValue());
    }

    @Test
    void rejectsInvalidProjectionRange() {

        NonInvestableAssetProjectionService service =
                new NonInvestableAssetProjectionService();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.project(
                                List.of(),
                                2030,
                                2029));
    }

}