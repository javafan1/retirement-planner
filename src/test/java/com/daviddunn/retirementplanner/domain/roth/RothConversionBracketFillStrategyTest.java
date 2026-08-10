package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RothConversionBracketFillStrategyTest {

    private final RothConversionBracketFillStrategy strategy =
            new RothConversionBracketFillStrategy();

    @Test
    void calculatesConversionNeededToFill22PercentBracket() {

        FederalTaxCalculation federalTaxCalculation =
                new FederalTaxCalculation(
                        new BigDecimal("180000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),
                        new BigDecimal("150000"),
                        new BigDecimal("18000"));

        FederalTaxBracket bracket =
                new FederalTaxBracket(
                        new BigDecimal("94300"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal conversion =
                strategy.calculateConversion(
                        federalTaxCalculation,
                        bracket);

        assertEquals(
                0,
                new BigDecimal("51050")
                        .compareTo(conversion));
    }

    @Test
    void returnsZeroWhenAlreadyAbove22PercentBracket() {

        FederalTaxCalculation federalTaxCalculation =
                new FederalTaxCalculation(
                        new BigDecimal("250000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),
                        new BigDecimal("220000"),
                        new BigDecimal("30000"));

        FederalTaxBracket bracket =
                new FederalTaxBracket(
                        new BigDecimal("94300"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal conversion =
                strategy.calculateConversion(
                        federalTaxCalculation,
                        bracket);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(conversion));
    }
}