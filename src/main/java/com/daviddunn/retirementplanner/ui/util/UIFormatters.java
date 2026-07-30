package com.daviddunn.retirementplanner.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;

public final class UIFormatters {

    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance();

    static {
        MONEY.setMaximumFractionDigits(0);
        MONEY.setMinimumFractionDigits(0);
    }

    private UIFormatters() {
    }

    public static String money(BigDecimal value) {

        if (value == null) {
            return "";
        }

        return MONEY.format(value);
    }
}