package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class FederalTaxRules {

    private final TaxFilingStatus filingStatus;
    private final BigDecimal standardDeduction;
    private final List<FederalTaxBracket> taxBrackets;

    @JsonCreator
    public FederalTaxRules(

            @JsonProperty("filingStatus")
            TaxFilingStatus filingStatus,

            @JsonProperty("standardDeduction")
            BigDecimal standardDeduction,

            @JsonProperty("taxBrackets")
            List<FederalTaxBracket> taxBrackets) {

        this.filingStatus =
                Objects.requireNonNull(
                        filingStatus,
                        "Filing status is required.");

        this.standardDeduction =
                Objects.requireNonNull(
                        standardDeduction,
                        "Standard deduction is required.");

        this.taxBrackets =
                List.copyOf(
                        Objects.requireNonNull(
                                taxBrackets,
                                "Tax brackets are required."));

        if (standardDeduction.signum() < 0) {
            throw new IllegalArgumentException(
                    "Standard deduction cannot be negative.");
        }

        if (this.taxBrackets.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one tax bracket is required.");
        }
    }

    public TaxFilingStatus getFilingStatus() {
        return filingStatus;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public List<FederalTaxBracket> getTaxBrackets() {
        return taxBrackets;
    }

    @Override
    public String toString() {

        return "FederalTaxRules{" +
                "filingStatus=" +
                filingStatus +
                ", standardDeduction=" +
                standardDeduction +
                ", taxBrackets=" +
                taxBrackets +
                '}';
    }
}