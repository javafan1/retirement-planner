package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import java.math.BigDecimal;

/** Isolated verified 2026 parameters. Not a production registration or a future-law forecast. */
public final class ColoradoTaxRules {
    public static final String VERSION = "CO-2026-PREPARATORY-1";
    public static final String REFERENCE_KEY = "colorado-income-tax-rules";

    public boolean productionAdmitted() { return false; }

    public BigDecimal rate(int year) {
        requireVerifiedYear(year);
        return new BigDecimal("0.044");
    }

    public static void requireVerifiedYear(int year) {
        if (year != 2026) throw new IllegalArgumentException(
                "Colorado future schedule requires official election certification/effective-law review; no rate inferred.");
    }
}
