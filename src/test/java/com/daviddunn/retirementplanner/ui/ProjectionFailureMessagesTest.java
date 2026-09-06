package com.daviddunn.retirementplanner.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionFailureMessagesTest {

    @Test
    void preservesSpecificOpeningRmdValidationClassification() {

        assertTrue(ProjectionFailureMessages.isOpeningRmdValidation(
                new IllegalStateException(
                        "Opening RMD information is required for Primary IRA in 2026.")));
    }

    @Test
    void classifiesUnexpectedExceptionsSeparatelyAndKeepsMessageNonTechnical() {

        assertFalse(ProjectionFailureMessages.isOpeningRmdValidation(
                new NullPointerException("unexpected")));

        String message = ProjectionFailureMessages.unexpectedProjectionFailureMessage();
        assertTrue(message.contains("could not be completed"));
        assertTrue(message.contains("plan data has not been changed"));
    }
}
