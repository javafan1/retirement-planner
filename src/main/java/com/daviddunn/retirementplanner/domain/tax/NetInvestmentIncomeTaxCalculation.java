package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Bounded domestic standard-deduction evidence, not a complete Form 8960. */
public record NetInvestmentIncomeTaxCalculation(String model, FilingStatus filingStatus,
        BigDecimal magi, BigDecimal taxableAnnuities, BigDecimal taxableInterest,
        BigDecimal deductions, BigDecimal magiAdjustment, BigDecimal threshold, BigDecimal rate, BigDecimal tax) {
    public static final String MODEL = "DOMESTIC_RECOGNIZED_INVESTMENT_INCOME_1411_V1";
    public NetInvestmentIncomeTaxCalculation {
        Objects.requireNonNull(filingStatus);
        for (var value : new BigDecimal[]{magi, taxableAnnuities, taxableInterest, deductions, magiAdjustment, threshold, rate, tax}) {
            Objects.requireNonNull(value);
            if (value.signum() < 0 || value.precision() > 4096 || value.scale() < -1024 || value.scale() > 4096)
                throw new IllegalArgumentException("Invalid NIIT amount.");
        }
        if (!MODEL.equals(model) || deductions.signum() != 0 || magiAdjustment.signum() != 0
                || threshold.compareTo(NetInvestmentIncomeTaxRules.threshold(filingStatus)) != 0
                || rate.compareTo(NetInvestmentIncomeTaxRules.rate()) != 0
                || tax.compareTo(taxableAnnuities.add(taxableInterest).min(magi.subtract(threshold).max(BigDecimal.ZERO))
                    .multiply(rate).setScale(2, RoundingMode.HALF_UP)) != 0)
            throw new IllegalArgumentException("Unsupported or inconsistent NIIT evidence.");
    }
}
