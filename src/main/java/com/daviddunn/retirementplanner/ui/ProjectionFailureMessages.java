package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;

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

    public static java.util.Optional<String> stateTaxUnavailableMessage(Exception exception) {
        Objects.requireNonNull(exception);
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof StateTaxUnavailableException unavailable) {
                return java.util.Optional.of("State income tax cannot be reliably estimated for "
                        + unavailable.result().jurisdiction() + " in " + unavailable.taxYear() + ".\n\n"
                        + String.join("\n", unavailable.result().limitations())
                        + "\n\nNo projection was completed. Your plan data has not been changed.");
            }
        }
        return java.util.Optional.empty();
    }
}
