package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganRetirementDeductionCalculator {

    public BigDecimal calculate(
            BigDecimal retirementIncome,
            FilingStatus filingStatus,
            MichiganTaxRules projectedMichiganTaxRules) {

        Objects.requireNonNull(
                retirementIncome,
                "Retirement income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                projectedMichiganTaxRules,
                "Michigan tax rules are required.");

        BigDecimal deductionLimit =
                determineDeductionLimit(
                        filingStatus,
                        projectedMichiganTaxRules);

        return retirementIncome.min(
                deductionLimit);
    }

    private BigDecimal determineDeductionLimit(
            FilingStatus filingStatus,
            MichiganTaxRules michiganTaxRules) {

        return switch (filingStatus) {

            case SINGLE ->
                    michiganTaxRules
                            .getRetirementDeductionSingle();

            case MARRIED_FILING_JOINTLY ->
                    michiganTaxRules
                            .getRetirementDeductionMarried();

            default ->
                    throw new UnsupportedOperationException(
                            "Michigan retirement deduction is not implemented for filing status: "
                                    + filingStatus);
        };
    }
}