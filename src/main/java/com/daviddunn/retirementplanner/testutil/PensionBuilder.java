package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class PensionBuilder {

    private String name =
            "Pension";

    private AccountOwnership ownership =
            AccountOwnership.PRIMARY;

    private LocalDate startDate =
            LocalDate.of(2026, 1, 1);

    private LocalDate endDate =
            null;

    private BigDecimal monthlyBenefit =
            BigDecimal.valueOf(1000);

    private BigDecimal annualColaRate =
            BigDecimal.ZERO;

    public static PensionBuilder
    aPension() {

        return new PensionBuilder();
    }

    private PensionBuilder() {
    }

    public PensionBuilder withName(
            String name) {

        this.name =
                Objects.requireNonNull(name);

        return this;
    }

    public PensionBuilder withOwnership(
            AccountOwnership ownership) {

        this.ownership =
                Objects.requireNonNull(ownership);

        return this;
    }

    public PensionBuilder withStartDate(
            LocalDate startDate) {

        this.startDate =
                Objects.requireNonNull(startDate);

        return this;
    }

    public PensionBuilder withEndDate(
            LocalDate endDate) {

        this.endDate = endDate;
        return this;
    }

    public PensionBuilder withMonthlyBenefit(
            long amount) {

        return withMonthlyBenefit(
                BigDecimal.valueOf(amount));
    }

    public PensionBuilder withMonthlyBenefit(
            BigDecimal amount) {

        this.monthlyBenefit =
                Objects.requireNonNull(amount);

        return this;
    }

    public PensionBuilder withAnnualColaRate(
            BigDecimal colaRate) {

        this.annualColaRate =
                Objects.requireNonNull(colaRate);

        return this;
    }

    public PensionBuilder withAnnualColaRate(
            double colaRate) {

        return withAnnualColaRate(
                BigDecimal.valueOf(colaRate));
    }

    public Pension build() {

        return new Pension(
                name,
                ownership,
                startDate,
                endDate,
                monthlyBenefit,
                annualColaRate);
    }
}