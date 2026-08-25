package com.daviddunn.retirementplanner.domain.tax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxParameterProjectionServiceTest {

    private TaxParameterProjectionService service;

    @BeforeEach
    void setUp() {

        service =
                new TaxParameterProjectionService();
    }

    @Test
    void publishedYearReturnsPublishedValue() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2026,
                        new BigDecimal("0.025"));

        assertEquals(
                new BigDecimal("100000"),
                projectedValue);
    }

    @Test
    void priorYearReturnsPublishedValue() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2025,
                        new BigDecimal("0.025"));

        assertEquals(
                new BigDecimal("100000"),
                projectedValue);
    }

    @Test
    void oneYearProjectionUsesSuppliedGrowthRate() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2027,
                        new BigDecimal("0.025"));

        assertEquals(
                new BigDecimal("102500"),
                projectedValue);
    }

    @Test
    void tenYearProjectionCompoundsSuppliedGrowthRate() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2036,
                        new BigDecimal("0.025"));

        assertEquals(
                new BigDecimal("128008"),
                projectedValue);
    }

}
