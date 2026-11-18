package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import java.math.BigDecimal;

/** Isolated 2026 preparation. No production route or analysis reference key. */
public final class ArkansasTaxRules {
    public static final String VERSION = "AR-2026-PREPARATORY-1";
    public static final BigDecimal STANDARD_DEDUCTION = new BigDecimal("2470");
    public static final BigDecimal RETIREMENT_LIMIT = new BigDecimal("6000");
    public static final BigDecimal PERSONAL_CREDIT = new BigDecimal("29");

    private ArkansasTaxRules() { }

    public static boolean productionAdmitted() { return false; }

    public static void requirePublishedYear(int year) {
        if (year != 2026) throw new IllegalArgumentException("Only reviewed 2026 Arkansas worksheets are available; future CPI amounts need review.");
    }
}
