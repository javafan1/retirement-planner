package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.tax.TaxIncome;

public interface StateIncomeTaxCalculator {

    StateIncomeTax calculate(
            TaxIncome taxIncome);
}