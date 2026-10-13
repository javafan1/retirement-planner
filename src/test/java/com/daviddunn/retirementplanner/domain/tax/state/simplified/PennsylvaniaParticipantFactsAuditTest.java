package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Audit only: participant facts deliberately do not assert legal plan qualification. */
class PennsylvaniaParticipantFactsAuditTest {
    @ParameterizedTest @EnumSource(value = IncomeType.class, names = {"EMPLOYER_RETIREMENT_PLAN", "PENSION"})
    void evenAffirmativeParticipantFactsDoNotProveRejectedLegalPlanCharacteristics(IncomeType type) throws Exception {
        var known = new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2020, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET,
                LocalDate.of(2021, 1, 1));
        for (var facts : List.of(RetirementParticipantFacts.UNKNOWN, known,
                new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.NOT_SEPARATED,
                        null, RetirementParticipantFacts.RetirementConditionsStatus.NOT_MET, null))) {
            var line = new Income(type, new BigDecimal("40000"), new BigDecimal("40000"), PensionSource.UNKNOWN,
                    Map.of("ELIGIBLE_PLAN_AND_RETIRED", Qualification.UNKNOWN));
            var request = new StateTaxRequest("PA", 2026, FilingStatus.SINGLE, new BigDecimal("40000"),
                    new BigDecimal("20000"), List.of(new PersonIncome(Owner.PRIMARY, new BigDecimal("73"),
                            BigDecimal.ZERO, BigDecimal.ZERO, Set.of(), List.of(line))));
            var result = new SimplifiedStateTaxCalculator().calculate(request);
            assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.status());
            assertTrue(result.estimate().isEmpty());
            assertFalse(result.unavailableReasons().isEmpty());
            // Knowing the participant event is useful, but cannot upgrade UNKNOWN plan qualification.
            assertNotNull(facts.separationStatusOn(LocalDate.of(2026, 12, 31)));
            assertNotNull(facts.retirementConditionsStatusOn(LocalDate.of(2026, 12, 31)));
        }
        assertEquals(List.of("AK", "FL", "IL", "MI", "NV", "NH", "PA", "SD", "TN", "TX", "WY"),
                StateTaxJurisdiction.alphabeticalChoices().stream().map(Enum::name).toList());
    }
}
