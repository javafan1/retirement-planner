
package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class Pension extends IncomeSource {

    private final BigDecimal monthlyBenefit;

    private final BigDecimal annualColaRate;

    @JsonCreator
    public Pension(

            @JsonProperty("name")
            String name,

            @JsonProperty("ownership")
            AccountOwnership ownership,

            @JsonProperty("startDate")
            LocalDate commencementDate,

            @JsonProperty("endDate")
            LocalDate terminationDate,

            @JsonProperty("monthlyBenefit")
            BigDecimal monthlyBenefit,

            @JsonProperty("annualColaRate")
            BigDecimal annualColaRate) {

        super(
                name,
                ownership,
                commencementDate,
                terminationDate);

        this.monthlyBenefit =
                Objects.requireNonNull(monthlyBenefit);

        this.annualColaRate =
                annualColaRate != null
                        ? annualColaRate
                        : BigDecimal.ZERO;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public BigDecimal getAnnualColaRate() {
        return annualColaRate;
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            Person person,
            LocalDate projectionDate,
            int activeMonths) {

        int yearsSinceStart =
                Math.max(
                        0,
                        projectionDate.getYear()
                                - getStartDate().getYear());

        BigDecimal colaMultiplier =
                BigDecimal.ONE
                        .add(annualColaRate)
                        .pow(yearsSinceStart);

        BigDecimal adjustedMonthlyBenefit =
                monthlyBenefit.multiply(
                        colaMultiplier);

        return adjustedMonthlyBenefit.multiply(
                BigDecimal.valueOf(activeMonths));
    }
}

/*
package com.daviddunn.retirementplanner.domain.income;


import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class Pension extends IncomeSource {

    private final BigDecimal monthlyBenefit;

    private final boolean annualColaRate;

    @JsonCreator
    public Pension(

            @JsonProperty("name")
            String name,

            @JsonProperty("ownership")
            AccountOwnership ownership,

            @JsonProperty("startDate")
            LocalDate commencementDate,

            @JsonProperty("endDate")
            LocalDate terminationDate,

            @JsonProperty("monthlyBenefit")
            BigDecimal monthlyBenefit,

            @JsonProperty("cola")
            boolean annualColaRate) {

        super(
                name,
                ownership,
                commencementDate,
                terminationDate);

        //this.monthlyBenefit = monthlyBenefit;
        this.monthlyBenefit = Objects.requireNonNull(monthlyBenefit);

        this.annualColaRate = annualColaRate;
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public boolean hasCola() {
        return annualColaRate;
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            LocalDate projectionDate,
            int activeMonths) {

        int yearsSinceStart =
                Math.max(
                        0,
                        projectionDate.getYear()
                                - getStartDate().getYear());

        BigDecimal colaMultiplier =
                BigDecimal.ONE
                        .add(annualColaRate)
                        .pow(yearsSinceStart);

        BigDecimal adjustedMonthlyBenefit =
                monthlyBenefit.multiply(
                        colaMultiplier);

        return adjustedMonthlyBenefit.multiply(
                BigDecimal.valueOf(activeMonths));
    }
//    @Override
//    protected BigDecimal calculateAnnualIncome(
//            LocalDate projectionDate,
//            int activeMonths) {
//
//        // COLA support will be added later.
//        return monthlyBenefit.multiply(
//                BigDecimal.valueOf(activeMonths));
//    }
}

 */