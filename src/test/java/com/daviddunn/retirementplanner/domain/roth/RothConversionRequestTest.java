package com.daviddunn.retirementplanner.domain.roth;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertNull(
                request.getCustomTargetTaxableIncome());
    }

    @Test
    void existingStrategiesDoNotRequireCustomTargetTaxableIncome() {

        List<RothConversionStrategy> strategies =
                List.of(
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionStrategy.FILL_12_PERCENT_BRACKET,
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                        RothConversionStrategy.FILL_24_PERCENT_BRACKET);

        for (RothConversionStrategy strategy : strategies) {

            RothConversionRequest request =
                    new RothConversionRequest(
                            true,
                            2026,
                            new BigDecimal("50000"),
                            RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                            strategy,
                            RothConversionFrequency.ANNUAL);

            assertEquals(
                    strategy,
                    request.getStrategy());

            assertNull(
                    request.getCustomTargetTaxableIncome());
        }
    }

    @Test
    void storesCustomTargetTaxableIncome() {

        BigDecimal target =
                new BigDecimal("180000");

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        BigDecimal.ZERO,
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .CUSTOM_TAXABLE_INCOME_TARGET,
                        RothConversionFrequency.ANNUAL,
                        target);

        assertEquals(
                RothConversionStrategy
                        .CUSTOM_TAXABLE_INCOME_TARGET,
                request.getStrategy());

        assertEquals(
                0,
                target.compareTo(
                        request
                                .getCustomTargetTaxableIncome()));
    }

    @Test
    void customStrategyRejectsMissingTargetTaxableIncome() {

        assertThrows(
                NullPointerException.class,
                () ->
                        new RothConversionRequest(
                                true,
                                2026,
                                BigDecimal.ZERO,
                                RothConversionStopRule
                                        .FIRST_HOUSEHOLD_RMD,
                                RothConversionStrategy
                                        .CUSTOM_TAXABLE_INCOME_TARGET,
                                RothConversionFrequency.ANNUAL,
                                null));
    }

    @Test
    void customStrategyRejectsNegativeTargetTaxableIncome() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RothConversionRequest(
                                true,
                                2026,
                                BigDecimal.ZERO,
                                RothConversionStopRule
                                        .FIRST_HOUSEHOLD_RMD,
                                RothConversionStrategy
                                        .CUSTOM_TAXABLE_INCOME_TARGET,
                                RothConversionFrequency.ANNUAL,
                                new BigDecimal("-1")));
    }
}
