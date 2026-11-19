package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Isolated 2026 preparation. No production route or analysis reference key. */
public final class ArkansasTaxRules {
    public static final String VERSION = "AR-2026-P2-1";
    public static final int REVIEWED_YEAR = 2026;
    public static final BigDecimal STANDARD_DEDUCTION = new BigDecimal("2470");
    public static final BigDecimal RETIREMENT_LIMIT = new BigDecimal("6000");
    public static final BigDecimal PERSONAL_CREDIT = new BigDecimal("29");

    // Eligibility ceilings for refusal, never the published threshold or a modeled credit table.
    // §26-51-301(c),(e) adjusts the statutory 2010 upper limits by at most 3% a year.
    private static final BigDecimal MAXIMUM_INDEX_FACTOR = new BigDecimal("1.03");
    private static final BigDecimal PUBLISHED_2025_CREDIT_ENDPOINT = new BigDecimal("27600");
    private static final int LAST_PROOF_YEAR = 2426;
    // §26-51-501(a)(6)(C) uses §26-51-201(d): the published 2025 $27,600 upper
    // endpoint can gain at most 3% in 2026, with rounding to $100. Round outward.
    public static final BigDecimal ADDITIONAL_CREDIT_NET_CEILING = PUBLISHED_2025_CREDIT_ENDPOINT
            .multiply(MAXIMUM_INDEX_FACTOR).setScale(-2, RoundingMode.CEILING);

    private ArkansasTaxRules() { }

    public static boolean productionAdmitted() { return false; }

    public static int reviewedYearFor(int year) {
        if (year < REVIEWED_YEAR) throw new IllegalArgumentException("Arkansas years before 2026 are outside the reviewed planning policy.");
        return REVIEWED_YEAR;
    }

    public static void requirePublishedYear(int year) {
        if (year != REVIEWED_YEAR) throw new IllegalArgumentException("Only reviewed 2026 Arkansas worksheets are available; future CPI amounts need review.");
    }

    public static BigDecimal lowIncomeGrossCeiling(boolean joint) {
        return lowIncomeGrossCeiling(REVIEWED_YEAR, joint);
    }

    public static BigDecimal lowIncomeGrossCeiling(int taxYear, boolean joint) {
        requireProofYear(taxYear);
        BigDecimal amount = new BigDecimal(joint ? "22400" : "14000");
        for (int year = 2010; year <= taxYear; year++) {
            amount = amount.multiply(MAXIMUM_INDEX_FACTOR).setScale(0, RoundingMode.CEILING);
        }
        return amount;
    }

    public static BigDecimal additionalCreditNetCeiling(int taxYear) {
        requireProofYear(taxYear);
        BigDecimal endpoint = PUBLISHED_2025_CREDIT_ENDPOINT;
        for (int year = 2026; year <= taxYear; year++) {
            // Nearest hundred with upward ties bounds either statutory tie convention.
            endpoint = endpoint.multiply(MAXIMUM_INDEX_FACTOR).setScale(-2, RoundingMode.HALF_UP);
        }
        return endpoint.max(ADDITIONAL_CREDIT_NET_CEILING);
    }

    private static void requireProofYear(int taxYear) {
        if (!supportsLowIncomeProof(taxYear)) {
            throw new IllegalArgumentException("Arkansas low-income admission proof is unavailable for this year.");
        }
    }

    public static boolean supportsLowIncomeProof(int taxYear) {
        return taxYear >= REVIEWED_YEAR && taxYear <= LAST_PROOF_YEAR;
    }
}
