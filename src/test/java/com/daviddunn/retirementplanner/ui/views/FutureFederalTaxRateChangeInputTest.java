package com.daviddunn.retirementplanner.ui.views;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FutureFederalTaxRateChangeInputTest {

    @Test
    void bothBlankMeansNoScheduledChange() {

        FutureFederalTaxRateChangeInput input =
                FutureFederalTaxRateChangeInput.parse(
                        "",
                        "  ");

        assertNull(input.adjustment());
        assertNull(input.effectiveYear());
    }

    @Test
    void parsesPopulatedPair() {

        FutureFederalTaxRateChangeInput input =
                FutureFederalTaxRateChangeInput.parse(
                        "5%",
                        "2030");

        assertEquals(
                new BigDecimal("0.05000000"),
                input.adjustment());
        assertEquals(2030, input.effectiveYear());
    }

    @Test
    void preservesEnteredZeroAsConfiguredPair() {

        FutureFederalTaxRateChangeInput input =
                FutureFederalTaxRateChangeInput.parse(
                        "0",
                        "2030");

        assertEquals(
                new BigDecimal("0E-8"),
                input.adjustment());
        assertEquals(2030, input.effectiveYear());
    }

    @Test
    void rejectsEitherPartialPair() {

        assertThrows(
                IllegalArgumentException.class,
                () -> FutureFederalTaxRateChangeInput.parse(
                        "5",
                        ""));

        assertThrows(
                IllegalArgumentException.class,
                () -> FutureFederalTaxRateChangeInput.parse(
                        "",
                        "2030"));
    }
}
