package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecurityPresentValueCalculatorTest {

    private final SocialSecurityPresentValueCalculator calculator =
            new SocialSecurityPresentValueCalculator();

    @Test
    void zeroColaLeavesNominalAndRealAmountsEqual() {
        assertMoney(new BigDecimal("1000.00"),
                calculator.toBaseDateRealAmount(
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        YearMonth.of(2030, 1),
                        YearMonth.of(2040, 1)));
    }

    @Test
    void annualColaIsRemovedUsingCalendarYearConvention() {
        assertMoney(new BigDecimal("1000.00"),
                calculator.toBaseDateRealAmount(
                        new BigDecimal("1030"),
                        new BigDecimal("0.03"),
                        YearMonth.of(2030, 6),
                        YearMonth.of(2031, 1)));
    }

    @Test
    void baseMonthHasDiscountFactorOne() {
        assertMoney(new BigDecimal("1200.00"),
                calculator.presentValue(
                        new BigDecimal("1200"),
                        new BigDecimal("0.12"),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2030, 1)));
    }

    @Test
    void twelveAndTwentyFourMonthsUseEffectiveAnnualDiscounting() {
        assertMoney(new BigDecimal("1200").divide(new BigDecimal("1.12"), MathContext.DECIMAL128),
                calculator.presentValue(
                        new BigDecimal("1200"),
                        new BigDecimal("0.12"),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2031, 1)));
        assertMoney(new BigDecimal("1200").divide(new BigDecimal("1.2544"), MathContext.DECIMAL128),
                calculator.presentValue(
                        new BigDecimal("1200"),
                        new BigDecimal("0.12"),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2032, 1)));
    }

    @Test
    void oneMonthUsesFractionalEffectiveAnnualExponent() {
        BigDecimal value = calculator.presentValue(
                new BigDecimal("1200"),
                new BigDecimal("0.12"),
                YearMonth.of(2030, 1),
                YearMonth.of(2030, 2));

        assertTrue(value.compareTo(new BigDecimal("1180")) > 0);
        assertTrue(value.compareTo(new BigDecimal("1200")) < 0);
    }

    @Test
    void negativeRealRateRaisesFuturePresentValue() {
        assertTrue(calculator.presentValue(
                        new BigDecimal("1000"),
                        new BigDecimal("-0.01"),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2031, 1))
                .compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void mathematicallyInvalidRatesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.presentValue(
                        BigDecimal.ONE,
                        BigDecimal.ONE.negate(),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2031, 1)));
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    @Test
    void realConversionRetainsSubCentPrecision() {
        var actual = calculator.toBaseDateRealAmount(BigDecimal.ONE, new BigDecimal("0.03"),
                YearMonth.of(2030, 1), YearMonth.of(2031, 1));
        assertEquals(BigDecimal.ONE.divide(new BigDecimal("1.03"), MathContext.DECIMAL128), actual);
        assertTrue(actual.scale() > 2);
    }

    @Test
    void fractionalDiscountUsesDecimalTwelfthRootAndRetainsPrecision() {
        var monthlyFactor = new BigDecimal("1.01");
        var annualRate = monthlyFactor.pow(12).subtract(BigDecimal.ONE);
        var actual = calculator.presentValue(BigDecimal.ONE, annualRate,
                YearMonth.of(2030, 1), YearMonth.of(2030, 2));
        assertEquals(BigDecimal.ONE.divide(monthlyFactor, MathContext.DECIMAL128), actual);
        assertTrue(actual.scale() > 2);
        assertEquals(new BigDecimal("0.99"), actual.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void pastPaymentsZeroAmountsAndTinyRatesRemainDecimal() {
        var base = YearMonth.of(2030, 1);
        assertMoney(new BigDecimal("1.12"), calculator.presentValue(BigDecimal.ONE,
                new BigDecimal("0.12"), base, base.minusYears(1)));
        assertMoney(BigDecimal.ZERO, calculator.presentValue(BigDecimal.ZERO,
                new BigDecimal("0.12"), base, base.plusYears(100)));
        assertTrue(calculator.presentValue(BigDecimal.ONE, new BigDecimal("0.00000000000000000001"),
                base, base.plusMonths(1)).compareTo(BigDecimal.ONE) < 0);
    }
}
