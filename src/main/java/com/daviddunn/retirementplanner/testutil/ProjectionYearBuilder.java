package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculation;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class ProjectionYearBuilder {

    private int projectionYear = 1;
    private int calendarYear = 2026;
    private int primaryPersonAge = 63;

    private BigDecimal beginningInvestableAssets = BigDecimal.ZERO;
    private BigDecimal investmentGrowth = BigDecimal.ZERO;
    private BigDecimal guaranteedIncome = BigDecimal.ZERO;
    private HouseholdSocialSecurityResult socialSecurityResult =
            HouseholdSocialSecurityResult.zero();
    private BigDecimal annualExpenses = BigDecimal.ZERO;
    private BigDecimal cashFlowNeed = BigDecimal.ZERO;
    private BigDecimal portfolioWithdrawal = BigDecimal.ZERO;
    private BigDecimal requiredMinimumDistribution = BigDecimal.ZERO;
    private BigDecimal excessRmd = BigDecimal.ZERO;
    private BigDecimal taxFundingWithdrawal = BigDecimal.ZERO;
    private BigDecimal endingInvestableAssets = BigDecimal.ZERO;
    private BigDecimal unallocatedCash =
            BigDecimal.ZERO;

    private BigDecimal estimatedHeirTax =
            BigDecimal.ZERO;

    private BigDecimal afterTaxEstateValue =
            BigDecimal.ZERO;

    private List<ProjectedAccountSnapshot> endingAccountSnapshots =
            List.of();

    private FederalTaxCalculation federalTaxCalculation =
            FederalTaxCalculationBuilder
                    .aFederalTaxCalculation()
                    .build();

    private MichiganTaxCalculation michiganTaxCalculation =
            MichiganTaxCalculationBuilder
                    .aMichiganTaxCalculation()
                    .build();

    private MedicarePremiumCalculation medicarePremiumCalculation =
            MedicarePremiumCalculationBuilder
                    .aMedicarePremiumCalculation()
                    .build();

    public static ProjectionYearBuilder aProjectionYear() {
        return new ProjectionYearBuilder();
    }

    private ProjectionYearBuilder() {
    }

    public ProjectionYearBuilder withProjectionYear(int projectionYear) {
        this.projectionYear = projectionYear;
        return this;
    }

    public ProjectionYearBuilder withCalendarYear(int calendarYear) {
        this.calendarYear = calendarYear;
        return this;
    }

    public ProjectionYearBuilder withPrimaryPersonAge(int age) {
        this.primaryPersonAge = age;
        return this;
    }

    public ProjectionYearBuilder withBeginningInvestableAssets(long amount) {
        this.beginningInvestableAssets = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withInvestmentGrowth(long amount) {
        this.investmentGrowth = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withGuaranteedIncome(long amount) {
        this.guaranteedIncome = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withSocialSecurityResult(
            HouseholdSocialSecurityResult result) {

        this.socialSecurityResult =
                Objects.requireNonNull(result);

        return this;
    }

    public ProjectionYearBuilder withAnnualExpenses(long amount) {
        this.annualExpenses = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withCashFlowNeed(long amount) {
        this.cashFlowNeed = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withPortfolioWithdrawal(long amount) {
        this.portfolioWithdrawal = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withRequiredMinimumDistribution(long amount) {
        this.requiredMinimumDistribution = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withExcessRmd(long amount) {
        this.excessRmd = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withTaxFundingWithdrawal(long amount) {
        this.taxFundingWithdrawal = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withEndingInvestableAssets(long amount) {
        this.endingInvestableAssets = BigDecimal.valueOf(amount);
        return this;
    }

    public ProjectionYearBuilder withEndingAccountSnapshots(
            List<ProjectedAccountSnapshot> snapshots) {

        this.endingAccountSnapshots =
                Objects.requireNonNull(snapshots);

        return this;
    }

    public ProjectionYearBuilder withFederalTaxCalculation(
            FederalTaxCalculation calculation) {

        this.federalTaxCalculation =
                Objects.requireNonNull(calculation);

        return this;
    }

    public ProjectionYearBuilder withMichiganTaxCalculation(
            MichiganTaxCalculation calculation) {

        this.michiganTaxCalculation =
                Objects.requireNonNull(calculation);

        return this;
    }

    public ProjectionYearBuilder withUnallocatedCash(
            long amount) {

        this.unallocatedCash =
                BigDecimal.valueOf(amount);

        return this;
    }

    public ProjectionYearBuilder withEstimatedHeirTax(
            long amount) {

        this.estimatedHeirTax =
                BigDecimal.valueOf(amount);

        return this;
    }

    public ProjectionYearBuilder withAfterTaxEstateValue(
            long amount) {

        this.afterTaxEstateValue =
                BigDecimal.valueOf(amount);

        return this;
    }

    public ProjectionYearBuilder withMedicarePremiumCalculation(
            MedicarePremiumCalculation calculation) {

        this.medicarePremiumCalculation =
                Objects.requireNonNull(calculation);

        return this;
    }

    public ProjectionYear build() {

        return new ProjectionYear(
                projectionYear,
                calendarYear,
                beginningInvestableAssets,
                investmentGrowth,
                guaranteedIncome,
                socialSecurityResult,
                annualExpenses,
                cashFlowNeed,
                portfolioWithdrawal,
                requiredMinimumDistribution,
                BigDecimal.ZERO,
                requiredMinimumDistribution,
                excessRmd,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                unallocatedCash,
                HouseholdCashSettlement.zero(),
                endingInvestableAssets,
                endingAccountSnapshots,
                federalTaxCalculation,
                michiganTaxCalculation,
                medicarePremiumCalculation,
                taxFundingWithdrawal,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                estimatedHeirTax,
                afterTaxEstateValue,
                primaryPersonAge
        );
    }
}
