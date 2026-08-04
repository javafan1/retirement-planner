
package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.projection.CompoundGrowthService;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public class SocialSecurityIncome extends IncomeSource {

    private final BigDecimal fullRetirementMonthlyBenefit;
    private final int claimingAge;
    private final BigDecimal annualColaRate;
    private final CompoundGrowthService
            compoundGrowthService;

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

        super(
                name,
                ownership,
                startDate,
                endDate);

        this.fullRetirementMonthlyBenefit =
                Objects.requireNonNull(
                        fullRetirementMonthlyBenefit,
                        "Full retirement monthly benefit is required.");

        this.annualColaRate =
                Objects.requireNonNull(
                        annualColaRate,
                        "Annual COLA rate is required.");

        if (claimingAge < 62 || claimingAge > 70) {
            throw new IllegalArgumentException(
                    "Claiming age must be between 62 and 70.");
        }

        this.claimingAge = claimingAge;

        this.compoundGrowthService =
                new CompoundGrowthService();
    }
//
//    @Override
//    protected BigDecimal calculateAnnualIncome(
//            Person person,
//            LocalDate projectionDate,
//            int activeMonths) {
//
//        BigDecimal monthlyBenefit =
//                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
//                        fullRetirementMonthlyBenefit,
//                        person.getBirthDate(),
//                        claimingAge);
//
//        int yearsReceivingBenefits =
//                Math.max(
//                        projectionDate.getYear()
//                                - getStartDate().getYear(),
//                        0);
//
//        BigDecimal colaMultiplier =
//                BigDecimal.ONE
//                        .add(annualColaRate)
//                        .pow(yearsReceivingBenefits);
//
//        return monthlyBenefit
//                .multiply(colaMultiplier)
//                .multiply(BigDecimal.valueOf(activeMonths))
//                .setScale(2, RoundingMode.HALF_UP);
//    }
//
public BigDecimal getFullRetirementMonthlyBenefit() {
        return fullRetirementMonthlyBenefit;
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            Person person,
            LocalDate projectionDate,
            int activeMonths) {

        BigDecimal monthlyBenefit =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        fullRetirementMonthlyBenefit,
                        person.getBirthDate(),
                        claimingAge);

        int yearsReceivingBenefits =
                Math.max(
                        projectionDate.getYear()
                                - getStartDate().getYear(),
                        0);

        BigDecimal projectedMonthlyBenefit =
                compoundGrowthService.project(
                        monthlyBenefit,
                        annualColaRate,
                        yearsReceivingBenefits);

        return projectedMonthlyBenefit
                .multiply(BigDecimal.valueOf(activeMonths))
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }

    public int getClaimingAge() {
        return claimingAge;
    }

    public BigDecimal getAnnualColaRate() {
        return annualColaRate;
    }
}
/*package com.daviddunn.retirementplanner.domain.income;


import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public class SocialSecurityIncome extends IncomeSource {

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

        super(
                name,
                ownership,
                startDate,
                endDate);

        this.fullRetirementMonthlyBenefit =
                Objects.requireNonNull(
                        fullRetirementMonthlyBenefit,
                        "Full retirement monthly benefit is required.");

        this.claimingAge = claimingAge;

        this.annualColaRate =
                Objects.requireNonNull(
                        annualColaRate,
                        "Annual COLA rate is required.");

        if (claimingAge < 62 || claimingAge > 70) {
            throw new IllegalArgumentException(
                    "Claiming age must be between 62 and 70.");
        }
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            LocalDate projectionDate,
            int activeMonths) {

        LocalDate birthDate =
                getStartDate().minusYears(claimingAge);

        BigDecimal monthlyBenefit =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        fullRetirementMonthlyBenefit,
                        birthDate,
                        claimingAge);

        int yearsReceivingBenefits =
                Math.max(
                        projectionDate.getYear()
                                - getStartDate().getYear(),
                        0);

        BigDecimal colaMultiplier =
                BigDecimal.ONE
                        .add(annualColaRate)
                        .pow(yearsReceivingBenefits);

        return monthlyBenefit
                .multiply(colaMultiplier)
                .multiply(BigDecimal.valueOf(activeMonths))
                .setScale(2, RoundingMode.HALF_UP);
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
}

*/

