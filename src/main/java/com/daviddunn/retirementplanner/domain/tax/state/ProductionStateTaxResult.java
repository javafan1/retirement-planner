package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Unsupported means no estimate; calculated zero is a valid, distinct result. */
public record ProductionStateTaxResult(
        StateTaxJurisdiction jurisdiction,
        CalculationMethod calculationMethod,
        StateTaxCalculationStatus calculationStatus,
        Optional<BigDecimal> estimatedIncomeTax,
        List<String> limitations,
        Optional<MichiganTaxCalculation> michiganDetails,
        List<StateTaxUnavailableReason> unavailableReasons) {

    /** Compatibility constructor for existing calculated production adapters. */
    public ProductionStateTaxResult(StateTaxJurisdiction jurisdiction, CalculationMethod calculationMethod,
            StateTaxCalculationStatus calculationStatus, BigDecimal incomeTax, List<String> limitations,
            Optional<MichiganTaxCalculation> michiganDetails) {
        this(jurisdiction, calculationMethod, calculationStatus, Optional.of(incomeTax),
                limitations, michiganDetails, List.of());
    }

    public ProductionStateTaxResult {
        Objects.requireNonNull(jurisdiction);
        Objects.requireNonNull(calculationMethod);
        Objects.requireNonNull(calculationStatus);
        Objects.requireNonNull(estimatedIncomeTax);
        limitations = List.copyOf(limitations);
        unavailableReasons = List.copyOf(unavailableReasons);
        Objects.requireNonNull(michiganDetails);
        boolean unsupported = calculationStatus == StateTaxCalculationStatus.UNSUPPORTED;
        if (calculationStatus == StateTaxCalculationStatus.PROVISIONAL
                || unsupported == estimatedIncomeTax.isPresent()
                || unsupported != !unavailableReasons.isEmpty()
                || (unsupported && (limitations.isEmpty() || limitations.stream().anyMatch(String::isBlank)
                        || michiganDetails.isPresent()))
                || estimatedIncomeTax.filter(amount -> amount.signum() < 0).isPresent()
                || (jurisdiction != StateTaxJurisdiction.MI && michiganDetails.isPresent())) {
            throw new IllegalArgumentException("Invalid state tax result.");
        }
    }

    public static ProductionStateTaxResult unsupported(StateTaxJurisdiction jurisdiction,
            CalculationMethod method, List<StateTaxUnavailableReason> reasons, String explanation) {
        return new ProductionStateTaxResult(jurisdiction, method, StateTaxCalculationStatus.UNSUPPORTED,
                Optional.empty(), List.of(explanation), Optional.empty(), reasons);
    }

    /** Numeric consumers must first admit a calculated result. Never defaults to zero. */
    public BigDecimal incomeTax() {
        return estimatedIncomeTax.orElseThrow(() -> new IllegalStateException("State income tax is unavailable."));
    }

    public static ProductionStateTaxResult michigan(MichiganTaxCalculation calculation) {
        return new ProductionStateTaxResult(StateTaxJurisdiction.MI, CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                StateTaxCalculationStatus.CALCULATED, calculation.incomeTax(), calculation.limitations(), Optional.of(calculation));
    }
}
