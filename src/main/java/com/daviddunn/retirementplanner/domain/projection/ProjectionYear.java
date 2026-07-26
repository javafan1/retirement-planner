package com.daviddunn.retirementplanner.domain.projection;


import com.daviddunn.retirementplanner.domain.financial.Account;

import java.util.List;
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

    private final BigDecimal endingInvestableAssets;

    private final List<ProjectedAccountSnapshot> endingAccountSnapshots;

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
            BigDecimal endingInvestableAssets) {

        this.projectionYear = projectionYear;
        this.calendarYear = calendarYear;


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
            List<ProjectedAccountSnapshot> endingAccountSnapshots) {

        this.projectionYear = projectionYear;
        this.calendarYear = calendarYear;

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

}