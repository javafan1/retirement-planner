package com.daviddunn.retirementplanner.domain.tax.state.northcarolina;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable reviewed law, verified 2026-11-16. G.S. 105-153.5(a)(1),(b)(3);
 * S.L. 2026-41 section 44.1 (printed page 607).
 * https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-41.pdf
 * https://www.ncdor.gov/individual-estimated-income-tax/open
 * Conditional 2035-2040 revenue triggers are deliberately not forecast.
 * Change VERSION whenever reviewed calculation rules or admission semantics change.
 */
public final class NorthCarolinaTaxRules {
    public static final String VERSION = "NC-2026-1";
    public static final String REFERENCE_KEY = "north-carolina-income-tax-rules";
    private static final BigDecimal SINGLE = new BigDecimal("12750");
    private static final BigDecimal JOINT = new BigDecimal("25500");
    private static final BigDecimal RATE_2026 = new BigDecimal("0.0399");
    private static final BigDecimal RATE_2027 = new BigDecimal("0.0349");
    private static final BigDecimal RATE_2030 = new BigDecimal("0.0324");
    private static final BigDecimal RATE_2033 = new BigDecimal("0.0299");

    public BigDecimal standardDeduction(FilingStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case SINGLE -> SINGLE;
            case MARRIED_FILING_JOINTLY -> JOINT;
            default -> throw new IllegalArgumentException("North Carolina supports Single/MFJ only.");
        };
    }

    public BigDecimal rate(int year) {
        if (year < 2026) {
            throw new IllegalArgumentException("North Carolina reviewed rules begin in 2026.");
        }
        if (year >= 2033) return RATE_2033;
        if (year >= 2030) return RATE_2030;
        if (year >= 2027) return RATE_2027;
        return RATE_2026;
    }
}
