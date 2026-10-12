package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Calculated production estimate; Michigan diagnostics are retained without fabricating them for Florida. */
public record ProductionStateTaxResult(
        StateTaxJurisdiction jurisdiction,
        CalculationMethod calculationMethod,
        Status calculationStatus,
        BigDecimal incomeTax,
        List<String> limitations,
        Optional<MichiganTaxCalculation> michiganDetails) {
    public enum Status { CALCULATED }

    public ProductionStateTaxResult {
        Objects.requireNonNull(jurisdiction);
        Objects.requireNonNull(calculationMethod);
        Objects.requireNonNull(calculationStatus);
        Objects.requireNonNull(incomeTax);
        limitations = List.copyOf(limitations);
        Objects.requireNonNull(michiganDetails);
        if (incomeTax.signum() < 0 || (jurisdiction != StateTaxJurisdiction.MI && michiganDetails.isPresent())) {
            throw new IllegalArgumentException("Invalid state tax result.");
        }
    }

    public static ProductionStateTaxResult michigan(MichiganTaxCalculation calculation) {
        return new ProductionStateTaxResult(StateTaxJurisdiction.MI, CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                Status.CALCULATED, calculation.incomeTax(), calculation.limitations(), Optional.of(calculation));
    }
}
