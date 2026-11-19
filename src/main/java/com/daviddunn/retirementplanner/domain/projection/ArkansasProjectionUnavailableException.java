package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;
import java.util.List;

/** A nonproduction annual result was unavailable. This is never an insolvency outcome. */
public final class ArkansasProjectionUnavailableException extends RuntimeException {
    public enum Reason { SOURCE_OR_TAX, FUNDING_PROOF, TARGET_PROOF }

    private final int year;
    private final Reason reason;
    private final ArkansasTaxIssue issue;
    private final String detail;
    private final List<ArkansasPensionPreflight.Review> pensionsToReview;

    public ArkansasProjectionUnavailableException(int year, Reason reason, String detail) {
        this(year, reason, detail, ArkansasTaxIssue.of(switch (reason) {
            case SOURCE_OR_TAX -> ArkansasTaxIssue.Kind.SOURCE_OR_TAX;
            case FUNDING_PROOF -> ArkansasTaxIssue.Kind.FUNDING_PROOF;
            case TARGET_PROOF -> ArkansasTaxIssue.Kind.TARGET_PROOF;
        }), List.of());
    }

    ArkansasProjectionUnavailableException(int year, Reason reason, String detail,
            ArkansasTaxIssue issue, List<ArkansasPensionPreflight.Review> pensionsToReview) {
        super("State Tax Unavailable — Arkansas (" + year + "): " + detail);
        this.year = year;
        this.reason = reason;
        this.issue = issue;
        this.detail = detail;
        this.pensionsToReview = List.copyOf(pensionsToReview);
    }

    public int year() { return year; }
    public Reason reason() { return reason; }
    public ArkansasTaxIssue issue() { return issue; }
    public String detail() { return detail; }
    public List<ArkansasPensionPreflight.Review> pensionsToReview() { return pensionsToReview; }
}
