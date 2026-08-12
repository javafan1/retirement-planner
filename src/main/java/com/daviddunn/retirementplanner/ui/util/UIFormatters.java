package com.daviddunn.retirementplanner.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
public final class UIFormatters {

    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance();

    private static final NumberFormat PERCENT =
            NumberFormat.getPercentInstance();

    static {
        MONEY.setMaximumFractionDigits(0);
        MONEY.setMinimumFractionDigits(0);

        PERCENT.setMaximumFractionDigits(2);
        PERCENT.setMinimumFractionDigits(2);
    }

    private UIFormatters() {
    }

    public static String money(BigDecimal value) {

        if (value == null) {
            return "";
        }

        return MONEY.format(value);
    }

    public static String percent(BigDecimal value) {

        if (value == null) {
            return "";
        }

        return PERCENT.format(value);
    }
}