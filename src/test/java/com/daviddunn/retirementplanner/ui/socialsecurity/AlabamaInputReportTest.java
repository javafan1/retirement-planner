package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.AlabamaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.tax.state.AlabamaProductionTaxCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaInputReportTest {
    @Test
    void individualReportsAndSharedAnalyzerInputsCaptureAllFiveCategories() throws Exception {
        var individual = AlabamaProjectionAcceptanceTest.plan(false);
        var pension=new com.daviddunn.retirementplanner.domain.income.Pension("Confirmed cash-balance pension",AccountOwnership.PRIMARY,
                java.time.LocalDate.of(2026,1,1),null,new BigDecimal("1000"),BigDecimal.ZERO);
        pension.setPlanClassification(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.DEFINED_BENEFIT_414J);
        individual.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var frozen = IndividualReportContext.capture(individual);
        var mapper = new ObjectMapper();
        var saved = mapper.writeValueAsString(frozen.assumptions());
        assertTrue(saved.contains("Defined benefit (IRC 414(j))"));
        pension.setPlanClassification(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.DEFINED_CONTRIBUTION);
        for (var note : AlabamaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(saved.contains(note));
        }
        individual.setPlanningAssumptions(individual.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        assertEquals(saved, mapper.writeValueAsString(frozen.assumptions()));
        assertFalse(mapper.writeValueAsString(IndividualReportContext.capture(individual).assumptions()).contains("AL-2026-1"));
        var couple = AlabamaProjectionAcceptanceTest.plan(true);
        var start = couple.getPlanningAssumptions().getProjectionStartDate();
        var summary = SocialSecurityAnalyzerInputSummary.from(couple,
                new SocialSecurityAnalyzerInputSummary.AnalyzerValues(BigDecimal.ONE, BigDecimal.ONE, start, start, BigDecimal.ZERO));
        var text = summary.rows().toString();
        for (var note : AlabamaProductionTaxCalculator.planningLimitations(2027, 2029)) {
            assertTrue(text.contains(note));
        }
    }
}
