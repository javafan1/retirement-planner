package com.daviddunn.retirementplanner.ui.charts;

public enum ProjectionChartMetric {
    INVESTABLE_ASSETS("Investable Assets"), TOTAL_NET_WORTH("Total Net Worth"),
    AFTER_TAX_ESTATE("After-Tax Estate"), TOTAL_INCOME("Total Income"),
    EXPENSES("Expenses (Projection Summary)"), TOTAL_TAXES("Total Income Taxes"),
    SOCIAL_SECURITY("Social Security Benefits"), INVESTMENT_GROWTH("Investment Growth"),
    PORTFOLIO_WITHDRAWALS("Gross Portfolio Withdrawals"), NON_INVESTABLE_ASSETS("Non-Investable Assets"),
    MEDICARE("Medicare Premiums"), ROTH_CONVERSIONS("Actual Roth Conversions"),
    RMD("RMD Distributed During Projection");

    private final String label;
    ProjectionChartMetric(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
