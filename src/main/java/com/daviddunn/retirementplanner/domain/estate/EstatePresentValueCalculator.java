package com.daviddunn.retirementplanner.domain.estate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Nominal estate -> valuation-date purchasing power -> real discounted estate, ACT/365.25.
 * Decimal logarithm/exponential series use 40 significant digits internally (34 on output).
 * No binary floating-point financial arithmetic or intermediate cent rounding.
 */
public final class EstatePresentValueCalculator {
    private static final MathContext WORK = new MathContext(40, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal EPSILON = new BigDecimal("1E-41");

    public BigDecimal calculate(BigDecimal nominalEstate, LocalDate valuationDate,
            LocalDate secondDeathDate, BigDecimal generalInflationRate, BigDecimal realDiscountRate) {
        return Objects.requireNonNull(nominalEstate).multiply(discountFactor(
                valuationDate, secondDeathDate, generalInflationRate, realDiscountRate), MathContext.DECIMAL128);
    }

    public BigDecimal discountFactor(LocalDate valuationDate, LocalDate secondDeathDate,
            BigDecimal generalInflationRate, BigDecimal realDiscountRate) {
        Objects.requireNonNull(valuationDate);
        Objects.requireNonNull(secondDeathDate);
        validateRate(generalInflationRate);
        validateRate(realDiscountRate);
        BigDecimal years = BigDecimal.valueOf(ChronoUnit.DAYS.between(valuationDate, secondDeathDate))
                .divide(new BigDecimal("365.25"), WORK);
        BigDecimal log = logarithm(BigDecimal.ONE.add(generalInflationRate))
                .add(logarithm(BigDecimal.ONE.add(realDiscountRate)), WORK);
        return exponential(log.multiply(years, WORK).negate()).round(MathContext.DECIMAL128);
    }

    public static void validateRate(BigDecimal rate) {
        if (Objects.requireNonNull(rate, "Rate is required.").compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException("Compounding rate must be greater than -1.");
        }
    }

    private static BigDecimal logarithm(BigDecimal value) {
        int roots = 0;
        while (value.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.1")) > 0) {
            value = value.sqrt(WORK);
            roots++;
        }
        BigDecimal z = value.subtract(BigDecimal.ONE).divide(value.add(BigDecimal.ONE), WORK);
        BigDecimal zSquared = z.multiply(z, WORK);
        BigDecimal power = z;
        BigDecimal sum = BigDecimal.ZERO;
        for (int denominator = 1; ; denominator += 2) {
            BigDecimal term = power.divide(BigDecimal.valueOf(denominator), WORK);
            sum = sum.add(term, WORK);
            if (term.abs().compareTo(EPSILON) < 0) {
                return sum.multiply(TWO.pow(roots + 1), WORK);
            }
            power = power.multiply(zSquared, WORK);
        }
    }

    private static BigDecimal exponential(BigDecimal exponent) {
        int squares = 0;
        while (exponent.abs().compareTo(new BigDecimal("0.1")) > 0) {
            exponent = exponent.divide(TWO, WORK);
            squares++;
        }
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int n = 1; ; n++) {
            term = term.multiply(exponent, WORK).divide(BigDecimal.valueOf(n), WORK);
            sum = sum.add(term, WORK);
            if (term.abs().compareTo(EPSILON) < 0) {
                break;
            }
        }
        for (int n = 0; n < squares; n++) {
            sum = sum.multiply(sum, WORK);
        }
        return sum;
    }
}
