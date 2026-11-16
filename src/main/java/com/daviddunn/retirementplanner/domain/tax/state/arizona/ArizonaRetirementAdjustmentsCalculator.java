package com.daviddunn.retirementplanner.domain.tax.state.arizona;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Arizona retirement adjustments reused by the production adapter; this component
 * does not establish residency, age, dependency or SSN eligibility itself.
 *
 * Sources (verified 2026-11-15): ARS 43-1022(10),(35), ARS 43-1023(E),
 * IRC 151(d)(5)(C), and IRS Schedule 1-A Part V lines 31-37.
 */
public final class ArizonaRetirementAdjustmentsCalculator {
    private static final BigDecimal AGE_EXEMPTION = new BigDecimal("2100");
    private static final BigDecimal ENHANCED_SENIOR_AMOUNT = new BigDecimal("6000");
    private static final BigDecimal PHASEOUT_RATE = new BigDecimal("0.06");
    private static final BigDecimal SINGLE_THRESHOLD = new BigDecimal("75000");
    private static final BigDecimal JOINT_THRESHOLD = new BigDecimal("150000");

    /**
     * Eligible filers must already satisfy the respective age, dependency and SSN
     * requirements. This bounded component assumes domestic MAGI equals federal AGI
     * and that none of the enhanced subtraction was already excluded from AGI.
     * It deliberately accepts no unverified standard deduction or final tax rate.
     */
    public Adjustments calculate(
            int taxYear,
            FilingStatus filingStatus,
            BigDecimal federalAdjustedGrossIncome,
            BigDecimal federallyTaxableSocialSecurity,
            int eligibleSeniorFilers) {
        Objects.requireNonNull(filingStatus, "Filing status is required.");
        requireNonnegative(federalAdjustedGrossIncome);
        requireNonnegative(federallyTaxableSocialSecurity);
        if (taxYear < 2026) {
            throw new IllegalArgumentException("Arizona adjustment review begins in 2026.");
        }
        int filers = switch (filingStatus) {
            case SINGLE -> 1;
            case MARRIED_FILING_JOINTLY -> 2;
            default -> throw new IllegalArgumentException("Arizona adjustment scope is Single/MFJ only.");
        };
        if (eligibleSeniorFilers < 0 || eligibleSeniorFilers > filers) {
            throw new IllegalArgumentException("Invalid eligible senior count for filing status.");
        }
        if (federallyTaxableSocialSecurity.compareTo(federalAdjustedGrossIncome) > 0) {
            throw new IllegalArgumentException("Taxable Social Security exceeds modeled federal AGI.");
        }
        BigDecimal count = BigDecimal.valueOf(eligibleSeniorFilers);
        BigDecimal ageExemption = AGE_EXEMPTION.multiply(count);
        BigDecimal enhanced = BigDecimal.ZERO;
        if (taxYear <= 2028) {
            BigDecimal threshold = filingStatus == FilingStatus.SINGLE
                    ? SINGLE_THRESHOLD : JOINT_THRESHOLD;
            BigDecimal reduction = federalAdjustedGrossIncome.subtract(threshold)
                    .max(BigDecimal.ZERO).multiply(PHASEOUT_RATE);
            enhanced = ENHANCED_SENIOR_AMOUNT.subtract(reduction)
                    .max(BigDecimal.ZERO).multiply(count);
        }
        BigDecimal signedIncome = federalAdjustedGrossIncome
                .subtract(federallyTaxableSocialSecurity)
                .subtract(ageExemption)
                .subtract(enhanced);
        return new Adjustments(federallyTaxableSocialSecurity, ageExemption, enhanced, signedIncome);
    }

    private static void requireNonnegative(BigDecimal amount) {
        if (Objects.requireNonNull(amount, "Income amount is required.").signum() < 0) {
            throw new IllegalArgumentException("Income amount must not be negative.");
        }
    }

    /** Signed intermediate income is intentionally not final taxable income or tax. */
    public record Adjustments(
            BigDecimal socialSecuritySubtraction,
            BigDecimal ageExemption,
            BigDecimal enhancedSeniorSubtraction,
            BigDecimal signedAdjustedIncome) {
        public Adjustments {
            requireNonnegative(socialSecuritySubtraction);
            requireNonnegative(ageExemption);
            requireNonnegative(enhancedSeniorSubtraction);
            Objects.requireNonNull(signedAdjustedIncome);
        }
    }
}
