package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganIncomeTaxCalculator {

    public BigDecimal calculate(
            BigDecimal taxableIncome,
            MichiganTaxRules michiganTaxRules) {

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        Objects.requireNonNull(
                michiganTaxRules,
                "Michigan tax rules are required.");

        if (taxableIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return taxableIncome.multiply(
                michiganTaxRules.getIncomeTaxRate());
    }
}