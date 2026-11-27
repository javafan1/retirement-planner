package com.daviddunn.retirementplanner.domain.roth;

public enum RothRmdSchedulingPolicy {
    CONTINUE_THROUGH_END_YEAR("Continue Through End Year",
            "Pay RMDs first. Eligible conversions may continue through the end year."),
    SKIP_POSITIVE_RMD_YEARS("Skip Years With RMDs",
            "Skip a year with a positive household RMD. Later zero-RMD years remain eligible."),
    PERMANENTLY_STOP_AT_FIRST_RMD("Permanently Stop at First RMD",
            "Stop in the first year with a positive household RMD and all later years.");

    private final String label;
    private final String description;

    RothRmdSchedulingPolicy(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
