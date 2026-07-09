package com.daviddunn.retirementplanner.util;

import java.math.BigDecimal;

public final class Money {

    private Money() {
        // Prevent instantiation
    }

    public static BigDecimal of(double amount) {
        return BigDecimal.valueOf(amount);
    }

    public static BigDecimal of(String amount) {
        return new BigDecimal(amount);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}