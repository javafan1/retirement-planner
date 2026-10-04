package com.daviddunn.retirementplanner.ui.help;

public final class HelpText {

    public static final String SURVIVOR_BENEFIT_CLAIMING_AGE =
            "When the surviving household member elects Social Security survivor benefits after the other's death. "
                    + "Separate from their own retirement-benefit claiming age. Choices depend on age at death "
                    + "and Survivor Full Retirement Age (FRA); death at or after Survivor FRA means immediate election.";

    public static final String PLANNING_HORIZON =
            "Defines the end of the household retirement projection, generally representing "
                    + "the assumed death of the surviving household member. This planning horizon "
                    + "can materially affect Social Security claiming comparisons, taxes, portfolio "
                    + "values, and estate results. Enter calendar years, including the opening year, "
                    + "which may be partial.";
    private HelpText() {
        // Utility class
    }

    /*
     * =====================================================
     * Economic Assumptions
     * =====================================================
     */

    public static final String INVESTMENT_RETURN =
            "Annual investment-return percentage used to grow projected investable balances before taxes; enter 5 for 5%. This is a constant return in deterministic projections. Monte Carlo uses its separate session return and volatility settings.";

    public static final String GENERAL_INFLATION =
            "Annual percentage used to grow General-category spending, including one-time purchases, from the projection opening year. It also projects applicable government-rule amounts such as IRMAA thresholds. Healthcare Inflation and Social Security COLA are separate assumptions.";

    public static final String HEALTHCARE_INFLATION =
            "Annual percentage used to grow Healthcare-category expenses and modeled Medicare premium amounts. It is separate from General Inflation and remains deterministic in the current Monte Carlo model.";

    public static final String SOCIAL_SECURITY_COLA =
            "Annual percentage used to grow Social Security benefit amounts from their benefit valuation year, including years before claiming. Separate claiming-age adjustments determine early or delayed benefits. This assumption is independent of General Inflation.";
}
