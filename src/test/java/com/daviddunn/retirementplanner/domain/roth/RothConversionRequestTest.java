package com.daviddunn.retirementplanner.domain.roth;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RothConversionRequestTest {

    @Test
    void storesConversionStrategy() {

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                        RothConversionFrequency.ANNUAL);

        assertEquals(
                RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                request.getStrategy());
    }
}