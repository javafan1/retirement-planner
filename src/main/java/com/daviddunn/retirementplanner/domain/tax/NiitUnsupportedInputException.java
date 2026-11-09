package com.daviddunn.retirementplanner.domain.tax;

/** Unsupported tax evidence must abort an analysis, never become portfolio failure. */
public final class NiitUnsupportedInputException extends UnsupportedOperationException {
    public NiitUnsupportedInputException(String detail) {
        super("This annuity-enabled calculation cannot establish complete NIIT treatment: " + detail
                + " Original inputs and previous results are preserved.");
    }
}
