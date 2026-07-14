package com.daviddunn.retirementplanner.domain.financial;

import java.math.BigDecimal;
import java.util.Objects;

public class Expense {

    private final String description;
    private final BigDecimal annualAmount;

    public Expense(String description,
                   BigDecimal annualAmount) {

        this.description =
                Objects.requireNonNull(description);

        this.annualAmount =
                Objects.requireNonNull(annualAmount);
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }
}