package com.daviddunn.retirementplanner.domain.rules;

public enum FilingStatus {

    SINGLE("Single"),

    MARRIED_FILING_JOINTLY(
            "Married Filing Jointly"),

    MARRIED_FILING_SEPARATELY(
            "Married Filing Separately"),

    HEAD_OF_HOUSEHOLD(
            "Head of Household"),

    QUALIFYING_SURVIVING_SPOUSE(
            "Qualifying Surviving Spouse"),
    ;

    private final String displayName;

    FilingStatus(
            String displayName) {

        this.displayName =
                displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}