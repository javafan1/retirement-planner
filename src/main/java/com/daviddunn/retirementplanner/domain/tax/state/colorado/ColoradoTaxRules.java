package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;

/** Isolated verified 2026 parameters. Not a production registration or a future-law forecast. */
public final class ColoradoTaxRules {
    public static final String VERSION = "CO-2026-PREPARATORY-2";
    public static final String REFERENCE_KEY = "colorado-income-tax-rules";

    public boolean productionAdmitted() { return false; }

    public BigDecimal rate(int year) {
        requireVerifiedYear(year);
        return new BigDecimal("0.044");
    }

    /** DR 0104EP 2026; deliberately refuses an unresolved future schedule. */
    public BigDecimal tax(int year, BigDecimal reconciledTaxableIncome) {
        BigDecimal rate = rate(year);
        if (Objects.requireNonNull(reconciledTaxableIncome).signum() < 0) {
            throw new IllegalArgumentException("Reconciled Colorado taxable income cannot be negative.");
        }
        return reconciledTaxableIncome.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Published IRS RP 2025-32 / RP 2026-35 amounts; not a future inflation forecast. */
    public BigDecimal basicDeduction(int year, FilingStatus status) {
        requirePublishedDeductionYear(year);
        boolean joint = isJoint(status);
        return new BigDecimal(year == 2026 ? joint ? "32200" : "16100" : joint ? "33200" : "16600");
    }

    public BigDecimal additionalAgeDeduction(int year, FilingStatus status) {
        requirePublishedDeductionYear(year);
        boolean joint = isJoint(status);
        return new BigDecimal(year == 2026 ? joint ? "1650" : "2050" : joint ? "1700" : "2100");
    }

    /** IRC 151 temporary deduction, ordinary domestic MAGI = AGI subset only. */
    public BigDecimal enhancedSeniorDeduction(int year, FilingStatus status, BigDecimal agi, int seniors) {
        boolean joint = isJoint(status);
        if (year < 2026 || Objects.requireNonNull(agi).signum() < 0
                || seniors < 0 || seniors > (joint ? 2 : 1)) {
            throw new IllegalArgumentException("Invalid Colorado senior-deduction worksheet facts.");
        }
        if (year > 2028) return BigDecimal.ZERO;
        BigDecimal threshold = new BigDecimal(joint ? "150000" : "75000");
        return new BigDecimal("6000").subtract(agi.subtract(threshold).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("0.06"))).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(seniors));
    }

    private static boolean isJoint(FilingStatus status) {
        if (Objects.requireNonNull(status) != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY) {
            throw new IllegalArgumentException("Colorado worksheet supports Single/MFJ only.");
        }
        return status == FilingStatus.MARRIED_FILING_JOINTLY;
    }

    private static void requirePublishedDeductionYear(int year) {
        if (year != 2026 && year != 2027) {
            throw new IllegalArgumentException("Colorado bridge needs reviewed published or approved projected federal deductions for this year.");
        }
    }

    public static void requireVerifiedYear(int year) {
        if (year != 2026) throw new IllegalArgumentException(
                "Colorado future schedule requires official election certification/effective-law review; no rate inferred.");
    }
}
