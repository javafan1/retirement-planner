package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;

/** Full-year residents: Florida imposes no individual income tax. Other Florida taxes are outside this service. */
final class FloridaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final NoGeneralIndividualIncomeTaxCalculator calculator = new NoGeneralIndividualIncomeTaxCalculator();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.FL) {
            throw new IllegalArgumentException("Florida adapter requires Florida jurisdiction.");
        }
        return calculator.calculate(request);
    }
}
