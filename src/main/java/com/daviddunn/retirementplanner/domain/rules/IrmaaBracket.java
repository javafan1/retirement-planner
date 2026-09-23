package com.daviddunn.retirementplanner.domain.rules;

import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class IrmaaBracket {

    private final FilingStatus filingStatus;

    private final BigDecimal minimumModifiedAdjustedGrossIncome;

    private final BigDecimal maximumModifiedAdjustedGrossIncome;

    private final boolean minimumIncomeInclusive;

    private final boolean maximumIncomeInclusive;

    private final BigDecimal monthlyPartBPremium;

    private final BigDecimal monthlyPartDPremium;

    @JsonCreator
    public IrmaaBracket(

            @JsonProperty("filingStatus")
            FilingStatus filingStatus,

            @JsonProperty("minimumModifiedAdjustedGrossIncome")
            BigDecimal minimumModifiedAdjustedGrossIncome,

            @JsonProperty("maximumModifiedAdjustedGrossIncome")
            BigDecimal maximumModifiedAdjustedGrossIncome,

            @JsonProperty("minimumIncomeInclusive")
            Boolean minimumIncomeInclusive,

            @JsonProperty("maximumIncomeInclusive")
            Boolean maximumIncomeInclusive,

            @JsonProperty("monthlyPartBPremium")
            BigDecimal monthlyPartBPremium,

            @JsonProperty("monthlyPartDPremium")
            BigDecimal monthlyPartDPremium) {

        this.filingStatus =
                Objects.requireNonNull(
                        filingStatus,
                        "Filing status is required.");

        this.minimumModifiedAdjustedGrossIncome =
                Objects.requireNonNull(
                        minimumModifiedAdjustedGrossIncome,
                        "Minimum modified adjusted gross income is required.");

        this.maximumModifiedAdjustedGrossIncome =
                maximumModifiedAdjustedGrossIncome;

        this.minimumIncomeInclusive =
                Objects.requireNonNull(
                        minimumIncomeInclusive,
                        "Minimum income inclusion is required.");

        this.maximumIncomeInclusive =
                Objects.requireNonNull(
                        maximumIncomeInclusive,
                        "Maximum income inclusion is required.");

        this.monthlyPartBPremium =
                Objects.requireNonNull(
                        monthlyPartBPremium,
                        "Monthly Part B premium is required.");

        this.monthlyPartDPremium =
                Objects.requireNonNull(
                        monthlyPartDPremium,
                        "Monthly Part D premium is required.");

        if (minimumModifiedAdjustedGrossIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Minimum modified adjusted gross income cannot be negative.");
        }

        if (maximumModifiedAdjustedGrossIncome != null
                && maximumModifiedAdjustedGrossIncome.compareTo(
                minimumModifiedAdjustedGrossIncome) <= 0) {

            throw new IllegalArgumentException(
                    "Maximum modified adjusted gross income must exceed the minimum.");
        }

        if (maximumModifiedAdjustedGrossIncome == null && maximumIncomeInclusive) {
            throw new IllegalArgumentException(
                    "An unbounded maximum cannot be inclusive.");
        }
    }

    public FilingStatus getFilingStatus() {
        return filingStatus;
    }

    public BigDecimal getMinimumModifiedAdjustedGrossIncome() {
        return minimumModifiedAdjustedGrossIncome;
    }

    public BigDecimal getMaximumModifiedAdjustedGrossIncome() {
        return maximumModifiedAdjustedGrossIncome;
    }

    public BigDecimal getMonthlyPartBPremium() {
        return monthlyPartBPremium;
    }

    public boolean isMinimumIncomeInclusive() {
        return minimumIncomeInclusive;
    }

    public boolean isMaximumIncomeInclusive() {
        return maximumIncomeInclusive;
    }

    public BigDecimal getMonthlyPartDPremium() {
        return monthlyPartDPremium;
    }

    public boolean contains(
            BigDecimal modifiedAdjustedGrossIncome) {

        Objects.requireNonNull(
                modifiedAdjustedGrossIncome,
                "Modified adjusted gross income is required.");

        int minimumComparison = modifiedAdjustedGrossIncome.compareTo(
                minimumModifiedAdjustedGrossIncome);

        if (minimumComparison < 0 || (minimumComparison == 0 && !minimumIncomeInclusive)) {

            return false;
        }

        if (maximumModifiedAdjustedGrossIncome == null) {
            return true;
        }

        int maximumComparison = modifiedAdjustedGrossIncome.compareTo(
                maximumModifiedAdjustedGrossIncome);

        return maximumComparison < 0 || (maximumComparison == 0 && maximumIncomeInclusive);
    }

    @Override
    public String toString() {

        return "IrmaaBracket{" +
                "filingStatus=" + filingStatus +
                ", minimumModifiedAdjustedGrossIncome=" + minimumModifiedAdjustedGrossIncome +
                ", maximumModifiedAdjustedGrossIncome=" + maximumModifiedAdjustedGrossIncome +
                ", minimumIncomeInclusive=" + minimumIncomeInclusive +
                ", maximumIncomeInclusive=" + maximumIncomeInclusive +
                ", monthlyPartBPremium=" + monthlyPartBPremium +
                ", monthlyPartDPremium=" + monthlyPartDPremium +
                '}';
    }

    @JsonIgnore
    public String getDisplayRange() {

        if (maximumModifiedAdjustedGrossIncome == null) {
            return (minimumIncomeInclusive ? "At least " : "Over ") + UIFormatters.money(
                    minimumModifiedAdjustedGrossIncome);
        }

        if (minimumModifiedAdjustedGrossIncome.signum() == 0 && minimumIncomeInclusive) {
            return (maximumIncomeInclusive ? "Up to " : "Under ") + UIFormatters.money(
                    maximumModifiedAdjustedGrossIncome);
        }

        return (minimumIncomeInclusive ? "At least " : "Over ")
                + UIFormatters.money(minimumModifiedAdjustedGrossIncome)
                + (maximumIncomeInclusive ? " through " : " and under ")
                + UIFormatters.money(maximumModifiedAdjustedGrossIncome);
    }
}
