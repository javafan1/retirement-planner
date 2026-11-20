package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.UnsupportedExecutionException;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

/**
 * User-facing messages for projection failures.
 * Domain-specific failures remain actionable; unexpected failures are kept
 * concise while their stack traces remain observable to developers.
 */
public final class ProjectionFailureMessages {

    private static final String OPENING_RMD_PREFIX =
            "Opening RMD information";
    private static final int MAX_CAUSES = 16;
    private static final Pattern TECHNICAL_DETAIL = Pattern.compile(
            "\\b(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*\\b"
                    + "|\\b[A-Z][\\w$]*(?:Exception|Error)\\b|\\bat\\s+[^\\s]+\\([^)]*:\\d+\\)");

    static final String ARKANSAS_CONDITIONAL_INCOME = """
            This Arkansas projection includes conditional income that Aledrix cannot yet calculate.

            Your plan and any previously accepted results have been preserved.

            Review the entries under View → Income → Conditional Benefits.

            To change the modeled state of residence, open View → Assumptions and find State of Residence under Income Tax Assumptions.""";

    static final String ARKANSAS_UNEXPECTED_FAILURE = """
            The projection could not be completed. Your existing plan and previously accepted results have not been replaced.

            Review the application log for details.""";

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
        return findCause(exception, ArkansasProjectionUnavailableException.class);
    }

    static boolean isArkansasConditionalIncomeRefusal(Throwable failure) {
        return findCause(failure, UnsupportedExecutionException.class)
                .flatMap(UnsupportedExecutionException::unsupportedState)
                .filter(StateTaxJurisdiction.AR::equals).isPresent();
    }

    static boolean isCancelled(Throwable failure) {
        return findCause(failure, CancellationException.class).isPresent()
                || findCause(failure, AnalysisCancelledException.class).isPresent();
    }

    private static <T extends Throwable> Optional<T> findCause(Throwable failure, Class<T> type) {
        for (int depth = 0; failure != null && depth < MAX_CAUSES; depth++, failure = failure.getCause()) {
            if (type.isInstance(failure)) return Optional.of(type.cast(failure));
        }
        return Optional.empty();
    }

    public static String arkansasUnavailableMessage(ArkansasProjectionUnavailableException failure) {
        return arkansasUnavailableMessage(failure, failure.detail());
    }

    static String arkansasProjectionWarningMessage(ArkansasProjectionUnavailableException failure) {
        String detail = failure.detail();
        if (detail != null && TECHNICAL_DETAIL.matcher(detail).find()) detail = "";
        return arkansasUnavailableMessage(failure, detail);
    }

    private static String arkansasUnavailableMessage(ArkansasProjectionUnavailableException failure, String sourceDetail) {
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
        var detail = Objects.toString(sourceDetail, "").replaceAll("\\s+", " ").strip();
        if (detail.length() > 240) detail = detail.substring(0, 239) + "…";
        return "Arkansas state income tax is unavailable for " + failure.year() + ". " + explanation
                + (detail.isBlank() ? "" : " " + detail);
    }
}
