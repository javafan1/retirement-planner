package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** No numeric estimate exists for UNSUPPORTED, including no fabricated zero. */
public record StateTaxResult(
        String jurisdiction, int taxYear, FilingStatus filingStatus,
        String datasetVersion, Status status, Optional<Estimate> estimate,
        List<String> warnings) {

    public StateTaxResult {
        estimate = java.util.Objects.requireNonNull(estimate);
        warnings = List.copyOf(warnings);
        if ((status == Status.UNSUPPORTED) == estimate.isPresent()) {
            throw new IllegalArgumentException("Status and estimate disagree");
        }
    }

    public enum Status { CALCULATED, PROVISIONAL, UNSUPPORTED }

    public record Line(String description, BigDecimal amount) { }

    public record Estimate(
            BigDecimal startingTaxBase, List<Line> incomeAdjustments,
            List<Line> retirementExclusions, List<Line> seniorAdjustments,
            BigDecimal standardDeduction, BigDecimal personalExemption,
            BigDecimal taxableIncome, BigDecimal taxBeforeCredits,
            BigDecimal credits, BigDecimal additionalTaxes,
            BigDecimal finalEstimatedTax) {
        public Estimate {
            incomeAdjustments = List.copyOf(incomeAdjustments);
            retirementExclusions = List.copyOf(retirementExclusions);
            seniorAdjustments = List.copyOf(seniorAdjustments);
        }
    }
}
