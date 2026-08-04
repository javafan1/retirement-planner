package com.daviddunn.retirementplanner.domain.financial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Expense {

    private final String description;

    private final BigDecimal annualAmount;

    private final GrowthCategory growthCategory;

    private final LocalDate startDate;

    private final LocalDate endDate;

    public Expense(
            String description,
            BigDecimal annualAmount) {

        this(
                description,
                annualAmount,
                GrowthCategory.GENERAL,
                null,
                null);
    }

    public Expense(
            String description,
            BigDecimal annualAmount,
            GrowthCategory growthCategory) {

        this(
                description,
                annualAmount,
                growthCategory,
                null,
                null);
    }

    @JsonCreator
    public Expense(

            @JsonProperty("description")
            String description,

            @JsonProperty("annualAmount")
            BigDecimal annualAmount,

            @JsonProperty("growthCategory")
            GrowthCategory growthCategory,

            @JsonProperty("startDate")
            LocalDate startDate,

            @JsonProperty("endDate")
            LocalDate endDate) {

        this.description =
                Objects.requireNonNull(
                        description,
                        "Description is required.");

        this.annualAmount =
                Objects.requireNonNull(
                        annualAmount,
                        "Annual amount is required.");

        this.growthCategory =
                growthCategory == null
                        ? GrowthCategory.GENERAL
                        : growthCategory;

        this.startDate =
                startDate;

        this.endDate =
                endDate;

        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {

            throw new IllegalArgumentException(
                    "Expense end date cannot be before the start date.");
        }
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public GrowthCategory getGrowthCategory() {
        return growthCategory;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isHealthcareExpense() {

        return growthCategory ==
                GrowthCategory.HEALTHCARE;
    }

    public boolean isActive(
            LocalDate projectionDate) {

        Objects.requireNonNull(
                projectionDate,
                "Projection date is required.");

        if (startDate != null &&
                projectionDate.isBefore(startDate)) {

            return false;
        }

        if (endDate != null &&
                projectionDate.isAfter(endDate)) {

            return false;
        }

        return true;
    }

    public boolean isAlwaysActive() {

        return startDate == null &&
                endDate == null;
    }
}