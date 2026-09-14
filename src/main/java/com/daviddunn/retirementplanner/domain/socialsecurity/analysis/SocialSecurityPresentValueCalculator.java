package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Monthly real/PV values retain DECIMAL128 precision; currency rounding belongs to display. */
public final class SocialSecurityPresentValueCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final MathContext WORK = new MathContext(40, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal ELEVEN = new BigDecimal("11");
    private volatile MonthlyFactor cachedFactor;

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

        return nominalAmount.divide(factor, MATH_CONTEXT);
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
        BigDecimal annualBase = BigDecimal.ONE.add(annualRealDiscountRate);
        long absoluteMonths = Math.abs(months);
        BigDecimal factor = annualBase.pow(Math.toIntExact(absoluteMonths / 12), WORK);
        int remainder = (int) (absoluteMonths % 12);
        if (remainder != 0) {
            factor = factor.multiply(monthlyFactor(annualBase).pow(remainder, WORK), WORK);
        }
        return months < 0
                ? realAmount.multiply(factor, MATH_CONTEXT)
                : realAmount.divide(factor, MATH_CONTEXT);
    }

    private BigDecimal monthlyFactor(BigDecimal annualBase) {
        MonthlyFactor cached = cachedFactor;
        if (cached != null && cached.annualBase().compareTo(annualBase) == 0) {
            return cached.factor();
        }
        // Newton's method for the positive twelfth root, with six guard digits.
        // A decimal magnitude estimate avoids binary floating point, even for extreme rates.
        int magnitude = annualBase.precision() - annualBase.scale();
        BigDecimal root = BigDecimal.ONE.scaleByPowerOfTen(Math.floorDiv(magnitude, 12) + 1);
        BigDecimal tolerance = new BigDecimal("1E-39");
        while (true) {
            BigDecimal next = root.multiply(ELEVEN, WORK)
                    .add(annualBase.divide(root.pow(11, WORK), WORK), WORK)
                    .divide(TWELVE, WORK);
            if (next.subtract(root).abs().compareTo(next.abs().multiply(tolerance)) <= 0) {
                cachedFactor = new MonthlyFactor(annualBase, next);
                return next;
            }
            root = next;
        }
    }

    private record MonthlyFactor(BigDecimal annualBase, BigDecimal factor) { }

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
