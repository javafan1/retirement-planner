package com.daviddunn.retirementplanner.ui.socialsecurity;

/** Concise failure explanations; service exceptions never become UI stack traces. */
final class SocialSecurityAnalysisFailurePresentation {

    private SocialSecurityAnalysisFailurePresentation() { }

    static String message(Throwable failure) {
        String message = failure == null || failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("complete") && message.contains("survivor")) {
            return "A complete survivor policy or explicit complete baseline strategy is required. "
                    + "Configure it before comparing the current strategy; no survivor age is assumed.";
        }
        if (message.contains("modern-cohort") || message.contains("advanced path")) {
            return "This analysis requires two supported modern-cohort people, each with one "
                    + "correctly owned Social Security source.";
        }
        if (failure instanceof IllegalArgumentException && !message.isBlank()) {
            return message;
        }
        return "Analysis failed before completion. The previous result has been preserved. "
                + "If the current-plan baseline failed, baseline differences are unavailable.";
    }
}
