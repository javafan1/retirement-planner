package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.OklahomaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.tax.state.OklahomaProductionTaxCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaInputReportTest {
    @Test
    void individualReportsAndSharedAnalyzerInputsCaptureAllFiveCategories() throws Exception {
        var individual = OklahomaProjectionAcceptanceTest.plan(false);
        var frozen = IndividualReportContext.capture(individual);
        var mapper = new ObjectMapper();
        var saved = mapper.writeValueAsString(frozen.assumptions());
        for (var note : OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(saved.contains(note));
        }
        individual.setPlanningAssumptions(individual.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        assertEquals(saved, mapper.writeValueAsString(frozen.assumptions()));
        assertFalse(mapper.writeValueAsString(IndividualReportContext.capture(individual).assumptions()).contains("OK-2026-1"));
        var couple = OklahomaProjectionAcceptanceTest.plan(true);
        var start = couple.getPlanningAssumptions().getProjectionStartDate();
        var summary = SocialSecurityAnalyzerInputSummary.from(couple,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(BigDecimal.ONE, BigDecimal.ONE, start, start, BigDecimal.ZERO));
        var text = summary.rows().toString();
        for (var note : OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(text.contains(note));
        }
    }
}
