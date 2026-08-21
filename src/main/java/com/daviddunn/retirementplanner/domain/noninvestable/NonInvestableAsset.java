package com.daviddunn.retirementplanner.domain.noninvestable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public class NonInvestableAsset {

    private String name;
    private BigDecimal currentValue;
    private BigDecimal annualGrowthRate;

    @JsonCreator
    public NonInvestableAsset(
            @JsonProperty("name")
            String name,

            @JsonProperty("currentValue")
            BigDecimal currentValue,

            @JsonProperty("annualGrowthRate")
            BigDecimal annualGrowthRate) {

        this.name =
                Objects.requireNonNull(
                        name,
                        "Asset name is required.");

        this.currentValue =
                Objects.requireNonNull(
                        currentValue,
                        "Current value is required.");

        this.annualGrowthRate =
                Objects.requireNonNull(
                        annualGrowthRate,
                        "Annual growth rate is required.");
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public BigDecimal getAnnualGrowthRate() {
        return annualGrowthRate;
    }

    public void setName(String name) {

        this.name =
                Objects.requireNonNull(
                        name,
                        "Asset name is required.");
    }

    public void setCurrentValue(
            BigDecimal currentValue) {

        this.currentValue =
                Objects.requireNonNull(
                        currentValue,
                        "Current value is required.");
    }

    public void setAnnualGrowthRate(
            BigDecimal annualGrowthRate) {

        this.annualGrowthRate =
                Objects.requireNonNull(
                        annualGrowthRate,
                        "Annual growth rate is required.");
    }

    @Override
    public String toString() {

        return "NonInvestableAsset{" +
                "name='" + name + '\'' +
                ", currentValue=" + currentValue +
                ", annualGrowthRate=" + annualGrowthRate +
                '}';
    }
}