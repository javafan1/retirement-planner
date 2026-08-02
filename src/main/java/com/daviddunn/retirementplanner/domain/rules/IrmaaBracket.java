package com.daviddunn.retirementplanner.domain.rules;

import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class IrmaaBracket {

    private final FilingStatus filingStatus;

    private final BigDecimal minimumModifiedAdjustedGrossIncome;

    private final BigDecimal maximumModifiedAdjustedGrossIncome;

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
                minimumModifiedAdjustedGrossIncome) < 0) {

            throw new IllegalArgumentException(
                    "Maximum modified adjusted gross income cannot be less than the minimum.");
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

    public BigDecimal getMonthlyPartDPremium() {
        return monthlyPartDPremium;
    }

    public boolean contains(
            BigDecimal modifiedAdjustedGrossIncome) {

        Objects.requireNonNull(
                modifiedAdjustedGrossIncome,
                "Modified adjusted gross income is required.");

        if (modifiedAdjustedGrossIncome.compareTo(
                minimumModifiedAdjustedGrossIncome) < 0) {

            return false;
        }

        return maximumModifiedAdjustedGrossIncome == null
                || modifiedAdjustedGrossIncome.compareTo(
                maximumModifiedAdjustedGrossIncome) <= 0;
    }

    @Override
    public String toString() {

        return "IrmaaBracket{" +
                "filingStatus=" + filingStatus +
                ", minimumModifiedAdjustedGrossIncome=" + minimumModifiedAdjustedGrossIncome +
                ", maximumModifiedAdjustedGrossIncome=" + maximumModifiedAdjustedGrossIncome +
                ", monthlyPartBPremium=" + monthlyPartBPremium +
                ", monthlyPartDPremium=" + monthlyPartDPremium +
                '}';
    }

    public String getDisplayRange() {

        if (maximumModifiedAdjustedGrossIncome == null) {
            return "Over " + UIFormatters.money(
                    minimumModifiedAdjustedGrossIncome);
        }

        if (minimumModifiedAdjustedGrossIncome.compareTo(BigDecimal.ZERO) == 0) {
            return "Up to " + UIFormatters.money(
                    maximumModifiedAdjustedGrossIncome);
        }

        return UIFormatters.money(minimumModifiedAdjustedGrossIncome)
                + " - "
                + UIFormatters.money(maximumModifiedAdjustedGrossIncome);
    }
}