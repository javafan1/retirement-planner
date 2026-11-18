package com.daviddunn.retirementplanner.domain.tax.state.arkansas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Alternatives remain deliberately distinct. Neither is an approved production policy. */
public final class ArkansasRateWorksheet {
    public enum Policy { P_STATUTORY_CENTS, OFFICIAL_2026_ESTIMATED_TABLE }

    private static final int[] TABLE = {3136,3150,3163,3177,3191,3204,3218,3232,3245,3259,
            3273,3287,3300,3314,3328,3341,3355,3369,3382,3396,3410,3424,3437,3451,3465,
            3478,3492,3506,3519};
    private record Row(String first, String base, String rate, String excess) { }
    private static final Row[] ROWS = {
            new Row("5600", "0", ".02", "5599.99"),
            new Row("11200", "111", ".03", "11199.99"),
            new Row("16000", "255", ".034", "15999.99"),
            new Row("26400", "608", ".037", "26399.99"),
            new Row("30000", "741", ".037", "29999.99"),
            new Row("40000", "1111", ".037", "39999.99"),
            new Row("50000", "1481", ".037", "49999.99"),
            new Row("60000", "1851", ".037", "59999.99"),
            new Row("70000", "2221", ".037", "69999.99"),
            new Row("80001", "2591", ".037", "80000.99"),
            new Row("97601", "3533", ".037", "97600.99"),
            new Row("100000", "3618", ".037", "99999.99")};

    public BigDecimal grossTax(int year, BigDecimal netIncome, Policy policy) {
        ArkansasTaxRules.requirePublishedYear(year);
        Objects.requireNonNull(policy);
        if (Objects.requireNonNull(netIncome).signum() < 0) throw new IllegalArgumentException("Net income must be floored before worksheet.");
        return (policy == Policy.P_STATUTORY_CENTS ? statutory(netIncome) : estimated(netIncome))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal bracketAdjustment(BigDecimal netIncome) {
        long whole = netIncome.setScale(0, RoundingMode.FLOOR).longValueExact();
        return whole < 94701 || whole > 97600 ? BigDecimal.ZERO
                : BigDecimal.valueOf(290 - 10 * ((whole - 94701) / 100));
    }

    private BigDecimal statutory(BigDecimal n) {
        if (n.setScale(0, RoundingMode.FLOOR).compareTo(b("94700")) > 0)
            return n.multiply(b(".037")).subtract(b("79.90")).subtract(bracketAdjustment(n));
        return slice(n, "5600", "11200", ".02").add(slice(n, "11200", "16000", ".03"))
                .add(slice(n, "16000", "26400", ".034"))
                .add(n.subtract(b("26400")).max(BigDecimal.ZERO).multiply(b(".037")));
    }

    private BigDecimal estimated(BigDecimal n) {
        // The dated ES table explicitly extends its whole-dollar bands through .99.
        if (n.compareTo(b("94701")) >= 0 && n.compareTo(b("97601")) < 0)
            return BigDecimal.valueOf(TABLE[n.subtract(b("94701")).divideToIntegralValue(b("100")).intValueExact()]);
        if (n.compareTo(b("5599")) >= 0 && n.compareTo(b("5600")) < 0)
            throw new IllegalArgumentException("Published ES zero-rate prose leaves 5599 through 5599.99 unassigned; no guessed return-table accuracy.");
        Row selected = null;
        for (var row : ROWS) if (n.compareTo(b(row.first())) >= 0) selected = row;
        return selected == null ? BigDecimal.ZERO : b(selected.base())
                .add(n.subtract(b(selected.excess())).multiply(b(selected.rate())));
    }

    private static BigDecimal slice(BigDecimal n, String lower, String upper, String rate) {
        return n.min(b(upper)).subtract(b(lower)).max(BigDecimal.ZERO).multiply(b(rate));
    }
    private static BigDecimal b(String value) { return new BigDecimal(value); }
}
