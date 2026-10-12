package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import java.util.Map;
import java.util.Objects;

/** The single production routing boundary; the eight-state research catalog is not an activation registry. */
public final class ProductionStateTaxService {
    private final Map<StateTaxJurisdiction, StateIncomeTaxCalculator> calculators = Map.of(
            StateTaxJurisdiction.MI, new MichiganProductionTaxAdapter(),
            StateTaxJurisdiction.FL, new FloridaProductionTaxCalculator());

    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().calculationMethod() != CalculationMethod.SIMPLIFIED_STATE_ESTIMATE) {
            throw new IllegalArgumentException("Unsupported production state tax calculation method.");
        }
        var calculator = calculators.get(request.assumptions().jurisdiction());
        if (calculator == null) {
            throw new IllegalArgumentException("Unsupported production state tax jurisdiction.");
        }
        return calculator.calculate(request);
    }
}
