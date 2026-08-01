package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;

import java.math.BigDecimal;

public final class CombinedTaxCalculator {

    public CombinedTaxCalculation combine(
            FederalTaxCalculation federal,
            MichiganTaxCalculation michigan) {

        BigDecimal totalIncomeTax =
                federal.getFederalIncomeTax()
                        .add(
                                michigan.incomeTax());

        return new CombinedTaxCalculation(
                federal,
                michigan,
                totalIncomeTax);
    }
}