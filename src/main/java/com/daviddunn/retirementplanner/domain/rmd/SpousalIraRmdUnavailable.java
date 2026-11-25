package com.daviddunn.retirementplanner.domain.rmd;

/** An applied-policy refusal is distinct from a verified financial shortfall. */
public final class SpousalIraRmdUnavailable extends IllegalArgumentException {
    public enum Reason {
        UNSUPPORTED_POLICY_OR_ACCOUNT,
        UNRESOLVED_MANDATORY_DISTRIBUTION,
        INCOMPATIBLE_ACCOUNT_HISTORY,
        MISSING_OR_DUPLICATE_SOURCE,
        INVALID_OWNERSHIP_CHRONOLOGY,
        HISTORICAL_EVIDENCE_MISMATCH
    }

    private final int year;
    private final Reason reason;

    public SpousalIraRmdUnavailable(int year, Reason reason, String detail) {
        super("Spousal IRA RMD treatment unavailable (" + year + "). " + detail);
        this.year = year;
        this.reason = java.util.Objects.requireNonNull(reason);
    }

    public int year() { return year; }
    public Reason reason() { return reason; }
}
