package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import java.math.BigDecimal;
import java.util.Objects;

public record MichiganTaxCalculation(

        BigDecimal retirementIncome,

        BigDecimal retirementDeduction,

        BigDecimal taxableIncome,

        BigDecimal incomeTax) {

    public MichiganTaxCalculation {

        Objects.requireNonNull(
                retirementIncome,
                "Retirement income is required.");

        Objects.requireNonNull(
                retirementDeduction,
                "Retirement deduction is required.");

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        Objects.requireNonNull(
                incomeTax,
                "Income tax is required.");
    }
}