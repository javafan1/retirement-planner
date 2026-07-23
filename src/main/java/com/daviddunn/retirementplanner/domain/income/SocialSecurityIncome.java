package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class SocialSecurityIncome extends IncomeSource {

    private static final BigDecimal AGE_62_FACTOR = BigDecimal.valueOf(0.70);
    private static final BigDecimal AGE_63_FACTOR = BigDecimal.valueOf(0.75);
    private static final BigDecimal AGE_64_FACTOR = BigDecimal.valueOf(0.80);
    private static final BigDecimal AGE_65_FACTOR = BigDecimal.valueOf(0.867);
    private static final BigDecimal AGE_66_FACTOR = BigDecimal.valueOf(0.933);
    private static final BigDecimal AGE_68_FACTOR = BigDecimal.valueOf(1.08);
    private static final BigDecimal AGE_69_FACTOR = BigDecimal.valueOf(1.16);
    private static final BigDecimal AGE_70_FACTOR = BigDecimal.valueOf(1.24);

    private final BigDecimal fullRetirementMonthlyBenefit;

    private final int claimingAge;

    private final BigDecimal annualColaRate;

    @JsonCreator
    public SocialSecurityIncome(

            @JsonProperty("name")
            String name,

            @JsonProperty("ownership")
            AccountOwnership ownership,

            @JsonProperty("startDate")
            LocalDate startDate,

            @JsonProperty("endDate")
            LocalDate endDate,

            @JsonProperty("fullRetirementMonthlyBenefit")
            BigDecimal fullRetirementMonthlyBenefit,

            @JsonProperty("claimingAge")
            int claimingAge,

            @JsonProperty("annualColaRate")
            BigDecimal annualColaRate) {

        super(name, ownership, startDate, endDate);

        this.fullRetirementMonthlyBenefit =
                Objects.requireNonNull(fullRetirementMonthlyBenefit);

        this.annualColaRate =
                Objects.requireNonNull(annualColaRate);

        if (claimingAge < 62 || claimingAge > 70) {
            throw new IllegalArgumentException(
                    "Claiming age must be between 62 and 70.");
        }

        this.claimingAge = claimingAge;
    }

    public BigDecimal getFullRetirementMonthlyBenefit() {
        return fullRetirementMonthlyBenefit;
    }

    public int getClaimingAge() {
        return claimingAge;
    }

    public BigDecimal getAnnualColaRate() {
        return annualColaRate;
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            LocalDate projectionDate,
            int activeMonths) {

        BigDecimal monthlyBenefit =
                calculateMonthlyBenefit();

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

        return adjustedMonthlyBenefit
                .multiply(
                        BigDecimal.valueOf(activeMonths));
    }

//    @Override
//    protected BigDecimal calculateAnnualIncome(
//            LocalDate projectionDate,
//            int activeMonths) {
//
//        BigDecimal monthlyBenefit =
//                calculateMonthlyBenefit();
//
//        // TODO: Apply COLA next.
//
//        return monthlyBenefit.multiply(
//                BigDecimal.valueOf(activeMonths));
//    }

    private BigDecimal calculateMonthlyBenefit() {

        return switch (claimingAge) {

            case 62 -> fullRetirementMonthlyBenefit.multiply(AGE_62_FACTOR);

            case 63 -> fullRetirementMonthlyBenefit.multiply(AGE_63_FACTOR);

            case 64 -> fullRetirementMonthlyBenefit.multiply(AGE_64_FACTOR);

            case 65 -> fullRetirementMonthlyBenefit.multiply(AGE_65_FACTOR);

            case 66 -> fullRetirementMonthlyBenefit.multiply(AGE_66_FACTOR);

            case 67 -> fullRetirementMonthlyBenefit;

            case 68 -> fullRetirementMonthlyBenefit.multiply(AGE_68_FACTOR);

            case 69 -> fullRetirementMonthlyBenefit.multiply(AGE_69_FACTOR);

            case 70 -> fullRetirementMonthlyBenefit.multiply(AGE_70_FACTOR);

            default ->
                    throw new IllegalStateException(
                            "Unexpected claiming age: " + claimingAge);
        };
    }
}