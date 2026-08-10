package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionBracketRoomCalculator {

    public BigDecimal calculateRoom(
            BigDecimal taxableIncome,
            FederalTaxBracket bracket) {

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        Objects.requireNonNull(
                bracket,
                "Federal tax bracket is required.");

        if (taxableIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Taxable income cannot be negative.");
        }

        /*
         * The highest federal bracket has no upper
         * bound. There is no finite amount of room
         * that can be calculated from the bracket
         * itself.
         */
        if (!bracket.hasUpperBound()) {
            throw new IllegalArgumentException(
                    "Federal tax bracket has no upper bound.");
        }

        BigDecimal room =
                bracket
                        .getUpperBound()
                        .subtract(taxableIncome);

        if (room.signum() < 0) {
            return BigDecimal.ZERO;
        }

        return room;
    }
}