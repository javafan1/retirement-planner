package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class IrmaaRules {

    private final List<IrmaaBracket> brackets;

    @JsonCreator
    public IrmaaRules(

            @JsonProperty("brackets")
            List<IrmaaBracket> brackets) {

        this.brackets =
                List.copyOf(
                        Objects.requireNonNull(
                                brackets,
                                "IRMAA brackets are required."));

        if (this.brackets.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one IRMAA bracket is required.");
        }
    }

    public List<IrmaaBracket> getBrackets() {
        return brackets;
    }

    public IrmaaBracket getBracket(
            FilingStatus filingStatus,
            java.math.BigDecimal modifiedAdjustedGrossIncome) {

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                modifiedAdjustedGrossIncome,
                "Modified adjusted gross income is required.");

        return brackets.stream()
                .filter(bracket ->
                        bracket.getFilingStatus() == filingStatus)
                .filter(bracket ->
                        bracket.contains(
                                modifiedAdjustedGrossIncome))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No IRMAA bracket found for filing status "
                                        + filingStatus
                                        + " and MAGI "
                                        + modifiedAdjustedGrossIncome));
    }

    @Override
    public String toString() {

        return "IrmaaRules{" +
                "brackets=" + brackets +
                '}';
    }
}