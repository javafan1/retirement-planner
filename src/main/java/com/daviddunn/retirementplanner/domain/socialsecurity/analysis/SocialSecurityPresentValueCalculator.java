package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Monthly real-dollar conversion and present-value calculations. */
public final class SocialSecurityPresentValueCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal toBaseDateRealAmount(
            BigDecimal nominalAmount,
            BigDecimal annualColaRate,
            YearMonth baseMonth,
            YearMonth paymentMonth) {

        requireAmount(nominalAmount, "Nominal amount");
        validateRate(annualColaRate, "Social Security COLA rate");
        Objects.requireNonNull(baseMonth, "Base month is required.");
        Objects.requireNonNull(paymentMonth, "Payment month is required.");

        int calendarYears = paymentMonth.getYear() - baseMonth.getYear();
        BigDecimal factor = integerGrowthFactor(
                annualColaRate,
                calendarYears);

        return nominalAmount.divide(factor, MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal presentValue(
            BigDecimal realAmount,
            BigDecimal annualRealDiscountRate,
            YearMonth baseMonth,
            YearMonth paymentMonth) {

        requireAmount(realAmount, "Real amount");
        validateRate(annualRealDiscountRate, "Real discount rate");
        Objects.requireNonNull(baseMonth, "Base month is required.");
        Objects.requireNonNull(paymentMonth, "Payment month is required.");

        long months = ChronoUnit.MONTHS.between(baseMonth, paymentMonth);
        double annualBase = BigDecimal.ONE
                .add(annualRealDiscountRate)
                .doubleValue();
        double factorValue = StrictMath.pow(
                annualBase,
                months / 12.0d);

        if (!Double.isFinite(factorValue) || factorValue <= 0.0d) {
            throw new IllegalArgumentException(
                    "Discount factor must be finite and positive.");
        }

        BigDecimal factor = BigDecimal.valueOf(factorValue);
        return realAmount.divide(factor, MATH_CONTEXT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal integerGrowthFactor(
            BigDecimal annualRate,
            int years) {

        BigDecimal annualFactor = BigDecimal.ONE.add(annualRate);
        if (years >= 0) {
            return annualFactor.pow(years, MATH_CONTEXT);
        }
        return BigDecimal.ONE.divide(
                annualFactor.pow(-years, MATH_CONTEXT),
                MATH_CONTEXT);
    }

    private void validateRate(BigDecimal rate, String description) {

        Objects.requireNonNull(rate, description + " is required.");
        if (rate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    description + " must be greater than -100%.");
        }
    }

    private void requireAmount(BigDecimal amount, String description) {

        Objects.requireNonNull(amount, description + " is required.");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }
    }
}
