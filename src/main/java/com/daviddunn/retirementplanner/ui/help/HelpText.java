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
            """
            The expected long-term annual return of your
            investment portfolio before taxes.

            This rate is used to project the future value
            of retirement accounts.

            Typical assumptions:

            • Conservative: 4% - 6%
            • Moderate: 6% - 8%
            • Aggressive: 8% - 10%
            """;

    public static final String GENERAL_INFLATION =
            """
            The annual inflation rate applied to
            non-healthcare household expenses.

            Examples include:

            • Housing
            • Utilities
            • Food
            • Transportation
            • Entertainment
            • Insurance

            This rate is applied only to expenses whose
            Growth Category is set to GENERAL.
            """;

    public static final String HEALTHCARE_INFLATION =
            """
            The annual inflation rate applied to
            healthcare-related expenses.

            Examples include:

            • Medicare premiums
            • Health insurance
            • Prescription drugs
            • Doctor visits
            • Dental care
            • Vision care
            • Long-term care insurance

            Healthcare expenses have historically risen
            faster than general inflation.

            This rate is applied only to expenses whose
            Growth Category is set to HEALTHCARE.
            """;

    public static final String SOCIAL_SECURITY_COLA =
            """
            The annual Cost-of-Living Adjustment (COLA)
            applied to Social Security benefits after
            they begin.

            This rate is independent of the General
            Inflation Rate because Social Security COLAs
            are determined by the Social Security
            Administration and may be higher or lower
            than overall inflation.

            This assumption projects future Social
            Security income only.
            """;
}
