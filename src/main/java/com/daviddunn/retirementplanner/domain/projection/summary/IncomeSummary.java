package com.daviddunn.retirementplanner.domain.projection.summary;

import java.math.BigDecimal;
import java.util.Objects;

public final class IncomeSummary {

    private final BigDecimal primarySocialSecurityMonthly;
    private final BigDecimal spouseSocialSecurityMonthly;

    private final BigDecimal primaryPensionMonthly;
    private final BigDecimal spousePensionMonthly;

    private final BigDecimal totalGuaranteedMonthlyIncome;

    public IncomeSummary(
            BigDecimal primarySocialSecurityMonthly,
            BigDecimal spouseSocialSecurityMonthly,
            BigDecimal primaryPensionMonthly,
            BigDecimal spousePensionMonthly) {

        this.primarySocialSecurityMonthly =
                Objects.requireNonNull(primarySocialSecurityMonthly);

        this.spouseSocialSecurityMonthly =
                Objects.requireNonNull(spouseSocialSecurityMonthly);

        this.primaryPensionMonthly =
                Objects.requireNonNull(primaryPensionMonthly);

        this.spousePensionMonthly =
                Objects.requireNonNull(spousePensionMonthly);

        this.totalGuaranteedMonthlyIncome =
                primarySocialSecurityMonthly
                        .add(spouseSocialSecurityMonthly)
                        .add(primaryPensionMonthly)
                        .add(spousePensionMonthly);
    }

    public BigDecimal getPrimarySocialSecurityMonthly() {
        return primarySocialSecurityMonthly;
    }

    public BigDecimal getSpouseSocialSecurityMonthly() {
        return spouseSocialSecurityMonthly;
    }

    public BigDecimal getPrimaryPensionMonthly() {
        return primaryPensionMonthly;
    }

    public BigDecimal getSpousePensionMonthly() {
        return spousePensionMonthly;
    }

    public BigDecimal getTotalGuaranteedMonthlyIncome() {
        return totalGuaranteedMonthlyIncome;
    }
}