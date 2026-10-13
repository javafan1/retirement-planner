package com.daviddunn.retirementplanner.domain.tax.state;

/** Missing financial facts, not jurisdiction-specific exemption conclusions. */
public enum StateTaxUnavailableReason {
    MISSING_RETIREMENT_QUALIFICATION,
    MISSING_DISTRIBUTION_BASIS,
    NONQUALIFIED_ROTH_BASIS_REQUIRED,
    INHERITED_DISTRIBUTION_HISTORY_REQUIRED,
    UNSUPPORTED_TRANSACTION_TYPE,
    UNSUPPORTED_MODEL_RULE
}
