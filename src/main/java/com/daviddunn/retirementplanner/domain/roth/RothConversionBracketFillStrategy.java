package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionBracketFillStrategy {

    private final RothConversionBracketRoomCalculator
            bracketRoomCalculator;

    public RothConversionBracketFillStrategy() {

        this.bracketRoomCalculator =
                new RothConversionBracketRoomCalculator();
    }

    public BigDecimal calculateConversion(
            FederalTaxCalculation federalTaxCalculation,
            FederalTaxBracket targetBracket) {

        Objects.requireNonNull(
                federalTaxCalculation,
                "Federal tax calculation is required.");

        Objects.requireNonNull(
                targetBracket,
                "Target federal tax bracket is required.");

        return bracketRoomCalculator.calculateRoom(
                federalTaxCalculation.getTaxableIncome(),
                targetBracket);
    }
}