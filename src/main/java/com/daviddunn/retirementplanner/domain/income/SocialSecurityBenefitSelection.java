package com.daviddunn.retirementplanner.domain.income;

public enum SocialSecurityBenefitSelection {
    NONE("None"),
    OWN("Own Retirement"),
    SURVIVOR("Survivor");

    private final String displayName;

    SocialSecurityBenefitSelection(
            String displayName) {

        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
