package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** Compact valuation totals for search paths that do not retain monthly valuations. */
public record SocialSecurityStrategyValuationSummary(
        BigDecimal nominalLifetimeBenefits,
        BigDecimal realLifetimeBenefits,
        BigDecimal presentValue,
        BigDecimal primarySelectedBenefits,
        BigDecimal spouseSelectedBenefits) {

    public SocialSecurityStrategyValuationSummary {
        Objects.requireNonNull(nominalLifetimeBenefits, "Nominal benefits are required.");
        Objects.requireNonNull(realLifetimeBenefits, "Real benefits are required.");
        Objects.requireNonNull(presentValue, "Present value is required.");
        Objects.requireNonNull(primarySelectedBenefits, "Primary selected benefits are required.");
        Objects.requireNonNull(spouseSelectedBenefits, "Spouse selected benefits are required.");
    }
}
