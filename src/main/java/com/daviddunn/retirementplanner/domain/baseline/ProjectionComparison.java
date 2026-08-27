package com.daviddunn.retirementplanner.domain.baseline;

import java.math.BigDecimal;
import java.util.Objects;

public record ProjectionComparison(
        int calendarYear,
        BigDecimal baselineInvestmentGrowth,
        BigDecimal currentInvestmentGrowth,
        BigDecimal baselineTotalIncome,
        BigDecimal currentTotalIncome,
        BigDecimal baselineTotalTaxes,
        BigDecimal currentTotalTaxes,
        BigDecimal baselinePeakAnnualTax,
        BigDecimal currentPeakAnnualTax,
        BigDecimal baselineEndingInvestableAssets,
        BigDecimal currentEndingInvestableAssets,
        BigDecimal baselineNetWorth,
        BigDecimal currentNetWorth,
        BigDecimal baselineAfterTaxEstate,
        BigDecimal currentAfterTaxEstate) {

    public ProjectionComparison {
        Objects.requireNonNull(baselineInvestmentGrowth);
        Objects.requireNonNull(currentInvestmentGrowth);
        Objects.requireNonNull(baselineTotalIncome);
        Objects.requireNonNull(currentTotalIncome);
        Objects.requireNonNull(baselineTotalTaxes);
        Objects.requireNonNull(currentTotalTaxes);
        Objects.requireNonNull(baselinePeakAnnualTax);
        Objects.requireNonNull(currentPeakAnnualTax);
        Objects.requireNonNull(baselineEndingInvestableAssets);
        Objects.requireNonNull(currentEndingInvestableAssets);
        Objects.requireNonNull(baselineNetWorth);
        Objects.requireNonNull(currentNetWorth);
        Objects.requireNonNull(baselineAfterTaxEstate);
        Objects.requireNonNull(currentAfterTaxEstate);
    }

    public BigDecimal getInvestmentGrowthChange() { return currentInvestmentGrowth.subtract(baselineInvestmentGrowth); }
    public BigDecimal getTotalIncomeChange() { return currentTotalIncome.subtract(baselineTotalIncome); }
    public BigDecimal getTotalTaxesChange() { return currentTotalTaxes.subtract(baselineTotalTaxes); }
    public BigDecimal getPeakAnnualTaxChange() { return currentPeakAnnualTax.subtract(baselinePeakAnnualTax); }
    public BigDecimal getEndingInvestableAssetsChange() { return currentEndingInvestableAssets.subtract(baselineEndingInvestableAssets); }
    public BigDecimal getNetWorthChange() { return currentNetWorth.subtract(baselineNetWorth); }
    public BigDecimal getAfterTaxEstateChange() { return currentAfterTaxEstate.subtract(baselineAfterTaxEstate); }

    public int getCalendarYear() { return calendarYear; }
    public BigDecimal getBaselineInvestmentGrowth() { return baselineInvestmentGrowth; }
    public BigDecimal getCurrentInvestmentGrowth() { return currentInvestmentGrowth; }
    public BigDecimal getBaselineTotalIncome() { return baselineTotalIncome; }
    public BigDecimal getCurrentTotalIncome() { return currentTotalIncome; }
    public BigDecimal getBaselineTotalTaxes() { return baselineTotalTaxes; }
    public BigDecimal getCurrentTotalTaxes() { return currentTotalTaxes; }
    public BigDecimal getBaselinePeakAnnualTax() { return baselinePeakAnnualTax; }
    public BigDecimal getCurrentPeakAnnualTax() { return currentPeakAnnualTax; }
    public BigDecimal getBaselineEndingInvestableAssets() { return baselineEndingInvestableAssets; }
    public BigDecimal getCurrentEndingInvestableAssets() { return currentEndingInvestableAssets; }
    public BigDecimal getBaselineNetWorth() { return baselineNetWorth; }
    public BigDecimal getCurrentNetWorth() { return currentNetWorth; }
    public BigDecimal getBaselineAfterTaxEstate() { return baselineAfterTaxEstate; }
    public BigDecimal getCurrentAfterTaxEstate() { return currentAfterTaxEstate; }
}
