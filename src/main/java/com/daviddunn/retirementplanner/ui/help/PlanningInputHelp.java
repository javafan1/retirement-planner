package com.daviddunn.retirementplanner.ui.help;

/** Shared wording for the assumptions editor and Results quick-edit panel. */
public final class PlanningInputHelp {
    private PlanningInputHelp() { }

    public static final String START = "First date included in the retirement projection. The opening calendar year may be partial. Changing its year may require new Opening RMD information.";
    public static final String DEATH_SCENARIO = "Select whether both people survive the configured horizon or which person dies first. A selected death scenario changes survivor income, household expenses and later filing status; longevity analyses use their own sampled or weighted deaths.";
    public static final String DEATH_YEAR = "Calendar year of the selected person's death, modeled as January 1. Used only for a death scenario; the surviving household continues to the planning horizon.";
    public static final String EXPENSE_FACTOR = "Percentage of recurring household expenses retained after the first death. Enter 100 for no reduction or 75 for three quarters of those expenses. One-time purchases are not reduced by this factor.";
    public static final String BRACKET_GROWTH = "Annual percentage growth in federal income-tax bracket dollar thresholds. This changes bracket boundaries, not marginal tax rates.";
    public static final String DEDUCTION_GROWTH = "Annual percentage growth in the modeled federal standard deduction. This is separate from bracket growth and General Inflation.";
    public static final String RATE_CHANGE = "Additive change to federal marginal rates in percentage points from the effective year onward. Enter 2 for a two-point increase, not a 2% relative increase. Enter both fields or leave both blank for no change.";
    public static final String RATE_YEAR = "First calendar year using the future federal marginal-rate change. The change continues in later years. Leave both the year and rate-change fields blank to disable it.";
    public static final String STATE_RATE = "Stored state income-tax percentage assumption. The current projection calculates Michigan tax from government rules; this field does not override that calculation.";
    public static final String LOCAL_RATE = "Stored local income-tax percentage assumption. The current projection does not apply this field as an additional local tax.";
    public static final String HEIR_RATE = "Estimated tax percentage applied to tax-deferred assets when reporting After-Tax Estate. This affects the estate estimate, not annual household income-tax calculations.";
    public static final String OPENING_RMD = "Review each applicable account's prior December 31 balance and RMD already distributed before the projection start. These historical facts determine the remaining opening-year RMD.";
    public static final String CLAIMING_AGE = "Whole-year age when this person elects Social Security retirement benefits. The application derives the benefit start date from their birth date and adjusts benefits for claiming relative to Full Retirement Age. This is separate from the survivor-benefit election.";
}
