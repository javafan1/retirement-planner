package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.model.SpousalInheritanceAssumption.Treatment.*;
import static org.junit.jupiter.api.Assertions.*;

class SpousalInheritanceAssumptionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "{\"expectedAnnualInvestmentReturn\":0.07,\"generalInflationRate\":0.03";

    @Test void newPlansPreferOwnerAndOmittedLegacyFieldsRemainOmitted() throws Exception {
        assertEquals(List.of(SPOUSE_AS_OWNER, SPOUSE_AS_BENEFICIARY), List.of(SpousalInheritanceAssumption.Treatment.values()));
        for (var plan : List.of(RetirementPlanFactory.createEmptyPlan(), RetirementPlanFactory.createSinglePersonPlan())) {
            assertEquals(SPOUSE_AS_OWNER, plan.getPlanningAssumptions().getEconomicAssumptions().getSpousalInheritance().treatment());
        }
        var legacy = JSON.readValue(BASE + "}", EconomicAssumptions.class);
        assertNull(legacy.getSpousalInheritance());
        assertFalse(JSON.valueToTree(legacy).has("spousalInheritance"));
        assertEquals(new EconomicAssumptions(new BigDecimal("0.07"), new BigDecimal("0.03")), legacy);
        // Whole-field null is treated as omitted legacy input, never as a choice.
        var nullField = JSON.readValue(BASE + ",\"spousalInheritance\":null}", EconomicAssumptions.class);
        assertEquals(legacy, nullField);
        assertFalse(JSON.valueToTree(nullField).has("spousalInheritance"));
    }

    @ParameterizedTest @EnumSource(SpousalInheritanceAssumption.Treatment.class)
    void explicitPlanChoiceRoundTripsWithVersion(SpousalInheritanceAssumption.Treatment treatment) throws Exception {
        var legacy = JSON.readValue(BASE + "}", EconomicAssumptions.class);
        var chosen = legacy.withSpousalInheritance(new SpousalInheritanceAssumption(treatment));
        var document = JSON.writeValueAsString(chosen);
        assertEquals(1, JSON.readTree(document).path("spousalInheritance").path("version").intValue());
        assertEquals(treatment.name(), JSON.readTree(document).path("spousalInheritance").path("treatment").textValue());
        assertEquals(chosen, JSON.readValue(document, EconomicAssumptions.class));
        assertNotEquals(legacy, chosen);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"SPOUSE_AS_OWNER\"", "{}", "{\"version\":2,\"treatment\":\"SPOUSE_AS_OWNER\"}",
            "{\"version\":\"1\",\"treatment\":\"SPOUSE_AS_OWNER\"}", "{\"version\":1,\"treatment\":\"AUTO\"}",
            "{\"version\":1,\"treatment\":0}", "{\"version\":1,\"treatment\":null}"})
    void rejectsMalformedOrFutureValues(String value) {
        assertThrows(JsonMappingException.class,
                () -> JSON.readValue(BASE + ",\"spousalInheritance\":" + value + "}", EconomicAssumptions.class));
    }
}
