package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateTaxAssumptionsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void michiganIsExplicitDefaultAndFloridaRoundTrips() throws Exception {
        assertEquals(StateTaxJurisdiction.MI, StateTaxAssumptions.michigan().jurisdiction());
        var florida = StateTaxAssumptions.simplified(StateTaxJurisdiction.FL);
        assertEquals(florida, mapper.readValue(mapper.writeValueAsString(florida), StateTaxAssumptions.class));
        assertNull(florida.effectiveRateOverride());
    }

    @Test
    void explicitUnknownOrIncompleteConfigurationIsRejected() {
        for (String json : new String[] {
                "{\"jurisdiction\":\"CA\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\"}",
                "{\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\"}",
                "{\"jurisdiction\":\"FL\"}",
                "{\"jurisdiction\":1,\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\"}",
                "{\"jurisdiction\":\"FL\",\"calculationMethod\":0}",
                "{\"jurisdiction\":\"FL\",\"calculationMethod\":\"EFFECTIVE_RATE_OVERRIDE\",\"effectiveRateOverride\":0.035}",
                "{\"jurisdiction\":\"MI\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\",\"effectiveRateOverride\":0.01}"}) {
            assertThrows(Exception.class, () -> mapper.readValue(json, StateTaxAssumptions.class), json);
        }
    }
}
