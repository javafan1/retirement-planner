package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
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

        validateCoverage();
    }

    private void validateCoverage() {

        Map<FilingStatus, IrmaaBracket> previousByStatus =
                new EnumMap<>(FilingStatus.class);

        for (IrmaaBracket bracket : brackets) {
            FilingStatus status = bracket.getFilingStatus();
            IrmaaBracket previous = previousByStatus.get(status);

            if (previous == null) {
                if (bracket.getMinimumModifiedAdjustedGrossIncome().signum() != 0
                        || !bracket.isMinimumIncomeInclusive()) {
                    throw invalidCoverage(status, "coverage must start at inclusive zero");
                }
            } else {
                if (previous.getMaximumModifiedAdjustedGrossIncome() == null) {
                    throw invalidCoverage(status, "only the final tier may be unbounded");
                }
                if (bracket.getMinimumModifiedAdjustedGrossIncome().compareTo(
                        previous.getMinimumModifiedAdjustedGrossIncome()) <= 0) {
                    throw invalidCoverage(status, "tiers must be ordered by increasing minimum income");
                }

                int boundaryComparison = bracket.getMinimumModifiedAdjustedGrossIncome().compareTo(
                        previous.getMaximumModifiedAdjustedGrossIncome());
                if (boundaryComparison > 0) {
                    throw invalidCoverage(status, "gap between adjacent thresholds");
                }
                if (boundaryComparison < 0) {
                    throw invalidCoverage(status, "overlap between adjacent tiers");
                }
                if (previous.isMaximumIncomeInclusive() == bracket.isMinimumIncomeInclusive()) {
                    throw invalidCoverage(status, "each shared boundary must belong to exactly one tier");
                }
            }

            previousByStatus.put(status, bracket);
        }

        for (var entry : previousByStatus.entrySet()) {
            if (entry.getValue().getMaximumModifiedAdjustedGrossIncome() != null) {
                throw invalidCoverage(entry.getKey(), "final tier must be unbounded");
            }
        }
    }

    private static IllegalArgumentException invalidCoverage(FilingStatus status, String reason) {
        return new IllegalArgumentException("Invalid IRMAA rules for " + status + ": " + reason + ".");
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
