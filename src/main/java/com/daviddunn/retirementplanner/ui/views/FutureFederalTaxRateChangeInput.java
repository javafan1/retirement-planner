package com.daviddunn.retirementplanner.ui.views;

import java.math.BigDecimal;
import java.math.RoundingMode;

record FutureFederalTaxRateChangeInput(
        BigDecimal adjustment,
        Integer effectiveYear) {

    static FutureFederalTaxRateChangeInput parse(
            String adjustmentText,
            String effectiveYearText) {

        String adjustmentValue =
                normalize(adjustmentText)
                        .replace("%", "");

        String yearValue =
                normalize(effectiveYearText);

        if (adjustmentValue.isEmpty()
                && yearValue.isEmpty()) {

            return new FutureFederalTaxRateChangeInput(
                    null,
                    null);
        }

        if (adjustmentValue.isEmpty()
                || yearValue.isEmpty()) {

            throw new IllegalArgumentException(
                    "Enter both the federal tax rate change percent and effective year, or clear both.");
        }

        try {

            BigDecimal adjustment =
                    new BigDecimal(adjustmentValue)
                            .divide(
                                    new BigDecimal("100"),
                                    8,
                                    RoundingMode.HALF_UP);

            Integer effectiveYear =
                    Integer.valueOf(yearValue);

            return new FutureFederalTaxRateChangeInput(
                    adjustment,
                    effectiveYear);

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Federal tax rate change percent and effective year must be valid numbers.",
                    ex);
        }
    }

    private static String normalize(
            String value) {

        return value == null
                ? ""
                : value.trim();
    }
}
