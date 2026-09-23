package com.daviddunn.retirementplanner.ui.socialsecurity;

/** Display choices only; none request another calculation. */
enum ClaimingStrategyHeatMapMetric {
    PERCENT_OF_OPTIMAL("% of Optimal",
            "Expected PV After-Tax Estate divided by the highest tested strategy's expected PV, multiplied by 100. "
                    + "Percentages require a positive highest PV. Colors always use this measure."),
    EXPECTED_PV_AFTER_TAX_ESTATE("Expected PV After-Tax Estate",
            "Mortality-weighted after-tax investable estate at second death, expressed in valuation-date dollars. "
                    + "This is the integrated analyzer's ranking objective and excludes non-investable assets."),
    DIFFERENCE_FROM_OPTIMAL("Difference from Optimal",
            "This strategy's expected PV after-tax estate minus the highest tested strategy's expected PV. "
                    + "Zero means equal PV; a negative value means lower PV."),
    DIFFERENCE_FROM_CURRENT("Difference from Current Plan",
            "This strategy's expected PV after-tax estate minus the complete Current Strategy baseline's expected PV. "
                    + "Unavailable when the baseline is incomplete or its evaluation failed."),
    EXPECTED_PV_SOCIAL_SECURITY("Expected PV Social Security",
            "Expected PV Social Security is not supplied by the current integrated result aggregate. "
                    + "No separate Social Security calculation is run for this display."),
    FUTURE_DOLLAR_ESTATE("Future-Dollar Estate",
            "Expected nominal after-tax investable estate across second-death dates, in future dollars. "
                    + "This is the analyzer's Expected After-Tax Heir Value, not its present-value ranking metric.");

    private final String label;
    private final String help;

    ClaimingStrategyHeatMapMetric(String label, String help) {
        this.label = label;
        this.help = help;
    }

    String help() { return help; }

    @Override
    public String toString() { return label; }
}
