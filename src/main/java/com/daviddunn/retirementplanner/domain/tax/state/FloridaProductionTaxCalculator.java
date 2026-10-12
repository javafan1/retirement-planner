package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Full-year residents: Florida imposes no individual income tax. Other Florida taxes are outside this service. */
final class FloridaProductionTaxCalculator implements StateIncomeTaxCalculator {
    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        return new ProductionStateTaxResult(StateTaxJurisdiction.FL, request.assumptions().calculationMethod(),
                ProductionStateTaxResult.Status.CALCULATED, BigDecimal.ZERO,
                List.of("Full-year Florida resident. Florida does not impose individual state income tax."), Optional.empty());
    }
}
