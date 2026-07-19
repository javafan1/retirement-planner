package com.daviddunn.retirementplanner.domain.model;
//
//public enum AccountType {
//
//    TRADITIONAL_IRA,
//    ROTH_IRA,
//    BROKERAGE,
//    FOUR_ZERO_ONE_K,
//    HSA
//}

public enum AccountType {

    TRADITIONAL_IRA("Traditional IRA"),
    ROTH_IRA("Roth IRA"),
    BROKERAGE("Brokerage"),
    CHECKING("Checking"),
    SAVINGS("Savings");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}