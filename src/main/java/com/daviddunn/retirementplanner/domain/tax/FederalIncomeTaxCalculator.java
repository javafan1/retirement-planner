package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalIncomeTaxCalculator {

    public BigDecimal calculateTax(
            BigDecimal taxableIncome,
            FilingStatus filingStatus,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (taxableIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        FederalTaxRules taxRules =
                governmentRules.getFederalTaxRules(
                        filingStatus);

        BigDecimal totalTax =
                BigDecimal.ZERO;

        for (FederalTaxBracket bracket :
                taxRules.getTaxBrackets()) {

            if (taxableIncome.compareTo(
                    bracket.getLowerBound()) <= 0) {

                break;
            }

            BigDecimal taxableInBracket;

            if (bracket.hasUpperBound()) {

                BigDecimal upper =
                        taxableIncome.min(
                                bracket.getUpperBound());

                taxableInBracket =
                        upper.subtract(
                                bracket.getLowerBound());

            } else {

                taxableInBracket =
                        taxableIncome.subtract(
                                bracket.getLowerBound());
            }

            if (taxableInBracket.signum() > 0) {

                BigDecimal taxForBracket =
                        taxableInBracket.multiply(
                                bracket.getTaxRate());

                totalTax =
                        totalTax.add(
                                taxForBracket);
            }
        }

        return totalTax;
    }
}