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
    void strictKeepsTheLegacyWireAndRegularOnlyUsesAStableExplicitValue() throws Exception {
        var legacy = "{\"jurisdiction\":\"AR\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\",\"effectiveRateOverride\":null}";
        var strict = mapper.readValue(legacy, StateTaxAssumptions.class);
        assertEquals(ArkansasLowIncomePolicy.STRICT, strict.arkansasLowIncomePolicy());
        assertEquals(legacy, mapper.writeValueAsString(strict));
        var regular = new StateTaxAssumptions(strict.jurisdiction(), strict.calculationMethod(), null,
                ArkansasLowIncomePolicy.REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED);
        assertEquals(regular, mapper.readValue(mapper.writeValueAsString(regular), StateTaxAssumptions.class));
        assertTrue(regular.usesArkansasRegularTaxOnly());
        assertEquals(strict, mapper.readValue(legacy.replace("}", ",\"arkansasLowIncomePolicy\":\"STRICT\"}"),
                StateTaxAssumptions.class));
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
                "{\"jurisdiction\":\"MI\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\",\"effectiveRateOverride\":0.01}",
                "{\"jurisdiction\":\"AR\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\",\"arkansasLowIncomePolicy\":\"UNKNOWN\"}",
                "{\"jurisdiction\":\"AR\",\"calculationMethod\":\"SIMPLIFIED_STATE_ESTIMATE\",\"arkansasLowIncomePolicy\":1}"}) {
            assertThrows(Exception.class, () -> mapper.readValue(json, StateTaxAssumptions.class), json);
        }
    }
}
