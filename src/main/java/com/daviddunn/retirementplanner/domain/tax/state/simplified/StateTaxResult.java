package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** No numeric estimate exists for UNSUPPORTED, including no fabricated zero. */
public record StateTaxResult(
        String jurisdiction, int taxYear, FilingStatus filingStatus,
        String datasetVersion, StateTaxCalculationStatus status, Optional<Estimate> estimate,
        List<String> warnings,
        List<StateTaxUnavailableReason> unavailableReasons) {

    public StateTaxResult(String jurisdiction, int taxYear, FilingStatus filingStatus, String datasetVersion,
            StateTaxCalculationStatus status,
            Optional<Estimate> estimate, List<String> warnings) {
        this(jurisdiction, taxYear, filingStatus, datasetVersion, status, estimate, warnings,
                status == StateTaxCalculationStatus.UNSUPPORTED
                        ? List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE)
                        : List.of());
    }

    public StateTaxResult {
        estimate = java.util.Objects.requireNonNull(estimate);
        warnings = List.copyOf(warnings);
        unavailableReasons = List.copyOf(unavailableReasons);
        java.util.Objects.requireNonNull(status);
        if ((status == StateTaxCalculationStatus.UNSUPPORTED) == estimate.isPresent()) {
            throw new IllegalArgumentException("Status and estimate disagree");
        }
        if ((status == StateTaxCalculationStatus.UNSUPPORTED)
                != !unavailableReasons.isEmpty()) {
            throw new IllegalArgumentException("Status and unavailable reasons disagree");
        }
        if (status == StateTaxCalculationStatus.UNSUPPORTED
                && (warnings.isEmpty() || warnings.stream().anyMatch(String::isBlank))) {
            throw new IllegalArgumentException("Unsupported estimates require an explanation.");
        }
    }

    /** The isolated prototype has one calculation mode; no override or plan setting is introduced. */
    public com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod calculationMethod() {
        return com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE;
    }

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
