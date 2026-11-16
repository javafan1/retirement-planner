package com.daviddunn.retirementplanner.domain.tax.state.kentucky;

import java.math.BigDecimal;

/** KRS 141.019/.020/.081; DOR published 2026/2027 deductions. Reviewed 2026-11-16. */
public final class KentuckyTaxRules {
    public static final String VERSION = "KY-2026-1";
    public static final String REFERENCE_KEY = "kentucky-income-tax-rules";
    public static final BigDecimal RETIREMENT_LIMIT = new BigDecimal("31110");
    public static final BigDecimal SENIOR_CREDIT = new BigDecimal("40");
    private static final BigDecimal RATE = new BigDecimal("0.035");

    public BigDecimal rate(int year) {
        requireYear(year);
        return RATE;
    }

    /** One deduction for an actual joint return, not two separate combined-return columns. */
    public BigDecimal standardDeduction(int year) {
        requireYear(year);
        return year == 2026 ? new BigDecimal("3360") : new BigDecimal("3470");
    }

    private static void requireYear(int year) {
        if (year < 2026) throw new IllegalArgumentException("Kentucky rules begin in 2026.");
    }
}
