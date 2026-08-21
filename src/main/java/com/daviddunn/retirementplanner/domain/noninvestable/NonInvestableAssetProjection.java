package com.daviddunn.retirementplanner.domain.noninvestable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NonInvestableAssetProjection {

    private final int calendarYear;

    private final List<NonInvestableAssetValue>
            assetValues;

    private final BigDecimal totalValue;

    public NonInvestableAssetProjection(
            int calendarYear,
            List<NonInvestableAssetValue> assetValues,
            BigDecimal totalValue) {

        if (calendarYear < 0) {
            throw new IllegalArgumentException(
                    "Calendar year must be positive.");
        }

        this.calendarYear =
                calendarYear;

        this.assetValues =
                List.copyOf(
                        Objects.requireNonNull(
                                assetValues,
                                "Asset values are required."));

        this.totalValue =
                Objects.requireNonNull(
                        totalValue,
                        "Total value is required.");
    }

    public int getCalendarYear() {
        return calendarYear;
    }

    public List<NonInvestableAssetValue>
    getAssetValues() {

        return Collections.unmodifiableList(
                assetValues);
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }
}