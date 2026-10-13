package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxUnavailableMessageTest {
    @Test void typedFailureIsActionableThroughExistingWrappedFailures() {
        var result = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.IL,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION),
                "Retirement qualification facts are missing.");
        var failure = new StateTaxUnavailableException(result, 2030);
        for (var exception : List.of(failure, new IllegalStateException("Evaluation failed", failure))) {
            var message = ProjectionFailureMessages.stateTaxUnavailableMessage(exception).orElseThrow();
            assertTrue(message.contains("2030"));
            assertTrue(message.contains("Retirement qualification facts are missing"));
            assertTrue(message.contains("No projection was completed"));
            assertFalse(message.contains("unexpected error"));
            assertFalse(message.contains("$0"));
        }
        assertTrue(ProjectionFailureMessages.stateTaxUnavailableMessage(new IllegalStateException("Unexpected")).isEmpty());
    }
}
