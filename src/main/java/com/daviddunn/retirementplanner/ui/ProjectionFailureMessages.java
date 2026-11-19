package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;

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

    public static String insufficientFundingMessage(
            com.daviddunn.retirementplanner.domain.projection.FundingFailure failure) {
        Objects.requireNonNull(failure, "Funding failure is required.");
        return "The projection could not be completed because required withdrawals exceeded eligible funds in "
                + failure.calendarYear() + ". First unsatisfied allocation: required "
                + com.daviddunn.retirementplanner.util.CurrencyFormatter.format(failure.requiredAmount())
                + ", eligible funds " + com.daviddunn.retirementplanner.util.CurrencyFormatter.format(failure.availableAmount())
                + ", shortfall " + com.daviddunn.retirementplanner.util.CurrencyFormatter.format(failure.shortfallAmount())
                + ". This is the first allocation shortfall, not total lifetime unmet spending. "
                + "Your plan is preserved. Edit the plan and retry; no completed projection is available.";
    }

    public static java.util.Optional<String> stateTaxUnavailableMessage(Exception exception) {
        Objects.requireNonNull(exception);
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ArkansasProjectionUnavailableException unavailable) {
                return java.util.Optional.of(arkansasUnavailableMessage(unavailable));
            }
            if (cause instanceof StateTaxUnavailableException unavailable) {
                return java.util.Optional.of("State income tax cannot be reliably estimated for "
                        + unavailable.result().jurisdiction() + " in " + unavailable.taxYear() + ".\n\n"
                        + String.join("\n", unavailable.result().limitations())
                        + "\n\nNo projection was completed. Your plan data has not been changed.");
            }
        }
        return java.util.Optional.empty();
    }

    public static java.util.Optional<ArkansasProjectionUnavailableException> arkansasFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ArkansasProjectionUnavailableException failure) return java.util.Optional.of(failure);
        }
        return java.util.Optional.empty();
    }

    public static String arkansasUnavailableMessage(ArkansasProjectionUnavailableException failure) {
        String explanation = switch (failure.issue().kind()) {
            case PENSION_ORIGIN -> "Verify the original retirement income source for the affected pensions using the plan documents.";
            case RETIREMENT_SOURCE -> "This retirement arrangement requires different tax treatment than the supported ordinary sources.";
            case RECEIPT_TIMING -> "The projected receipt dates do not establish which payments occur before age 59½.";
            case ADDITIONAL_TAX -> "The additional tax on early distributions cannot be established from the available evidence.";
            case LOW_INCOME -> "Arkansas low-income rules could change this year's estimate. The available rules cannot establish the amount.";
            case BASIS -> "The taxable portion cannot be established without sufficient basis evidence.";
            case NIIT -> "The available investment-income evidence is incomplete for the net investment income tax.";
            case POLICY -> "The year or filing scope is outside the reviewed Arkansas planning policy.";
            case FUNDING_BUDGET -> "The funding calculation reached its proof limit before it could establish a withdrawal amount.";
            case FUNDING_PROOF -> "The funding calculation could not establish a supported withdrawal amount.";
            case TARGET_BUDGET -> "The targeted Roth conversion reached its proof limit.";
            case TARGET_PROOF -> "The calculation could not verify a targeted Roth conversion.";
            case SOURCE_OR_TAX -> "The available source or tax evidence is incomplete or inconsistent.";
        };
        var detail = Objects.toString(failure.detail(), "").replaceAll("\\s+", " ").strip();
        if (detail.length() > 240) detail = detail.substring(0, 239) + "…";
        return "Arkansas state income tax is unavailable for " + failure.year() + ". " + explanation
                + (detail.isBlank() ? "" : " " + detail);
    }
}
