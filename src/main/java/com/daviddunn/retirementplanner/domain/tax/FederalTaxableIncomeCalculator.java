package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxableIncomeCalculator {

    public BigDecimal calculateTaxableIncome(
            BigDecimal adjustedGrossIncome,
            FilingStatus filingStatus,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                adjustedGrossIncome,
                "Adjusted gross income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (adjustedGrossIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        FederalTaxRules taxRules =
                governmentRules.getFederalTaxRules(
                        filingStatus);

        BigDecimal taxableIncome =
                adjustedGrossIncome.subtract(
                        taxRules.getStandardDeduction());

        return taxableIncome.max(
                BigDecimal.ZERO);
    }
}