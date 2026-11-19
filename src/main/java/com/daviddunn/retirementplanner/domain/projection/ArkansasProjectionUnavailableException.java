package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight;
import java.util.List;

/** A nonproduction annual result was unavailable. This is never an insolvency outcome. */
public final class ArkansasProjectionUnavailableException extends RuntimeException {
    public enum Reason { SOURCE_OR_TAX, FUNDING_PROOF, TARGET_PROOF }

    private final int year;
    private final Reason reason;
    private final List<ArkansasPensionPreflight.Review> pensionsToReview;

    public ArkansasProjectionUnavailableException(int year, Reason reason, String detail) {
        this(year, reason, detail, List.of());
    }

    ArkansasProjectionUnavailableException(int year, Reason reason, String detail,
            List<ArkansasPensionPreflight.Review> pensionsToReview) {
        super("State Tax Unavailable — Arkansas (" + year + "): " + detail);
        this.year = year;
        this.reason = reason;
        this.pensionsToReview = List.copyOf(pensionsToReview);
    }

    public int year() { return year; }
    public Reason reason() { return reason; }
    public List<ArkansasPensionPreflight.Review> pensionsToReview() { return pensionsToReview; }
}
