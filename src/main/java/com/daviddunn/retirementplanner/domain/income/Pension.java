package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Pension extends IncomeSource {

    private final LocalDate commencementDate;
    private final BigDecimal monthlyBenefit;
    private final boolean cola;

    @JsonCreator
    public Pension(
            @JsonProperty("name") String name,
            @JsonProperty("commencementDate") LocalDate commencementDate,
            @JsonProperty("monthlyBenefit") BigDecimal monthlyBenefit,
            @JsonProperty("cola") boolean cola) {

        super(name);

        this.commencementDate = commencementDate;
        this.monthlyBenefit = monthlyBenefit;
        this.cola = cola;
    }

    public LocalDate getCommencementDate() {
        return commencementDate;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public boolean hasCola() {
        return cola;
    }

    @Override
    public BigDecimal getAnnualIncome() {
        return monthlyBenefit.multiply(BigDecimal.valueOf(12));
    }
}