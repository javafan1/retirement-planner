
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
    private final int benefitValuationYear;
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
            BigDecimal annualColaRate,

            @JsonProperty("benefitValuationYear")
            Integer benefitValuationYear) {

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

        this.benefitValuationYear =
                benefitValuationYear != null
                        ? benefitValuationYear
                        : startDate.getYear();

        this.compoundGrowthService =
                new CompoundGrowthService();
    }

    public SocialSecurityIncome(
            String name,
            AccountOwnership ownership,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal fullRetirementMonthlyBenefit,
            int claimingAge,
            BigDecimal annualColaRate) {

        this(
                name,
                ownership,
                startDate,
                endDate,
                fullRetirementMonthlyBenefit,
                claimingAge,
                annualColaRate,
                startDate.getYear());
    }

public BigDecimal getFullRetirementMonthlyBenefit() {
        return fullRetirementMonthlyBenefit;
    }

    @Override
    protected BigDecimal calculateAnnualIncome(
            Person person,
            LocalDate projectionDate,
            int activeMonths) {

        return getProjectedMonthlyBenefit(
                person,
                projectionDate)
                .multiply(
                        BigDecimal.valueOf(activeMonths))
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

    public int getBenefitValuationYear() {
        return benefitValuationYear;
    }

    public BigDecimal getAnnualIncome(
            Person person,
            LocalDate projectionDate,
            BigDecimal socialSecurityColaRate) {

        int activeMonths = getActiveMonths(
                projectionDate.getYear());

        if (activeMonths == 0) {
            return BigDecimal.ZERO;
        }

        return getProjectedMonthlyBenefit(
                person,
                projectionDate,
                socialSecurityColaRate)
                .multiply(BigDecimal.valueOf(activeMonths))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getProjectedMonthlyBenefit(
            Person person,
            LocalDate projectionDate) {

        return getProjectedMonthlyBenefit(
                person,
                projectionDate,
                annualColaRate);
    }

    public BigDecimal getProjectedMonthlyBenefit(
            Person person,
            LocalDate projectionDate,
            BigDecimal socialSecurityColaRate) {

        Objects.requireNonNull(
                person,
                "Person is required.");

        Objects.requireNonNull(
                projectionDate,
                "Projection date is required.");

        BigDecimal monthlyBenefit =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        fullRetirementMonthlyBenefit,
                        person.getBirthDate(),
                        claimingAge);

        Objects.requireNonNull(
                socialSecurityColaRate,
                "Social Security COLA rate is required.");

        int colaYears =
                Math.max(
                        projectionDate.getYear()
                                - benefitValuationYear,
                        0);

        return compoundGrowthService.project(
                        monthlyBenefit,
                        socialSecurityColaRate,
                        colaYears)
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }

    public BigDecimal getMonthlyBenefitAtDeath(
            Person person,
            LocalDate deathDate) {

        return getMonthlyBenefitAtDeath(
                person,
                deathDate,
                annualColaRate);
    }

    public BigDecimal getMonthlyBenefitAtDeath(
            Person person,
            LocalDate deathDate,
            BigDecimal socialSecurityColaRate) {

        Objects.requireNonNull(
                person,
                "Person is required.");

        Objects.requireNonNull(
                deathDate,
                "Death date is required.");

        /*
         * Social Security had not started by the
         * death date.
         *
         * For the MVP, use the full-retirement
         * monthly benefit as the survivor-benefit
         * base.
         */
        if (getStartDate().isAfter(deathDate)) {

            int colaYears = Math.max(
                    deathDate.getYear() - benefitValuationYear,
                    0);

            return compoundGrowthService.project(
                    fullRetirementMonthlyBenefit,
                    socialSecurityColaRate,
                    colaYears)
                    .setScale(
                            2,
                            RoundingMode.HALF_UP);
        }

        /*
         * Social Security had already started.
         *
         * Use the actual retirement benefit,
         * including its claiming-age calculation
         * and applicable COLA projection.
         */
        return getProjectedMonthlyBenefit(
                person,
                deathDate,
                socialSecurityColaRate);
    }


}
