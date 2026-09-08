package com.daviddunn.retirementplanner.domain.estate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class EstatePresentValueCalculatorTest {
    private final EstatePresentValueCalculator calculator = new EstatePresentValueCalculator();
    private final LocalDate valuation = LocalDate.of(2024, 1, 1);

    @ParameterizedTest
    @CsvSource({"0,0", "0.03,0", "0,0.04", "0.03,0.04", "-0.02,0.03"})
    void fourCalendarYearsIncludingLeapDayAreFourAct36525Years(String inflation, String discount) {
        var i = new BigDecimal(inflation);
        var r = new BigDecimal(discount);
        var expected = new BigDecimal("100000").divide(
                BigDecimal.ONE.add(i).pow(4).multiply(BigDecimal.ONE.add(r).pow(4)), MathContext.DECIMAL128);
        close(expected, calculator.calculate(new BigDecimal("100000"), valuation,
                LocalDate.of(2028, 1, 1), i, r));
    }

    @Test
    void fractionalYearUsesActualDaysAndSupportsDatesBeforeValuation() {
        // 1461 days / 365.25 = 4; four equal date intervals compose to this factor.
        var rate = new BigDecimal("0.04");
        var day = calculator.discountFactor(valuation, valuation.plusDays(1), BigDecimal.ZERO, rate);
        close(BigDecimal.ONE.divide(new BigDecimal("1.04").pow(4), MathContext.DECIMAL128),
                day.pow(1461, MathContext.DECIMAL128));
        close(BigDecimal.ONE, day.multiply(calculator.discountFactor(valuation.plusDays(1),
                valuation, BigDecimal.ZERO, rate)));
        close(BigDecimal.ONE, calculator.discountFactor(valuation, valuation, rate, rate));
    }

    @Test
    void rejectsRatesWhoseCompoundingBaseIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> calculator.discountFactor(
                valuation, valuation.plusYears(1), new BigDecimal("-1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> calculator.discountFactor(
                valuation, valuation.plusYears(1), BigDecimal.ZERO, new BigDecimal("-2")));
    }

    private static void close(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.subtract(actual).abs().compareTo(new BigDecimal("1E-25")) < 0,
                () -> expected + " != " + actual);
    }
}
