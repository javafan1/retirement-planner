package com.daviddunn.retirementplanner.ui.help;

/** Shared by the full Roth editor and Results quick-edit panel. */
public final class RothInputHelp {
    private RothInputHelp() { }

    public static final String ENABLE = "Include scheduled Roth conversions in the projection. Transfers require eligible tax-deferred assets and a Roth destination owned by the same person; enabling this does not change current account balances.";
    public static final String STRATEGY = "Choose a fixed dollar conversion, a federal bracket-fill target, or a custom taxable-income target. Target strategies account for taxes and tax-funding withdrawals when finding conversion room; actual transfers remain limited by eligible accounts.";
    public static final String YEAR = "First calendar year in which a conversion may occur. A one-time schedule runs only in this year; an annual schedule repeats subject to the stop rule.";
    public static final String AMOUNT = "Requested household conversion in dollars for each scheduled year under Fixed Amount. Primary-owned eligible sources are used before spouse-owned sources, with transfers only to a same-owner Roth account. Target strategies calculate their own requested amount.";
    public static final String TARGET = "Household federal taxable-income target in nominal dollars for Custom Taxable Income Target. This is total taxable income, not the amount to convert; existing income and tax-funding withdrawals reduce available room.";
    public static final String FREQUENCY = "One Time converts only in the start year. Annual repeats from the start year through the projection, subject to the selected stop rule and available eligible assets.";
    public static final String STOP = "First Household RMD prevents conversions in years when the household is subject to an RMD. No stop rule allows scheduled conversions to continue; RMD distributions themselves cannot be converted.";
}
