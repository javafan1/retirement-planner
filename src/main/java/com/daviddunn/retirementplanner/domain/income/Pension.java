package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.PersonRole;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Pension extends IncomeSource {

    private final BigDecimal monthlyBenefit;

    private final boolean cola;

    @JsonCreator
    public Pension(

            @JsonProperty("name")
            String name,

            @JsonProperty("owner")
            PersonRole owner,

            @JsonProperty("startDate")
            LocalDate commencementDate,

            @JsonProperty("endDate")
            LocalDate terminationDate,

            @JsonProperty("monthlyBenefit")
            BigDecimal monthlyBenefit,

            @JsonProperty("cola")
            boolean cola) {

        super(
                name,
                owner,
                commencementDate,
                terminationDate);

        this.monthlyBenefit = monthlyBenefit;
        this.cola = cola;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public boolean hasCola() {
        return cola;
    }

    @Override
    public BigDecimal getAnnualIncome(LocalDate projectionDate) {

        if (!isActive(projectionDate)) {
            return BigDecimal.ZERO;
        }

        // COLA support will be added later.
        return monthlyBenefit.multiply(BigDecimal.valueOf(12));
    }
}