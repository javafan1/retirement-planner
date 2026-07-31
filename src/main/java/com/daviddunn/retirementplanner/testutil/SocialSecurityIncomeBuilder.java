package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class SocialSecurityIncomeBuilder {

    private String name =
            "Social Security";

    private AccountOwnership ownership =
            AccountOwnership.PRIMARY;

    private LocalDate startDate =
            LocalDate.of(2030, 1, 1);

    private LocalDate endDate =
            null;

    private BigDecimal fullRetirementMonthlyBenefit =
            BigDecimal.valueOf(3000);

    private int claimingAge =
            67;

    private BigDecimal annualColaRate =
            new BigDecimal("0.025");

    public static SocialSecurityIncomeBuilder
    aSocialSecurityIncome() {

        return new SocialSecurityIncomeBuilder();
    }

    private SocialSecurityIncomeBuilder() {
    }

    public SocialSecurityIncomeBuilder withName(
            String name) {

        this.name =
                Objects.requireNonNull(name);

        return this;
    }

    public SocialSecurityIncomeBuilder withOwnership(
            AccountOwnership ownership) {

        this.ownership =
                Objects.requireNonNull(ownership);

        return this;
    }

    public SocialSecurityIncomeBuilder withStartDate(
            LocalDate startDate) {

        this.startDate =
                Objects.requireNonNull(startDate);

        return this;
    }

    public SocialSecurityIncomeBuilder withEndDate(
            LocalDate endDate) {

        this.endDate = endDate;
        return this;
    }

    public SocialSecurityIncomeBuilder withFullRetirementMonthlyBenefit(
            long amount) {

        return withFullRetirementMonthlyBenefit(
                BigDecimal.valueOf(amount));
    }

    public SocialSecurityIncomeBuilder withFullRetirementMonthlyBenefit(
            BigDecimal amount) {

        this.fullRetirementMonthlyBenefit =
                Objects.requireNonNull(amount);

        return this;
    }

    public SocialSecurityIncomeBuilder withClaimingAge(
            int claimingAge) {

        this.claimingAge = claimingAge;
        return this;
    }

    public SocialSecurityIncomeBuilder withAnnualColaRate(
            BigDecimal colaRate) {

        this.annualColaRate =
                Objects.requireNonNull(colaRate);

        return this;
    }

    public SocialSecurityIncomeBuilder withAnnualColaRate(
            double colaRate) {

        return withAnnualColaRate(
                BigDecimal.valueOf(colaRate));
    }

    public SocialSecurityIncome build() {

        return new SocialSecurityIncome(
                name,
                ownership,
                startDate,
                endDate,
                fullRetirementMonthlyBenefit,
                claimingAge,
                annualColaRate);
    }
}