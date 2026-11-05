package com.daviddunn.retirementplanner.domain.socialsecurity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable calendar-year policy for transforming a scheduled payable benefit. */
public record SocialSecurityBenefitReductionPolicy(
        BigDecimal reductionPercent,
        int effectiveYear) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final SocialSecurityBenefitReductionPolicy NONE =
            new SocialSecurityBenefitReductionPolicy(BigDecimal.ZERO, 2032);

    public SocialSecurityBenefitReductionPolicy {
        Objects.requireNonNull(reductionPercent, "Reduction percentage is required.");
        if (reductionPercent.compareTo(BigDecimal.ZERO) < 0
                || reductionPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Social Security benefit reduction must be between 0 and 100 percent.");
        }
        if (effectiveYear < 1000 || effectiveYear > 9999) {
            throw new IllegalArgumentException("Effective year must be between 1000 and 9999.");
        }
        // Cache keys use mathematical percentage equivalence, not input scale.
        reductionPercent = reductionPercent.stripTrailingZeros();
    }

    public static SocialSecurityBenefitReductionPolicy none() {
        return NONE;
    }

    public boolean isEffective(int paymentCalendarYear) {
        return reductionPercent.signum() != 0 && paymentCalendarYear >= effectiveYear;
    }

    /** Returns the original scheduled amount unchanged when the policy is inactive. */
    public BigDecimal reduce(BigDecimal scheduledBenefit, int paymentCalendarYear) {
        Objects.requireNonNull(scheduledBenefit, "Scheduled benefit is required.");
        if (scheduledBenefit.signum() < 0) {
            throw new IllegalArgumentException("Scheduled benefit cannot be negative.");
        }
        if (!isEffective(paymentCalendarYear) || scheduledBenefit.signum() == 0) {
            return scheduledBenefit;
        }
        BigDecimal payableFactor = BigDecimal.ONE.subtract(reductionPercent.movePointLeft(2));
        return scheduledBenefit.multiply(payableFactor).setScale(2, RoundingMode.HALF_UP);
    }
}
