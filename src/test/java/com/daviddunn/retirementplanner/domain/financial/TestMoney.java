package com.daviddunn.retirementplanner.domain.financial;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TestMoney {

    private TestMoney() {
    }

    public static BigDecimal money(String value) {

        return new BigDecimal(value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return money("0");
    }

    public static BigDecimal percent(String value) {
        return new BigDecimal(value);
    }
}