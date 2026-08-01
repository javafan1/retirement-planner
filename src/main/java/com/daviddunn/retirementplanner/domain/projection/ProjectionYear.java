package com.daviddunn.retirementplanner.domain.projection;


import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;

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
    private final BigDecimal federalIncomeTax;
    private final BigDecimal michiganIncomeTax;
    private final int primaryPersonAge;



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
            Integer primaryPersonAge
    ) {


        this.adjustedGrossIncome =
                BigDecimal.ZERO;

        this.taxableSocialSecurity =
                BigDecimal.ZERO;

        this.federalTaxableIncome =
                BigDecimal.ZERO;

        this.federalIncomeTax =
                BigDecimal.ZERO;

        this.taxFundingWithdrawal =
                BigDecimal.ZERO;

        this.michiganIncomeTax =
                BigDecimal.ZERO;


        this.projectionYear = projectionYear;
        this.calendarYear = calendarYear;
        this.primaryPersonAge = primaryPersonAge;


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


        this.endingAccountSnapshots =
                List.of();
    }

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
            List<ProjectedAccountSnapshot> endingAccountSnapshots, int primaryPersonAge) {



        this.adjustedGrossIncome =
                BigDecimal.ZERO;

        this.taxableSocialSecurity =
                BigDecimal.ZERO;

        this.federalTaxableIncome =
                BigDecimal.ZERO;

        this.federalIncomeTax =
                BigDecimal.ZERO;

        this.michiganIncomeTax =
                BigDecimal.ZERO;

        this.projectionYear = projectionYear;
        this.primaryPersonAge = primaryPersonAge;
        this.calendarYear = calendarYear;

        this.taxFundingWithdrawal =
                BigDecimal.ZERO;

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
    }

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
            FederalTaxCalculation federalTaxCalculation,
            MichiganTaxCalculation michiganTaxCalculation,
            BigDecimal taxFundingWithdrawal, int primaryPersonAge) {

        this.projectionYear = projectionYear;
        this.calendarYear = calendarYear;
        this.primaryPersonAge = primaryPersonAge;
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

        this.taxFundingWithdrawal =
                Objects.requireNonNull(
                        taxFundingWithdrawal,
                        "Tax funding withdrawal is required.");


        if (taxFundingWithdrawal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Tax funding withdrawal cannot be negative.");
        }

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

        this.federalIncomeTax =
                federalTaxCalculation
                        .getFederalIncomeTax();

        this.michiganIncomeTax =
                michiganTaxCalculation
                        .incomeTax();
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
    public BigDecimal getTaxFundingWithdrawal() {
        return taxFundingWithdrawal;
    }

    public int getPrimaryPersonAge() {
        return primaryPersonAge;
    }

    public BigDecimal getMichiganIncomeTax() {
        return michiganIncomeTax;
    }
}