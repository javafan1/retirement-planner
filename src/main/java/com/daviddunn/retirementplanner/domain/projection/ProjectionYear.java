package com.daviddunn.retirementplanner.domain.projection;


import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculation;
import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;

import java.math.RoundingMode;
import java.util.List;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.Objects;

public class ProjectionYear {

    private final int projectionYear;
    private final int calendarYear;

    private final BigDecimal beginningInvestableAssets;
    private final BigDecimal investmentGrowth;

    private final BigDecimal guaranteedIncome;
    private final BigDecimal annualExpenses;

    private final BigDecimal cashFlowNeed;
    private final BigDecimal portfolioWithdrawal;
    private final BigDecimal requiredMinimumDistribution;
    private final BigDecimal excessRmd;
    private final BigDecimal taxFundingWithdrawal;

    private final BigDecimal endingInvestableAssets;

    private final List<ProjectedAccountSnapshot> endingAccountSnapshots;

    private final BigDecimal adjustedGrossIncome;
    private final BigDecimal taxableSocialSecurity;
    private final BigDecimal federalTaxableIncome;
    private final BigDecimal federalStandardDeduction;
    private final BigDecimal federalIncomeTax;
    private final BigDecimal michiganIncomeTax;

    private final int primaryPersonAge;

    private final BigDecimal michiganRetirementIncome;
    private final BigDecimal michiganRetirementDeduction;
    private final BigDecimal michiganTaxableIncome;

    private final MedicarePremiumCalculation
            medicarePremiumCalculation;

    private final BigDecimal rothConversion;

    private final BigDecimal beginningRetainedRmdAssets;
    private final BigDecimal retainedRmdAssetGrowth;
    private final BigDecimal endingRetainedRmdAssets;

    private final BigDecimal unallocatedCash;

    private final BigDecimal combinedEffectiveTaxRate;

    private final BigDecimal estimatedHeirTax;
    private final BigDecimal afterTaxEstateValue;


    /*
     * Existing compatibility constructor.
     *
     * Roth conversion defaults to zero.
     */
    public ProjectionYear(
            int projectionYear,
            int calendarYear,
            BigDecimal beginningInvestableAssets,
            BigDecimal investmentGrowth,
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses,
            BigDecimal cashFlowNeed,
            BigDecimal portfolioWithdrawal,
            BigDecimal requiredMinimumDistribution,
            BigDecimal excessRmd,
            BigDecimal endingInvestableAssets,
            Integer primaryPersonAge) {

        this.projectionYear =
                projectionYear;

        this.calendarYear =
                calendarYear;

        this.primaryPersonAge =
                primaryPersonAge;

        this.beginningInvestableAssets =
                Objects.requireNonNull(
                        beginningInvestableAssets,
                        "beginningInvestableAssets");

        this.investmentGrowth =
                Objects.requireNonNull(
                        investmentGrowth,
                        "investmentGrowth");

        this.guaranteedIncome =
                Objects.requireNonNull(
                        guaranteedIncome,
                        "guaranteedIncome");

        this.annualExpenses =
                Objects.requireNonNull(
                        annualExpenses,
                        "annualExpenses");

        this.cashFlowNeed =
                Objects.requireNonNull(
                        cashFlowNeed,
                        "cashFlowNeed");

        this.portfolioWithdrawal =
                Objects.requireNonNull(
                        portfolioWithdrawal,
                        "portfolioWithdrawal");

        this.requiredMinimumDistribution =
                Objects.requireNonNull(
                        requiredMinimumDistribution,
                        "requiredMinimumDistribution");

        this.excessRmd =
                Objects.requireNonNull(
                        excessRmd,
                        "excessRmd");

        this.endingInvestableAssets =
                Objects.requireNonNull(
                        endingInvestableAssets,
                        "endingInvestableAssets");

        this.combinedEffectiveTaxRate =
                BigDecimal.ZERO;


        this.unallocatedCash = BigDecimal.ZERO;
        this.beginningRetainedRmdAssets = BigDecimal.ZERO;
        this.retainedRmdAssetGrowth = BigDecimal.ZERO;
        this.endingRetainedRmdAssets = BigDecimal.ZERO;
//        this.unallocatedCash =
//                Objects.requireNonNull(
//                        unallocatedCash,
//                        "Unallocated cash is required.");
//
//        if (unallocatedCash.signum() < 0) {
//            throw new IllegalArgumentException(
//                    "Unallocated cash cannot be negative.");
//        }

        this.estimatedHeirTax =
                BigDecimal.ZERO;

        this.afterTaxEstateValue =
                BigDecimal.ZERO;

        this.adjustedGrossIncome =
                BigDecimal.ZERO;

        this.taxableSocialSecurity =
                BigDecimal.ZERO;

        this.federalTaxableIncome =
                BigDecimal.ZERO;

        this.federalStandardDeduction =
                BigDecimal.ZERO;

        this.federalIncomeTax =
                BigDecimal.ZERO;

        this.taxFundingWithdrawal =
                BigDecimal.ZERO;

        this.michiganIncomeTax =
                BigDecimal.ZERO;

        this.michiganRetirementIncome =
                BigDecimal.ZERO;

        this.michiganRetirementDeduction =
                BigDecimal.ZERO;

        this.michiganTaxableIncome =
                BigDecimal.ZERO;

        this.medicarePremiumCalculation =
                null;

        this.endingAccountSnapshots =
                List.of();

        this.rothConversion =
                BigDecimal.ZERO;
    }


    /*
     * Compatibility constructor with account snapshots.
     *
     * Roth conversion defaults to zero.
     */
    public ProjectionYear(
            int projectionYear,
            int calendarYear,
            BigDecimal beginningInvestableAssets,
            BigDecimal investmentGrowth,
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses,
            BigDecimal cashFlowNeed,
            BigDecimal portfolioWithdrawal,
            BigDecimal requiredMinimumDistribution,
            BigDecimal excessRmd,
            BigDecimal endingInvestableAssets,
            List<ProjectedAccountSnapshot> endingAccountSnapshots,
            int primaryPersonAge) {

        this.projectionYear =
                projectionYear;

        this.calendarYear =
                calendarYear;

        this.primaryPersonAge =
                primaryPersonAge;

        this.beginningInvestableAssets =
                Objects.requireNonNull(
                        beginningInvestableAssets,
                        "beginningInvestableAssets");

        this.investmentGrowth =
                Objects.requireNonNull(
                        investmentGrowth,
                        "investmentGrowth");

        this.guaranteedIncome =
                Objects.requireNonNull(
                        guaranteedIncome,
                        "guaranteedIncome");

        this.annualExpenses =
                Objects.requireNonNull(
                        annualExpenses,
                        "annualExpenses");

        this.cashFlowNeed =
                Objects.requireNonNull(
                        cashFlowNeed,
                        "cashFlowNeed");

        this.portfolioWithdrawal =
                Objects.requireNonNull(
                        portfolioWithdrawal,
                        "portfolioWithdrawal");

        this.requiredMinimumDistribution =
                Objects.requireNonNull(
                        requiredMinimumDistribution,
                        "requiredMinimumDistribution");

        this.excessRmd =
                Objects.requireNonNull(
                        excessRmd,
                        "excessRmd");

        this.endingInvestableAssets =
                Objects.requireNonNull(
                        endingInvestableAssets,
                        "endingInvestableAssets");

        Objects.requireNonNull(
                endingAccountSnapshots,
                "Ending account snapshots are required.");

        this.unallocatedCash = BigDecimal.ZERO;
        this.beginningRetainedRmdAssets = BigDecimal.ZERO;
        this.retainedRmdAssetGrowth = BigDecimal.ZERO;
        this.endingRetainedRmdAssets = BigDecimal.ZERO;

        this.estimatedHeirTax =
                BigDecimal.ZERO;

        this.afterTaxEstateValue =
                BigDecimal.ZERO;

        this.endingAccountSnapshots =
                List.copyOf(
                        endingAccountSnapshots);

        this.adjustedGrossIncome =
                BigDecimal.ZERO;

        this.taxableSocialSecurity =
                BigDecimal.ZERO;

        this.federalTaxableIncome =
                BigDecimal.ZERO;

        this.combinedEffectiveTaxRate =
                BigDecimal.ZERO;

        this.federalStandardDeduction =
                BigDecimal.ZERO;

        this.federalIncomeTax =
                BigDecimal.ZERO;

        this.taxFundingWithdrawal =
                BigDecimal.ZERO;

        this.michiganIncomeTax =
                BigDecimal.ZERO;

        this.michiganRetirementIncome =
                BigDecimal.ZERO;

        this.michiganRetirementDeduction =
                BigDecimal.ZERO;

        this.michiganTaxableIncome =
                BigDecimal.ZERO;

        this.medicarePremiumCalculation =
                null;

        this.rothConversion =
                BigDecimal.ZERO;
    }


    /*
     * Full projection constructor.
     *
     * This constructor is used when tax calculations,
     * Medicare calculations, account snapshots, and
     * Roth conversion results are available.
     */
    public ProjectionYear(
            int projectionYear,
            int calendarYear,
            BigDecimal beginningInvestableAssets,
            BigDecimal investmentGrowth,
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses,
            BigDecimal cashFlowNeed,
            BigDecimal portfolioWithdrawal,
            BigDecimal requiredMinimumDistribution,
            BigDecimal excessRmd,
            BigDecimal beginningRetainedRmdAssets,
            BigDecimal retainedRmdAssetGrowth,
            BigDecimal unallocatedCash,
            BigDecimal endingInvestableAssets,
            List<ProjectedAccountSnapshot> endingAccountSnapshots,
            FederalTaxCalculation federalTaxCalculation,
            MichiganTaxCalculation michiganTaxCalculation,
            MedicarePremiumCalculation medicarePremiumCalculation,
            BigDecimal taxFundingWithdrawal,
            BigDecimal rothConversion,
            BigDecimal estimatedHeirTax,
            BigDecimal afterTaxEstateValue,
            int primaryPersonAge) {

        this.projectionYear =
                projectionYear;

        this.calendarYear =
                calendarYear;

        this.primaryPersonAge =
                primaryPersonAge;

        this.beginningInvestableAssets =
                Objects.requireNonNull(
                        beginningInvestableAssets,
                        "beginningInvestableAssets");

        this.investmentGrowth =
                Objects.requireNonNull(
                        investmentGrowth,
                        "investmentGrowth");

        this.guaranteedIncome =
                Objects.requireNonNull(
                        guaranteedIncome,
                        "guaranteedIncome");

        this.annualExpenses =
                Objects.requireNonNull(
                        annualExpenses,
                        "annualExpenses");

        this.cashFlowNeed =
                Objects.requireNonNull(
                        cashFlowNeed,
                        "cashFlowNeed");

        this.portfolioWithdrawal =
                Objects.requireNonNull(
                        portfolioWithdrawal,
                        "portfolioWithdrawal");

        this.requiredMinimumDistribution =
                Objects.requireNonNull(
                        requiredMinimumDistribution,
                        "requiredMinimumDistribution");

        this.excessRmd =
                Objects.requireNonNull(
                        excessRmd,
                        "excessRmd");

        this.endingInvestableAssets =
                Objects.requireNonNull(
                        endingInvestableAssets,
                        "endingInvestableAssets");

        Objects.requireNonNull(
                endingAccountSnapshots,
                "Ending account snapshots are required.");

        this.endingAccountSnapshots =
                List.copyOf(
                        endingAccountSnapshots);

        Objects.requireNonNull(
                federalTaxCalculation,
                "Federal tax calculation is required.");

        this.adjustedGrossIncome =
                federalTaxCalculation
                        .getAdjustedGrossIncome();

        this.taxableSocialSecurity =
                federalTaxCalculation
                        .getTaxableSocialSecurity();

        this.federalTaxableIncome =
                federalTaxCalculation
                        .getTaxableIncome();

        this.federalStandardDeduction =
                federalTaxCalculation
                        .getStandardDeduction();

        this.federalIncomeTax =
                federalTaxCalculation
                        .getFederalIncomeTax();



        Objects.requireNonNull(
                michiganTaxCalculation,
                "Michigan tax calculation is required.");

        this.michiganRetirementIncome =
                michiganTaxCalculation
                        .retirementIncome();

        this.michiganRetirementDeduction =
                michiganTaxCalculation
                        .retirementDeduction();

        this.michiganTaxableIncome =
                michiganTaxCalculation
                        .taxableIncome();

        this.michiganIncomeTax =
                michiganTaxCalculation
                        .incomeTax();

        BigDecimal totalIncomeTax =
                this.federalIncomeTax
                        .add(this.michiganIncomeTax);

        if (this.adjustedGrossIncome.signum() == 0) {
            this.combinedEffectiveTaxRate =
                    BigDecimal.ZERO;
        } else {
            this.combinedEffectiveTaxRate =
                    totalIncomeTax
                            .divide(
                                    this.adjustedGrossIncome,
                                    10,
                                    RoundingMode.HALF_UP);
        }

        Objects.requireNonNull(
                medicarePremiumCalculation,
                "Medicare premium calculation is required.");

        this.medicarePremiumCalculation =
                medicarePremiumCalculation;

        this.taxFundingWithdrawal =
                Objects.requireNonNull(
                        taxFundingWithdrawal,
                        "Tax funding withdrawal is required.");

        if (taxFundingWithdrawal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Tax funding withdrawal cannot be negative.");
        }

        this.unallocatedCash =
                Objects.requireNonNull(
                        unallocatedCash,
                        "unallocatedCash");

        this.beginningRetainedRmdAssets =
                Objects.requireNonNull(
                        beginningRetainedRmdAssets,
                        "beginningRetainedRmdAssets");

        this.retainedRmdAssetGrowth =
                Objects.requireNonNull(
                        retainedRmdAssetGrowth,
                        "retainedRmdAssetGrowth");

        this.endingRetainedRmdAssets =
                this.unallocatedCash;

        this.rothConversion =
                Objects.requireNonNull(
                        rothConversion,
                        "Roth conversion is required.");

        if (rothConversion.signum() < 0) {
            throw new IllegalArgumentException(
                    "Roth conversion cannot be negative.");
        }
        this.estimatedHeirTax =
                Objects.requireNonNull(
                        estimatedHeirTax,
                        "Estimated heir tax is required.");

        if (estimatedHeirTax.signum() < 0) {
            throw new IllegalArgumentException(
                    "Estimated heir tax cannot be negative.");
        }

        this.afterTaxEstateValue =
                Objects.requireNonNull(
                        afterTaxEstateValue,
                        "After-tax estate value is required.");

        if (afterTaxEstateValue.signum() < 0) {
            throw new IllegalArgumentException(
                    "After-tax estate value cannot be negative.");
        }


    }


    public int getProjectionYear() {
        return projectionYear;
    }

    public int getCalendarYear() {
        return calendarYear;
    }

    public BigDecimal getBeginningInvestableAssets() {
        return beginningInvestableAssets;
    }

    public BigDecimal getInvestmentGrowth() {
        return investmentGrowth;
    }

    public BigDecimal getGuaranteedIncome() {
        return guaranteedIncome;
    }

    public BigDecimal getAnnualExpenses() {
        return annualExpenses;
    }

    public BigDecimal getCashFlowNeed() {
        return cashFlowNeed;
    }

    public BigDecimal getPortfolioWithdrawal() {
        return portfolioWithdrawal;
    }

    public BigDecimal getRequiredMinimumDistribution() {
        return requiredMinimumDistribution;
    }

    public BigDecimal getExcessRmd() {
        return excessRmd;
    }

    @JsonIgnore
    public BigDecimal getReinvestableExcessRmd() {
        return getExcessRmd();
    }

    public BigDecimal getEndingInvestableAssets() {
        return endingInvestableAssets;
    }

    public BigDecimal getRothConversion() {
        return rothConversion;
    }

    @JsonIgnore
    public BigDecimal getNetCashFlow() {

        return guaranteedIncome
                .subtract(annualExpenses);
    }

    @JsonIgnore
    public BigDecimal getAssetChange() {

        return endingInvestableAssets
                .subtract(beginningInvestableAssets);
    }

    public List<ProjectedAccountSnapshot>
    getEndingAccountSnapshots() {

        return endingAccountSnapshots;
    }

    @JsonIgnore
    public BigDecimal getEndingBalance(
            Account account) {

        Objects.requireNonNull(
                account,
                "Account is required.");

        return endingAccountSnapshots
                .stream()
                .filter(snapshot ->
                        snapshot.getAccount() == account)
                .map(ProjectedAccountSnapshot::getEndingBalance)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Account is not part of this projection year."));
    }

//    public BigDecimal getUnallocatedCash() {
//
//
//        return this.getEndingBalance();
//    }

    public BigDecimal getAdjustedGrossIncome() {
        return adjustedGrossIncome;
    }

    public BigDecimal getTaxableSocialSecurity() {
        return taxableSocialSecurity;
    }

    public BigDecimal getFederalTaxableIncome() {
        return federalTaxableIncome;
    }

    public BigDecimal getFederalIncomeTax() {
        return federalIncomeTax;
    }

    public BigDecimal getCombinedEffectiveTaxRate() {
        return combinedEffectiveTaxRate;
    }

    public BigDecimal getTaxFundingWithdrawal() {
        return taxFundingWithdrawal;
    }

    public int getPrimaryPersonAge() {
        return primaryPersonAge;
    }

    public BigDecimal getMichiganIncomeTax() {
        return michiganIncomeTax;
    }

    public BigDecimal getMichiganRetirementIncome() {
        return michiganRetirementIncome;
    }

    public BigDecimal getMichiganRetirementDeduction() {
        return michiganRetirementDeduction;
    }

    public BigDecimal getMichiganTaxableIncome() {
        return michiganTaxableIncome;
    }

    public BigDecimal getTotalIncomeTax() {

        return federalIncomeTax.add(
                michiganIncomeTax);
    }

    public BigDecimal getFederalStandardDeduction() {
        return federalStandardDeduction;
    }

    public BigDecimal getAnnualMedicarePremium() {

        return medicarePremiumCalculation
                .totalAnnualMedicarePremium();
    }

    public BigDecimal getMonthlyPartBPremium() {

        return medicarePremiumCalculation
                .monthlyPartBPremium();
    }

    public BigDecimal getMonthlyPartDPremium() {

        return medicarePremiumCalculation
                .monthlyPartDPremium();
    }

    public BigDecimal getModifiedAdjustedGrossIncome() {

        return medicarePremiumCalculation
                .modifiedAdjustedGrossIncome();
    }

    public MedicarePremiumCalculation
    getMedicarePremiumCalculation() {

        return medicarePremiumCalculation;
    }

    public BigDecimal getAnnualPartBPremium() {

        return medicarePremiumCalculation
                .annualPartBPremium();
    }

    public BigDecimal getAnnualPartDPremium() {

        return medicarePremiumCalculation
                .annualPartDPremium();
    }

    public IrmaaBracket getIrmaaBracket() {

        return medicarePremiumCalculation
                .irmaaBracket();
    }

    public String getIrmaaBracketDisplay() {

        return medicarePremiumCalculation
                .irmaaBracket()
                .getDisplayRange();
    }

    public BigDecimal getUnallocatedCash() {
        return unallocatedCash;
    }

    public BigDecimal getBeginningRetainedRmdAssets() {
        return beginningRetainedRmdAssets;
    }

    public BigDecimal getRetainedRmdAssetGrowth() {
        return retainedRmdAssetGrowth;
    }

    public BigDecimal getEndingRetainedRmdAssets() {
        return endingRetainedRmdAssets;
    }

    public BigDecimal getEstimatedHeirTax() {
        return estimatedHeirTax;
    }

    public BigDecimal getAfterTaxEstateValue() {
        return afterTaxEstateValue;
    }
}
