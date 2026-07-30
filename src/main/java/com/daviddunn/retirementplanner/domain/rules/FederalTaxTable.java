package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class FederalTaxTable {

    private final int taxYear;

    private final FilingStatus filingStatus;

    private final BigDecimal standardDeduction;

    private final List<FederalTaxBracket> brackets;

    @JsonCreator
    public FederalTaxTable(

            @JsonProperty("taxYear")
            int taxYear,

            @JsonProperty("filingStatus")
            FilingStatus filingStatus,

            @JsonProperty("standardDeduction")
            BigDecimal standardDeduction,

            @JsonProperty("brackets")
            List<FederalTaxBracket> brackets) {

        if (taxYear < 1900) {
            throw new IllegalArgumentException(
                    "Invalid tax year.");
        }

        this.taxYear = taxYear;

        this.filingStatus =
                Objects.requireNonNull(
                        filingStatus,
                        "Filing status is required.");

        this.standardDeduction =
                Objects.requireNonNull(
                        standardDeduction,
                        "Standard deduction is required.");

        this.brackets =
                List.copyOf(
                        Objects.requireNonNull(
                                brackets,
                                "Tax brackets are required."));

        if (brackets.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one tax bracket is required.");
        }
    }

    public int getTaxYear() {
        return taxYear;
    }

    public FilingStatus getFilingStatus() {
        return filingStatus;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public List<FederalTaxBracket> getBrackets() {
        return brackets;
    }

    @Override
    public String toString() {
        return "FederalTaxTable{" +
                "taxYear=" + taxYear +
                ", filingStatus=" + filingStatus +
                ", standardDeduction=" + standardDeduction +
                ", brackets=" + brackets +
                '}';
    }
}