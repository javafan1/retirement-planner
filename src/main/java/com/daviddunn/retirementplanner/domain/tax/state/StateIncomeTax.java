package com.daviddunn.retirementplanner.domain.tax.state;

import java.math.BigDecimal;
import java.util.Objects;

public class StateIncomeTax {

    private final BigDecimal taxableIncome;

    private final BigDecimal retirementDeduction;

    private final BigDecimal tax;

    public StateIncomeTax(
            BigDecimal taxableIncome,
            BigDecimal retirementDeduction,
            BigDecimal tax) {

        this.taxableIncome =
                Objects.requireNonNull(
                        taxableIncome);

        this.retirementDeduction =
                Objects.requireNonNull(
                        retirementDeduction);

        this.tax =
                Objects.requireNonNull(
                        tax);
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getRetirementDeduction() {
        return retirementDeduction;
    }

    public BigDecimal getTax() {
        return tax;
    }
}