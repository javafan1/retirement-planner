package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxBracketCalculator;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionTargetBracketResolver {

    private final FederalTaxBracketCalculator
            federalTaxBracketCalculator;

    public RothConversionTargetBracketResolver() {

        this.federalTaxBracketCalculator =
                new FederalTaxBracketCalculator();
    }

    public FederalTaxBracket resolve(
            RothConversionStrategy strategy,
            FederalTaxRules federalTaxRules) {

        Objects.requireNonNull(
                strategy,
                "Roth conversion strategy is required.");

        Objects.requireNonNull(
                federalTaxRules,
                "Federal tax rules are required.");

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
                };

        return federalTaxBracketCalculator.findBracket(
                federalTaxRules,
                targetRate);
    }
}