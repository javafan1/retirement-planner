package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RothConversionBracketRoomCalculatorTest {

    private final RothConversionBracketRoomCalculator calculator =
            new RothConversionBracketRoomCalculator();

    @Test
    void calculatesRemainingRoomBelowBracketCeiling() {

        FederalTaxBracket bracket =
                new FederalTaxBracket(
                        new BigDecimal("94300"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal room =
                calculator.calculateRoom(
                        new BigDecimal("150000"),
                        bracket);

        assertEquals(
                0,
                new BigDecimal("51050")
                        .compareTo(room));
    }

    @Test
    void returnsZeroWhenIncomeIsAtBracketCeiling() {

        FederalTaxBracket bracket =
                new FederalTaxBracket(
                        new BigDecimal("94300"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal room =
                calculator.calculateRoom(
                        new BigDecimal("201050"),
                        bracket);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(room));
    }

    @Test
    void returnsZeroWhenIncomeIsAboveBracketCeiling() {

        FederalTaxBracket bracket =
                new FederalTaxBracket(
                        new BigDecimal("94300"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal room =
                calculator.calculateRoom(
                        new BigDecimal("220000"),
                        bracket);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(room));
    }

    @Test
    void calculatesRoomUsingFederalTaxableIncome() {

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

        BigDecimal room =
                calculator.calculateRoom(
                        federalTaxCalculation.getTaxableIncome(),
                        bracket);

        assertEquals(
                0,
                new BigDecimal("51050")
                        .compareTo(room));
    }

    @Test
    void returnsZeroRoomWhenFederalTaxableIncomeIsAbove22PercentBracket() {

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

        BigDecimal room =
                calculator.calculateRoom(
                        federalTaxCalculation.getTaxableIncome(),
                        bracket);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(room));
    }
}