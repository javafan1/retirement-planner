package com.daviddunn.retirementplanner.domain.financial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public class Expense {

    private final String description;
    private final BigDecimal annualAmount;

    @JsonCreator
    public Expense(
            @JsonProperty("description") String description,
            @JsonProperty("annualAmount") BigDecimal annualAmount) {

        this.description = Objects.requireNonNull(description);
        this.annualAmount = Objects.requireNonNull(annualAmount);
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }
}