package com.daviddunn.retirementplanner.domain.baseline;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProjectionComparisonTest {

    @Test
    void calculatesChangesAsCurrentMinusBaseline() {

        ProjectionComparison comparison =
                new ProjectionComparison(
                        2040,

                        new BigDecimal("5000000"),
                        new BigDecimal("5300000"),

                        new BigDecimal("1000000"),
                        new BigDecimal("1100000"),

                        new BigDecimal("6000000"),
                        new BigDecimal("6400000"),

                        new BigDecimal("5500000"),
                        new BigDecimal("5800000"),

                        new BigDecimal("0.18"),
                        new BigDecimal("0.17"),

                        new BigDecimal("6500000"),
                        new BigDecimal("6800000"));

        assertEquals(
                new BigDecimal("300000"),
                comparison
                        .getEndingInvestableAssetsChange());

        assertEquals(
                new BigDecimal("100000"),
                comparison
                        .getNonInvestableAssetsChange());

        assertEquals(
                new BigDecimal("400000"),
                comparison
                        .getNetWorthChange());

        assertEquals(
                new BigDecimal("300000"),
                comparison
                        .getAfterTaxEstateChange());

        assertEquals(
                new BigDecimal("-0.01"),
                comparison
                        .getEffectiveTaxRateChange());

        assertEquals(
                new BigDecimal("300000"),
                comparison
                        .getPeakInvestableAssetsChange());
    }
}
