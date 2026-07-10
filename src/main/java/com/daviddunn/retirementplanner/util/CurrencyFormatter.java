package com.daviddunn.retirementplanner.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {

    private static final NumberFormat FORMATTER =
            NumberFormat.getCurrencyInstance(Locale.US);

    private CurrencyFormatter() {
    }

    public static String format(BigDecimal amount) {
        return FORMATTER.format(amount);
    }
}