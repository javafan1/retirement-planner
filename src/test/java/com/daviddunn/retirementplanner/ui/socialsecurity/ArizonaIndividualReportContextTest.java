package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ArizonaProjectionAcceptanceTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArizonaIndividualReportContextTest {
    @Test
    void resultTimeIndividualAssumptionsRetainArizonaLimitationsForSavedExports() throws Exception {
        var plan = ArizonaProjectionAcceptanceTest.plan(false);
        var frozen = IndividualReportContext.capture(plan);
        var mapper = new ObjectMapper();
        var saved = mapper.writeValueAsString(frozen.assumptions());
        assertTrue(saved.contains("AZ-2026-1"));
        assertTrue(saved.contains("government and military"));
        assertTrue(saved.contains("expires after 2028"));
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        assertEquals(saved, mapper.writeValueAsString(frozen.assumptions()));
        assertFalse(mapper.writeValueAsString(IndividualReportContext.capture(plan).assumptions())
                .contains("AZ-2026-1"));
    }
}
