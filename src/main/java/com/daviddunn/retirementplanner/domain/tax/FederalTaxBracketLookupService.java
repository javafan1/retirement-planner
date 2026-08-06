package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxBracketLookupService {

    public FederalTaxBracket findBracket(
            FederalTaxRules federalTaxRules,
            BigDecimal taxRate) {

        Objects.requireNonNull(
                federalTaxRules,
                "Federal tax rules are required.");

        Objects.requireNonNull(
                taxRate,
                "Tax rate is required.");

        for (FederalTaxBracket bracket :
                federalTaxRules.getTaxBrackets()) {

            if (bracket.getTaxRate()
                    .compareTo(taxRate) == 0) {

                return bracket;
            }
        }

        throw new IllegalArgumentException(
                "Federal tax bracket not found for tax rate "
                        + taxRate + ".");
    }

    public BigDecimal calculateRemainingCapacity(
            FederalTaxRules federalTaxRules,
            BigDecimal taxableIncome,
            BigDecimal targetTaxRate) {

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        FederalTaxBracket targetBracket =
                findBracket(
                        federalTaxRules,
                        targetTaxRate);

        if (!targetBracket.hasUpperBound()) {

            throw new IllegalArgumentException(
                    "Cannot calculate remaining capacity for the highest tax bracket.");
        }

        BigDecimal remainingCapacity =
                targetBracket.getUpperBound()
                        .subtract(taxableIncome);

        if (remainingCapacity.signum() < 0) {

            return BigDecimal.ZERO;
        }

        return remainingCapacity;
    }

//    public FederalTaxBracket findBracketForIncome(
//            FederalTaxRules federalTaxRules,
//            BigDecimal taxableIncome) {
//
//        Objects.requireNonNull(
//                federalTaxRules,
//                "Federal tax rules are required.");
//
//        Objects.requireNonNull(
//                taxableIncome,
//                "Taxable income is required.");
//
//        for (FederalTaxBracket bracket :
//                federalTaxRules.getTaxBrackets()) {
//
//            /*
//             * Highest bracket.
//             */
//            if (!bracket.hasUpperBound()) {
//
//                if (taxableIncome.compareTo(
//                        bracket.getLowerBound()) > 0) {
//
//                    return bracket;
//                }
//
//                continue;
//            }
//
//            /*
//             * First bracket.
//             */
//            if (bracket.getLowerBound()
//                    .compareTo(BigDecimal.ZERO) == 0) {
//
//                if (taxableIncome.compareTo(
//                        bracket.getUpperBound()) <= 0) {
//
//                    return bracket;
//                }
//
//                continue;
//            }
//
//            /*
//             * Remaining brackets:
//             *
//             * lower bound is exclusive
//             * upper bound is inclusive
//             */
//            if (taxableIncome.compareTo(
//                    bracket.getLowerBound()) > 0
//                    &&
//                    taxableIncome.compareTo(
//                            bracket.getUpperBound()) <= 0) {
//
//                return bracket;
//            }
//        }
//
//        throw new IllegalStateException(
//                "Unable to determine federal tax bracket.");
//    }

}