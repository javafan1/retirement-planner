package com.daviddunn.retirementplanner.ui;

import java.util.Objects;

/**
 * User-facing messages for projection failures.
 * Domain-specific failures remain actionable; unexpected failures are kept
 * concise while their stack traces remain observable to developers.
 */
public final class ProjectionFailureMessages {

    private static final String OPENING_RMD_PREFIX =
            "Opening RMD information";

    private ProjectionFailureMessages() {
    }

    public static boolean isOpeningRmdValidation(Exception exception) {
        Objects.requireNonNull(exception, "Exception is required.");

        String message = exception.getMessage();
        return message != null && message.startsWith(OPENING_RMD_PREFIX);
    }

    public static String unexpectedProjectionFailureMessage() {
        return "The projection could not be completed because of an unexpected error.\n\n"
                + "Your plan data has not been changed.";
    }
}
