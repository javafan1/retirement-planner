package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertMoney(new BigDecimal("1071.43"),
                calculator.presentValue(
                        new BigDecimal("1200"),
                        new BigDecimal("0.12"),
                        YearMonth.of(2030, 1),
                        YearMonth.of(2031, 1)));
        assertMoney(new BigDecimal("956.63"),
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
}
