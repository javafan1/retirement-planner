package com.daviddunn.retirementplanner.domain.socialsecurity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityBenefitReductionPolicyTest {

    @Test
    void zeroAndPreEffectiveYearReturnTheScheduledObjectUnchanged() {
        BigDecimal scheduled = new BigDecimal("1234.567");
        assertSame(scheduled, SocialSecurityBenefitReductionPolicy.none().reduce(scheduled, 2040));
        var policy = new SocialSecurityBenefitReductionPolicy(new BigDecimal("22"), 2040);
        assertSame(scheduled, policy.reduce(scheduled, 2039));
    }

    @Test
    void appliesDecimalPercentagesAtAndAfterEffectiveYearWithoutCompounding() {
        assertMoney("99.90", reduce("0.1", 2030, "100.00", 2030));
        assertMoney("90.00", reduce("10", 2030, "100.00", 2030));
        assertMoney("83.00", reduce("17", 2030, "100.00", 2030));
        assertMoney("78.00", reduce("22", 2030, "100.00", 2030));
        assertMoney("0.00", reduce("100", 2030, "100.00", 2030));

        var policy = new SocialSecurityBenefitReductionPolicy(new BigDecimal("22"), 2030);
        assertMoney("78.00", policy.reduce(new BigDecimal("100.00"), 2030));
        assertMoney("78.00", policy.reduce(new BigDecimal("100.00"), 2040));
    }

    @Test
    void roundsOnceToCentsUsingHalfUp() {
        var policy = new SocialSecurityBenefitReductionPolicy(new BigDecimal("17"), 2030);
        assertMoney("0.83", policy.reduce(new BigDecimal("1.00"), 2030));
        assertMoney("0.82", policy.reduce(new BigDecimal("0.99"), 2030));
    }

    @Test
    void cacheIdentityUsesMathematicalPercentEquivalenceAndEffectiveYear() {
        assertEquals(new SocialSecurityBenefitReductionPolicy(new BigDecimal("17.0"), 2040),
                new SocialSecurityBenefitReductionPolicy(new BigDecimal("17.00"), 2040));
        assertNotEquals(new SocialSecurityBenefitReductionPolicy(new BigDecimal("17"), 2040),
                new SocialSecurityBenefitReductionPolicy(new BigDecimal("22"), 2040));
        assertNotEquals(new SocialSecurityBenefitReductionPolicy(new BigDecimal("17"), 2040),
                new SocialSecurityBenefitReductionPolicy(new BigDecimal("17"), 2041));
    }

    @Test
    void rejectsOutOfRangePercentagesYearsAndNegativePayments() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityBenefitReductionPolicy(new BigDecimal("-0.01"), 2032));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityBenefitReductionPolicy(new BigDecimal("100.01"), 2032));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityBenefitReductionPolicy(BigDecimal.ZERO, 999));
        assertThrows(IllegalArgumentException.class,
                () -> new SocialSecurityBenefitReductionPolicy(BigDecimal.ZERO, 10000));
        assertThrows(IllegalArgumentException.class,
                () -> SocialSecurityBenefitReductionPolicy.none().reduce(BigDecimal.ONE.negate(), 2032));
    }

    private static BigDecimal reduce(String percent, int year, String amount, int paymentYear) {
        return new SocialSecurityBenefitReductionPolicy(new BigDecimal(percent), year)
                .reduce(new BigDecimal(amount), paymentYear);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
        assertEquals(2, actual.scale());
    }
}
