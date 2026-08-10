package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxBracketCalculator {

    public FederalTaxBracket findBracket(
            FederalTaxRules rules,
            BigDecimal taxRate) {

        Objects.requireNonNull(
                rules,
                "Federal tax rules are required.");

        Objects.requireNonNull(
                taxRate,
                "Tax rate is required.");

        return rules
                .getTaxBrackets()
                .stream()
                .filter(bracket ->
                        bracket
                                .getTaxRate()
                                .compareTo(taxRate) == 0)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No federal tax bracket found for rate "
                                        + taxRate));
    }
}