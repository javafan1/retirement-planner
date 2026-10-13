package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxUnavailableMonteCarloBoundaryTest {
    @Test void existingFailureWrapperPreservesTypedUnavailabilityAndExplanation() {
        var result = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.IL,
                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                List.of(StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED), "Roth history unavailable.");
        var cause = new StateTaxUnavailableException(result, 2030);
        for (var phase : MonteCarloExecutionException.Phase.values()) {
            var failure = new MonteCarloExecutionException(phase, OptionalInt.of(3), 417, "Fixture", cause);
            assertSame(cause, failure.stateTaxUnavailable().orElseThrow());
            assertSame(result, failure.stateTaxUnavailable().orElseThrow().result());
            assertTrue(failure.getMessage().contains("State income tax unavailable"));
            assertTrue(failure.getMessage().contains("Roth history unavailable"));
        }
    }
}
