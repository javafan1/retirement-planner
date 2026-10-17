package com.daviddunn.retirementplanner.ui.wizard;

import javafx.scene.Node;

/** An implemented page validates its controls before writing to the isolated draft. */
public interface NewPlanWizardStep {

    /** Wizard-local identity, never serialized. CUSTOM preserves lightweight test pages. */
    enum StepKey { HOUSEHOLD, ACCOUNTS, INCOME, EXPENSES, ASSUMPTIONS, REVIEW, SOCIAL_SECURITY, PENSIONS, CUSTOM }

    default StepKey key() { return StepKey.CUSTOM; }

    String title();

    Node content();

    /** Reports actionable feedback in the page and returns false when navigation is blocked. */
    boolean validateAndApply();

    default void onEntering() { }
}
