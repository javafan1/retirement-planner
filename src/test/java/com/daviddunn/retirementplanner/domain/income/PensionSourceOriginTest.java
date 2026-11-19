package com.daviddunn.retirementplanner.domain.income;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PensionSourceOriginTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String LEGACY = """
            {"incomeType":"pension","name":"Legacy","ownership":"PRIMARY","startDate":"2026-01-01",
             "monthlyBenefit":900,"annualColaRate":0,"planClassification":"DEFINED_BENEFIT_414J"}
            """.strip();

    @Test
    void missingAndExplicitUnknownShareCanonicalRepresentation() throws Exception {
        var legacy = MAPPER.readValue(LEGACY, Pension.class);
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, legacy.getSourceOrigin());
        var legacyCanonical = MAPPER.writeValueAsString(legacy);
        assertFalse(legacyCanonical.contains("sourceOrigin"));
        assertEquals(legacyCanonical, MAPPER.writeValueAsString(MAPPER.readValue(withOrigin("\"UNKNOWN\""), Pension.class)));
        assertEquals(legacyCanonical, MAPPER.writeValueAsString(MAPPER.readValue(withOrigin("null"), Pension.class)));

        for (var origin : RetirementIncomeSourceOrigin.values()) {
            legacy.setSourceOrigin(origin);
            var saved = MAPPER.writeValueAsString(legacy);
            var reloaded = MAPPER.readValue(saved, Pension.class);
            assertEquals(origin, reloaded.getSourceOrigin());
            assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J, reloaded.getPlanClassification());
            assertEquals(saved, MAPPER.writeValueAsString(reloaded));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"GUESSED_EMPLOYER_NAME\"", "\"employer_retirement_plan\"", "1", "false", "{}"})
    void invalidOriginCannotBecomeAnEmploymentPlan(String value) {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue(withOrigin(value), Pension.class));
    }

    private static String withOrigin(String value) {
        return LEGACY.substring(0, LEGACY.length() - 1) + ",\"sourceOrigin\":" + value + "}";
    }
}
