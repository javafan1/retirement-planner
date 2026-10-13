package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts.SeparationStatus;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts.RetirementConditionsStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class RetirementParticipantFactsTest {
    private static final LocalDate EVENT = LocalDate.of(2030, 6, 1);

    @Test void missingLegacyPropertiesRemainUnknown() throws Exception {
        assertEquals(RetirementParticipantFacts.UNKNOWN,
                new ObjectMapper().readValue("{}", RetirementParticipantFacts.class));
    }

    @Test void eventsAreNotRetroactiveAndApplyOnTheirExactDate() {
        var facts = new RetirementParticipantFacts(SeparationStatus.SEPARATED, EVENT,
                RetirementConditionsStatus.MET, EVENT.plusYears(1));
        assertEquals(SeparationStatus.NOT_SEPARATED, facts.separationStatusOn(EVENT.minusDays(1)));
        assertEquals(SeparationStatus.SEPARATED, facts.separationStatusOn(EVENT));
        assertEquals(SeparationStatus.SEPARATED, facts.separationStatusOn(EVENT.plusYears(1)));
        assertEquals(RetirementConditionsStatus.NOT_MET, facts.retirementConditionsStatusOn(EVENT));
        assertEquals(RetirementConditionsStatus.MET, facts.retirementConditionsStatusOn(EVENT.plusYears(1)));
    }

    @Test void undatedAffirmativeFactsHaveUnknownTiming() {
        var facts = new RetirementParticipantFacts(SeparationStatus.SEPARATED, null,
                RetirementConditionsStatus.MET, null);
        assertEquals(SeparationStatus.UNKNOWN, facts.separationStatusOn(EVENT));
        assertEquals(RetirementConditionsStatus.UNKNOWN, facts.retirementConditionsStatusOn(EVENT));
    }

    @ParameterizedTest @EnumSource(value = SeparationStatus.class, names = {"UNKNOWN", "NOT_SEPARATED"})
    void separationDatesRequireAffirmativeStatus(SeparationStatus status) {
        assertThrows(IllegalArgumentException.class, () -> new RetirementParticipantFacts(
                status, EVENT, RetirementConditionsStatus.UNKNOWN, null));
    }

    @ParameterizedTest @EnumSource(value = RetirementConditionsStatus.class, names = {"UNKNOWN", "NOT_MET"})
    void conditionDatesRequireAffirmativeStatus(RetirementConditionsStatus status) {
        assertThrows(IllegalArgumentException.class, () -> new RetirementParticipantFacts(
                SeparationStatus.UNKNOWN, null, status, EVENT));
    }

    @Test void negativePlanningAssertionsRemainNegative() {
        var facts = new RetirementParticipantFacts(SeparationStatus.NOT_SEPARATED, null,
                RetirementConditionsStatus.NOT_MET, null);
        assertEquals(SeparationStatus.NOT_SEPARATED, facts.separationStatusOn(EVENT));
        assertEquals(RetirementConditionsStatus.NOT_MET, facts.retirementConditionsStatusOn(EVENT));
    }

    @Test void eventDatesRoundTripAndInvalidCombinationsRejectOnLoad() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var facts = new RetirementParticipantFacts(SeparationStatus.SEPARATED, EVENT,
                RetirementConditionsStatus.MET, EVENT.plusMonths(1));
        assertEquals(facts, mapper.readValue(mapper.writeValueAsBytes(facts), RetirementParticipantFacts.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"separationStatus\":\"UNKNOWN\",\"separationDate\":\"2030-06-01\"}",
                RetirementParticipantFacts.class));
    }
}
