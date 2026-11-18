package com.daviddunn.retirementplanner.domain.income;

/** Explicit legal plan fact; names, payment frequency and account presentation are not evidence. */
public enum PensionPlanClassification {
    UNKNOWN("Unknown"),
    DEFINED_BENEFIT_414J("Defined benefit (IRC 414(j))"),
    DEFINED_CONTRIBUTION("Defined contribution");

    private final String label;

    PensionPlanClassification(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Keep absent legacy facts absent on the wire and in existing default fingerprints. */
    public static final class UnknownFilter {
        @Override
        public boolean equals(Object value) {
            return value == UNKNOWN;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
