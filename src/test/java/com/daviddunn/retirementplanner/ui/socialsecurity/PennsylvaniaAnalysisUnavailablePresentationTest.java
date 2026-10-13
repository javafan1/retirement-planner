package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PennsylvaniaAnalysisUnavailablePresentationTest {
    @Test void typedStateTaxReasonSurvivesWeightedEvaluationWrapper() {
        var result = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.PA,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION), "Pension legal-plan facts are unavailable.");
        var failure = new IllegalStateException("Lifetime scenario failed. No completed expected value is available.",
                new StateTaxUnavailableException(result, 2030));
        var message = SocialSecurityAnalysisFailurePresentation.message(failure);
        assertTrue(message.contains("Pennsylvania"));
        assertTrue(message.contains("Pension legal-plan facts are unavailable"));
        assertTrue(message.contains("No completed state-tax-dependent analysis"));
        assertFalse(message.contains("$0"));
    }
}
