package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;

import java.math.BigDecimal;
import java.util.Objects;

public record TotalTaxCalculation(

        FederalTaxCalculation federalTaxCalculation,

        MichiganTaxCalculation michiganTaxCalculation,

        BigDecimal totalIncomeTax) {

    public TotalTaxCalculation {

        Objects.requireNonNull(
                federalTaxCalculation,
                "Federal tax calculation is required.");

        Objects.requireNonNull(
                michiganTaxCalculation,
                "Michigan tax calculation is required.");

        Objects.requireNonNull(
                totalIncomeTax,
                "Total income tax is required.");
    }
}