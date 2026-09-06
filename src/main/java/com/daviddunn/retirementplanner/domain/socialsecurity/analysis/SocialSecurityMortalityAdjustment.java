package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Constant proportional-hazard scenario adjustment for annual mortality qx.
 * Fractional exponentiation is deliberately isolated here. This is a planning
 * assumption, not a medical or individualized actuarial estimate.
 */
public record SocialSecurityMortalityAdjustment(BigDecimal factor) {

    private static final BigDecimal ONE = BigDecimal.ONE;

    public SocialSecurityMortalityAdjustment {
        Objects.requireNonNull(factor, "Mortality adjustment factor is required.");
        if (factor.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Mortality adjustment factor must be greater than zero.");
        }
        double exponent = factor.doubleValue();
        if (!Double.isFinite(exponent) || exponent == 0.0d) {
            throw new IllegalArgumentException(
                    "Mortality adjustment factor must be representable as a finite positive exponent.");
        }
    }

    public static SocialSecurityMortalityAdjustment standard() {
        return new SocialSecurityMortalityAdjustment(ONE);
    }

    public static SocialSecurityMortalityAdjustment of(BigDecimal factor) {
        return new SocialSecurityMortalityAdjustment(factor);
    }

    /** Returns 1 - (1 - qx)^factor, preserving exact identity and boundaries. */
    public BigDecimal adjust(BigDecimal qx) {
        Objects.requireNonNull(qx, "Mortality qx is required.");
        if (qx.compareTo(BigDecimal.ZERO) < 0 || qx.compareTo(ONE) > 0) {
            throw new IllegalArgumentException("Mortality qx must be between 0 and 1.");
        }
        if (factor.compareTo(ONE) == 0 || qx.signum() == 0 || qx.compareTo(ONE) == 0) {
            return qx;
        }

        // StrictMath is the only floating-point boundary in the mortality layer.
        double adjusted = -StrictMath.expm1(
                factor.doubleValue() * StrictMath.log1p(-qx.doubleValue()));
        if (!Double.isFinite(adjusted)) {
            throw new IllegalArgumentException("Unable to calculate adjusted mortality qx.");
        }
        // Clamp only floating-point boundary noise; valid inputs are mathematically bounded.
        adjusted = Math.max(0.0d, Math.min(1.0d, adjusted));
        return BigDecimal.valueOf(adjusted);
    }
}
