package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class Pension extends IncomeSource {

    private final BigDecimal monthlyBenefit;

    private final BigDecimal survivorMonthlyBenefit;

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
            BigDecimal annualColaRate,

            @JsonProperty("survivorMonthlyBenefit")
            BigDecimal survivorMonthlyBenefit) {

        super(
                name,
                ownership,
                commencementDate,
                terminationDate);

        this.monthlyBenefit =
                Objects.requireNonNull(
                        monthlyBenefit,
                        "Monthly benefit is required.");

        this.survivorMonthlyBenefit =
                survivorMonthlyBenefit;

        this.annualColaRate =
                annualColaRate != null
                        ? annualColaRate
                        : BigDecimal.ZERO;
    }

    /*
     * Compatibility constructor.
     *
     * Existing pensions that do not specify
     * survivor benefits continue to work.
     */
    public Pension(
            String name,
            AccountOwnership ownership,
            LocalDate commencementDate,
            LocalDate terminationDate,
            BigDecimal monthlyBenefit,
            BigDecimal annualColaRate) {

        this(
                name,
                ownership,
                commencementDate,
                terminationDate,
                monthlyBenefit,
                annualColaRate,
                null);
    }

    public BigDecimal getMonthlyBenefit() {
        return monthlyBenefit;
    }

    public BigDecimal getSurvivorMonthlyBenefit() {
        return survivorMonthlyBenefit;
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

    @JsonIgnore
    public BigDecimal getAnnualSurvivorIncome(
            LocalDate projectionDate) {

        int activeMonths =
                getActiveMonths(
                        projectionDate.getYear());

        if (activeMonths == 0
                || survivorMonthlyBenefit == null) {

            return BigDecimal.ZERO;
        }

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
                survivorMonthlyBenefit.multiply(
                        colaMultiplier);

        return adjustedMonthlyBenefit.multiply(
                BigDecimal.valueOf(activeMonths));
    }

}