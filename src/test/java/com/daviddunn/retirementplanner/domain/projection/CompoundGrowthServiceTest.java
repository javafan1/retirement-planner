package com.daviddunn.retirementplanner.domain.projection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CompoundGrowthServiceTest {

    private final CompoundGrowthService service =
            new CompoundGrowthService();

    @Test
    void shouldReturnOriginalValueWhenYearsIsZero() {

        BigDecimal result =
                service.project(
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(0.05),
                        0);

        assertEquals(
                0,
                result.compareTo(BigDecimal.valueOf(1000)));
    }

    @Test
    void shouldApplySingleYearGrowth() {

        BigDecimal result =
                service.project(
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(0.10),
                        1);

        assertEquals(
                0,
                result.compareTo(BigDecimal.valueOf(1100)));
    }

    @Test
    void shouldCompoundMultipleYears() {

        BigDecimal result =
                service.project(
                                BigDecimal.valueOf(1000),
                                BigDecimal.valueOf(0.05),
                                10)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        assertEquals(
                BigDecimal.valueOf(1628.89)
                        .setScale(2),
                result);
    }

    @Test
    void shouldSupportZeroGrowthRate() {

        BigDecimal result =
                service.project(
                        BigDecimal.valueOf(5000),
                        BigDecimal.ZERO,
                        20);

        assertEquals(
                0,
                result.compareTo(BigDecimal.valueOf(5000)));
    }

    @Test
    void shouldRejectNegativeYears() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.project(
                        BigDecimal.TEN,
                        BigDecimal.valueOf(0.05),
                        -1));
    }

    @Test
    void shouldRejectNullStartingValue() {

        assertThrows(
                NullPointerException.class,
                () -> service.project(
                        null,
                        BigDecimal.valueOf(0.05),
                        10));
    }

    @Test
    void shouldRejectNullGrowthRate() {

        assertThrows(
                NullPointerException.class,
                () -> service.project(
                        BigDecimal.TEN,
                        null,
                        10));
    }
}