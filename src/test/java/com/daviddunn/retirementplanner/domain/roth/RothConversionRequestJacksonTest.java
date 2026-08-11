package com.daviddunn.retirementplanner.domain.roth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RothConversionRequestJacksonTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        objectMapper =
                new ObjectMapper();
    }

    @Test
    void fixedAmountRequestRoundTripsThroughJackson()
            throws Exception {

        RothConversionRequest original =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .FIXED_AMOUNT,
                        RothConversionFrequency
                                .ANNUAL);

        String json =
                objectMapper.writeValueAsString(
                        original);

        RothConversionRequest restored =
                objectMapper.readValue(
                        json,
                        RothConversionRequest.class);

        assertTrue(
                restored.isEnabled());

        assertEquals(
                2026,
                restored.getStartYear());

        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                restored.getAnnualAmount()));

        assertEquals(
                RothConversionStopRule
                        .FIRST_HOUSEHOLD_RMD,
                restored.getStopRule());

        assertEquals(
                RothConversionStrategy
                        .FIXED_AMOUNT,
                restored.getStrategy());

        assertEquals(
                RothConversionFrequency
                        .ANNUAL,
                restored.getFrequency());
    }


    @Test
    void fill22PercentBracketRequestRoundTripsThroughJackson()
            throws Exception {

        RothConversionRequest original =
                new RothConversionRequest(
                        true,
                        2026,
                        BigDecimal.ZERO,
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .FILL_22_PERCENT_BRACKET,
                        RothConversionFrequency
                                .ANNUAL);

        String json =
                objectMapper.writeValueAsString(
                        original);

        RothConversionRequest restored =
                objectMapper.readValue(
                        json,
                        RothConversionRequest.class);

        assertTrue(
                restored.isEnabled());

        assertEquals(
                2026,
                restored.getStartYear());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        restored.getAnnualAmount()));

        assertEquals(
                RothConversionStopRule
                        .FIRST_HOUSEHOLD_RMD,
                restored.getStopRule());

        assertEquals(
                RothConversionStrategy
                        .FILL_22_PERCENT_BRACKET,
                restored.getStrategy());

        assertEquals(
                RothConversionFrequency
                        .ANNUAL,
                restored.getFrequency());
    }
}