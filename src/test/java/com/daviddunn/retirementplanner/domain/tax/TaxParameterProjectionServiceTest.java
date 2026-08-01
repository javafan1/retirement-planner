package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
                        planningAssumptions());

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
                        planningAssumptions());

        assertEquals(
                new BigDecimal("100000"),
                projectedValue);
    }

    @Test
    void oneYearProjectionUsesInflation() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2027,
                        planningAssumptions());

        assertEquals(
                new BigDecimal("102500"),
                projectedValue);
    }

    @Test
    void tenYearProjectionCompoundsInflation() {

        BigDecimal projectedValue =
                service.project(
                        new BigDecimal("100000"),
                        2026,
                        2036,
                        planningAssumptions());

        assertEquals(
                new BigDecimal("128008"),
                projectedValue);
    }

    private PlanningAssumptions planningAssumptions() {

        return new PlanningAssumptions(
                new BigDecimal("0.070"),
                new BigDecimal("0.025"),
                30,
                LocalDate.of(2026, 1, 1));
    }
}