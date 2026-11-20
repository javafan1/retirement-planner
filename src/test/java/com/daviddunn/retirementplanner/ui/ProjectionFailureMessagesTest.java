package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void recognizesOnlyTheArkansasStateRefusalThroughCompletionWrappers() {
        var arkansas = assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                () -> ConditionalBenefitAdmission.requireDeterministicState(StateTaxJurisdiction.AR));
        assertTrue(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(arkansas));
        assertTrue(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(new CompletionException(arkansas)));
        assertTrue(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(
                new CompletionException(new ExecutionException(new CompletionException(arkansas)))));

        var iowa = assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                () -> ConditionalBenefitAdmission.requireDeterministicState(StateTaxJurisdiction.IA));
        assertFalse(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(new CompletionException(iowa)));
        assertFalse(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(
                new ConditionalBenefitAdmission.UnsupportedExecutionException()));
        assertFalse(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(
                new RuntimeException(arkansas.toString())));
        assertTrue(ProjectionFailureMessages.isCancelled(new CompletionException(new CancellationException())));
    }

    @Test
    void preservesTypedAnnualEvidenceAndOmitsTechnicalDetailInProjectionWarning() {
        var failure = new ArkansasProjectionUnavailableException(2027,
                ArkansasProjectionUnavailableException.Reason.FUNDING_PROOF, "A withdrawal amount could not be verified.");
        assertEquals(failure, ProjectionFailureMessages.arkansasFailure(
                new CompletionException(new ExecutionException(failure))).orElseThrow());
        assertTrue(ProjectionFailureMessages.arkansasProjectionWarningMessage(failure).contains("2027"));
        assertTrue(ProjectionFailureMessages.arkansasProjectionWarningMessage(failure)
                .contains("A withdrawal amount could not be verified."));
        var annual = new ArkansasProjectionUnavailableException(2028,
                ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX,
                "java.util.concurrent.CompletionException: com.example.InternalTaxFailure");
        String message = ProjectionFailureMessages.arkansasProjectionWarningMessage(annual);
        assertTrue(message.contains("2028"));
        assertFalse(message.contains("CompletionException"));
        assertFalse(message.contains("com.example"));
    }

    @Test
    void boundsCauseInspectionEvenForCyclicOrVeryDeepChains() {
        var cause = new IllegalStateException();
        cause.initCause(new IllegalStateException(cause));
        assertFalse(ProjectionFailureMessages.isArkansasConditionalIncomeRefusal(cause));
        assertTrue(ProjectionFailureMessages.arkansasFailure(cause).isEmpty());
    }
}
