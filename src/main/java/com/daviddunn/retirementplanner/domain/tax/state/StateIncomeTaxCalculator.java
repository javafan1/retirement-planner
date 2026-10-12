package com.daviddunn.retirementplanner.domain.tax.state;

public interface StateIncomeTaxCalculator {
    ProductionStateTaxResult calculate(StateTaxCalculationRequest request);
}
