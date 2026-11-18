package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PensionClassificationTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Pension pension() {
        return new Pension("Cash balance is only a name", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("750"));
    }

    @Test
    void legacyWireShapeDefaultsUnknownWithoutInferringFromName() throws Exception {
        var pension = pension();
        assertEquals(PensionPlanClassification.UNKNOWN, pension.getPlanClassification());
        var json = mapper.writeValueAsString(pension);
        assertFalse(json.contains("planClassification"), "Default must preserve the legacy wire/fingerprint shape.");
        assertEquals(PensionPlanClassification.UNKNOWN,
                mapper.readValue(json, Pension.class).getPlanClassification());
    }

    @Test
    void explicitClassificationRoundTripsAndNullDefaultsUnknown() throws Exception {
        for (var type : PensionPlanClassification.values()) {
            var pension = pension();
            pension.setPlanClassification(type);
            assertEquals(type, mapper.readValue(mapper.writeValueAsString(pension), Pension.class)
                    .getPlanClassification());
        }
        var pension = pension();
        pension.setPlanClassification(null);
        assertEquals(PensionPlanClassification.UNKNOWN, pension.getPlanClassification());
    }

    @Test
    void survivorReceiptsFreezeOriginalClassificationAndCurrentRecipient() {
        var pension = pension();
        pension.setPlanClassification(PensionPlanClassification.DEFINED_BENEFIT_414J);
        var receipt = IncomeSourceAttribution.pension(pension, AccountOwnership.SPOUSE,
                new BigDecimal("9000"), 2027);
        pension.setPlanClassification(PensionPlanClassification.DEFINED_CONTRIBUTION);
        assertEquals(AccountOwnership.SPOUSE, receipt.owner().orElseThrow());
        assertEquals(AccountOwnership.PRIMARY, receipt.pension().orElseThrow().originalOwner());
        assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J,
                receipt.pension().orElseThrow().planClassification());
    }
}
