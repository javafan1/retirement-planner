package com.daviddunn.retirementplanner.domain.baseline;

import java.math.BigDecimal;
import java.util.Objects;

public class ProjectionComparison {

    private final int calendarYear;

    private final BigDecimal
            baselineEndingInvestableAssets;

    private final BigDecimal
            currentEndingInvestableAssets;

    private final BigDecimal
            endingInvestableAssetsChange;

    private final BigDecimal
            baselineNonInvestableAssets;

    private final BigDecimal
            currentNonInvestableAssets;

    private final BigDecimal
            nonInvestableAssetsChange;

    private final BigDecimal
            baselineNetWorth;

    private final BigDecimal
            currentNetWorth;

    private final BigDecimal
            netWorthChange;

    private final BigDecimal
            baselineAfterTaxEstate;

    private final BigDecimal
            currentAfterTaxEstate;

    private final BigDecimal
            afterTaxEstateChange;

    private final BigDecimal
            baselineEffectiveTaxRate;

    private final BigDecimal
            currentEffectiveTaxRate;

    private final BigDecimal
            effectiveTaxRateChange;

    private final BigDecimal
            baselinePeakInvestableAssets;

    private final BigDecimal
            currentPeakInvestableAssets;

    private final BigDecimal
            peakInvestableAssetsChange;


    public ProjectionComparison(
            int calendarYear,

            BigDecimal baselineEndingInvestableAssets,
            BigDecimal currentEndingInvestableAssets,

            BigDecimal baselineNonInvestableAssets,
            BigDecimal currentNonInvestableAssets,

            BigDecimal baselineNetWorth,
            BigDecimal currentNetWorth,

            BigDecimal baselineAfterTaxEstate,
            BigDecimal currentAfterTaxEstate,

            BigDecimal baselineEffectiveTaxRate,
            BigDecimal currentEffectiveTaxRate,

            BigDecimal baselinePeakInvestableAssets,
            BigDecimal currentPeakInvestableAssets) {

        this.calendarYear =
                calendarYear;

        this.baselineEndingInvestableAssets =
                Objects.requireNonNull(
                        baselineEndingInvestableAssets);

        this.currentEndingInvestableAssets =
                Objects.requireNonNull(
                        currentEndingInvestableAssets);

        this.endingInvestableAssetsChange =
                currentEndingInvestableAssets.subtract(
                        baselineEndingInvestableAssets);

        this.baselineNonInvestableAssets =
                Objects.requireNonNull(
                        baselineNonInvestableAssets);

        this.currentNonInvestableAssets =
                Objects.requireNonNull(
                        currentNonInvestableAssets);

        this.nonInvestableAssetsChange =
                currentNonInvestableAssets.subtract(
                        baselineNonInvestableAssets);

        this.baselineNetWorth =
                Objects.requireNonNull(
                        baselineNetWorth);

        this.currentNetWorth =
                Objects.requireNonNull(
                        currentNetWorth);

        this.netWorthChange =
                currentNetWorth.subtract(
                        baselineNetWorth);

        this.baselineAfterTaxEstate =
                Objects.requireNonNull(
                        baselineAfterTaxEstate);

        this.currentAfterTaxEstate =
                Objects.requireNonNull(
                        currentAfterTaxEstate);

        this.afterTaxEstateChange =
                currentAfterTaxEstate.subtract(
                        baselineAfterTaxEstate);

        this.baselineEffectiveTaxRate =
                Objects.requireNonNull(
                        baselineEffectiveTaxRate);

        this.currentEffectiveTaxRate =
                Objects.requireNonNull(
                        currentEffectiveTaxRate);

        this.effectiveTaxRateChange =
                currentEffectiveTaxRate.subtract(
                        baselineEffectiveTaxRate);

        this.baselinePeakInvestableAssets =
                Objects.requireNonNull(
                        baselinePeakInvestableAssets);

        this.currentPeakInvestableAssets =
                Objects.requireNonNull(
                        currentPeakInvestableAssets);

        this.peakInvestableAssetsChange =
                currentPeakInvestableAssets.subtract(
                        baselinePeakInvestableAssets);
    }


    public int getCalendarYear() {
        return calendarYear;
    }


    public BigDecimal
    getBaselineEndingInvestableAssets() {

        return baselineEndingInvestableAssets;
    }


    public BigDecimal
    getCurrentEndingInvestableAssets() {

        return currentEndingInvestableAssets;
    }


    public BigDecimal
    getEndingInvestableAssetsChange() {

        return endingInvestableAssetsChange;
    }


    public BigDecimal
    getBaselineNonInvestableAssets() {

        return baselineNonInvestableAssets;
    }


    public BigDecimal
    getCurrentNonInvestableAssets() {

        return currentNonInvestableAssets;
    }


    public BigDecimal
    getNonInvestableAssetsChange() {

        return nonInvestableAssetsChange;
    }


    public BigDecimal
    getBaselineNetWorth() {

        return baselineNetWorth;
    }


    public BigDecimal
    getCurrentNetWorth() {

        return currentNetWorth;
    }


    public BigDecimal
    getNetWorthChange() {

        return netWorthChange;
    }


    public BigDecimal
    getBaselineAfterTaxEstate() {

        return baselineAfterTaxEstate;
    }


    public BigDecimal
    getCurrentAfterTaxEstate() {

        return currentAfterTaxEstate;
    }


    public BigDecimal
    getAfterTaxEstateChange() {

        return afterTaxEstateChange;
    }


    public BigDecimal
    getBaselineEffectiveTaxRate() {

        return baselineEffectiveTaxRate;
    }


    public BigDecimal
    getCurrentEffectiveTaxRate() {

        return currentEffectiveTaxRate;
    }


    public BigDecimal
    getEffectiveTaxRateChange() {

        return effectiveTaxRateChange;
    }


    public BigDecimal
    getBaselinePeakInvestableAssets() {

        return baselinePeakInvestableAssets;
    }


    public BigDecimal
    getCurrentPeakInvestableAssets() {

        return currentPeakInvestableAssets;
    }


    public BigDecimal
    getPeakInvestableAssetsChange() {

        return peakInvestableAssetsChange;
    }
}