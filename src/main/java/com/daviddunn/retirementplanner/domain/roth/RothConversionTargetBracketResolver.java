package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionTargetBracketResolver {

    public FederalTaxBracket resolve(
            RothConversionStrategy strategy,
            FederalTaxRules publishedFederalTaxRules,
            FederalTaxRules projectedFederalTaxRules) {

        Objects.requireNonNull(
                strategy,
                "Roth conversion strategy is required.");

        Objects.requireNonNull(
                publishedFederalTaxRules,
                "Published federal tax rules are required.");

        Objects.requireNonNull(
                projectedFederalTaxRules,
                "Projected federal tax rules are required.");

        BigDecimal targetRate =
                switch (strategy) {

                    case FILL_12_PERCENT_BRACKET ->
                            new BigDecimal("0.12");

                    case FILL_22_PERCENT_BRACKET ->
                            new BigDecimal("0.22");

                    case FILL_24_PERCENT_BRACKET ->
                            new BigDecimal("0.24");

                    case FIXED_AMOUNT ->
                            throw new IllegalArgumentException(
                                    "Fixed amount strategy does not have a target tax bracket.");

                    case CUSTOM_TAXABLE_INCOME_TARGET ->
                            throw new IllegalArgumentException(
                                    "Custom taxable-income target strategy does not have a target tax bracket.");
                };

        int bracketIndex = findBracketIndex(
                publishedFederalTaxRules,
                targetRate);

        if (bracketIndex >= projectedFederalTaxRules
                .getTaxBrackets()
                .size()) {

            throw new IllegalStateException(
                    "Projected federal tax rules do not contain the target bracket.");
        }

        return projectedFederalTaxRules
                .getTaxBrackets()
                .get(bracketIndex);
    }

    private int findBracketIndex(
            FederalTaxRules federalTaxRules,
            BigDecimal targetRate) {

        for (int index = 0;
             index < federalTaxRules.getTaxBrackets().size();
             index++) {

            if (federalTaxRules.getTaxBrackets().get(index)
                    .getTaxRate()
                    .compareTo(targetRate) == 0) {

                return index;
            }
        }

        throw new IllegalStateException(
                "No published federal tax bracket found for rate "
                        + targetRate);
    }
}
