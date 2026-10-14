package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IowaTaxRulesRepositoryTest {
    @Test
    void cachedImmutableRulesRetainIndependent2026ParametersAndSourceYears() {
        var repository = new IowaTaxRulesRepository();
        var rules = repository.load(); assertSame(rules, repository.load());
        assertEquals(new BigDecimal("0.038"), rules.rate());
        assertEquals(new BigDecimal("0.043"), rules.alternativeRate());
        assertEquals(new BigDecimal("9000"), rules.threshold(FilingStatus.SINGLE, false));
        assertEquals(new BigDecimal("13500"), rules.threshold(FilingStatus.MARRIED_FILING_JOINTLY, false));
        assertEquals(new BigDecimal("24000"), rules.threshold(FilingStatus.SINGLE, true));
        assertEquals(new BigDecimal("32000"), rules.threshold(FilingStatus.MARRIED_FILING_JOINTLY, true));
        assertTrue(rules.sources().stream().anyMatch(source -> source.contains("Latest located instructions 2025")));
        assertThrows(UnsupportedOperationException.class, () -> rules.personalCredits().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknownField", "missingRate", "nullRate", "wrongYear", "wrongAge", "duplicateField"})
    void malformedOrUnreviewedRulesCannotLoad(String shape) throws Exception {
        var mapper = new ObjectMapper();
        var tree = mapper.valueToTree(new IowaTaxRulesRepository().load());
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) tree;
        switch (shape) {
            case "unknownField" -> node.put("inventedLaw", 1);
            case "missingRate" -> node.remove("rate");
            case "nullRate" -> node.putNull("rate");
            case "wrongYear" -> node.put("validatedYear", 2025);
            case "wrongAge" -> node.put("retirementAge", 59);
            default -> { }
        }
        String json = node.toString();
        if (shape.equals("duplicateField")) json = json.replace("{", "{\"rate\":0.038,");
        var input = new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> new IowaTaxRulesRepository().parse(input));
    }
}
