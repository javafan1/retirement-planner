package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;

/** A proved funding shortage in an analysis that requires a complete financial horizon. */
public final class InsufficientAnalysisFundsException extends IllegalStateException {
    private final FundingFailure failure;

    InsufficientAnalysisFundsException(FundingFailure failure) {
        super("Insufficient funds in " + failure.calendarYear() + ". Shortfall " + failure.shortfallAmount().toPlainString() + ".");
        this.failure = failure;
    }

    public FundingFailure failure() { return failure; }
}
