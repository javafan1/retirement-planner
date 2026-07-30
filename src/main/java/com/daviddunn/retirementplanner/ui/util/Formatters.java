package com.daviddunn.retirementplanner.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;

public final class Formatters {

    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance();

    public static String money(BigDecimal value) {

        if (value == null) {
            return "";
        }

        return MONEY.format(value);
    }
}