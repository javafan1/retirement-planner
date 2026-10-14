package com.daviddunn.retirementplanner.domain.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class FederalTaxBaseBreakdownTest {
    @ParameterizedTest
    @CsvSource({"0,16100,-16100,0", "10000,16100,-6100,0", "16100,16100,0,0", "20000,16100,3900,3900", "20000.00,16100.0,3900.0,3900.000"})
    void observesSignedModeledBaseWithoutChangingScalars(String a, String d, String raw, String t) {
        var result = new FederalTaxCalculation(new BigDecimal(a), BigDecimal.ZERO,
                new BigDecimal(d), new BigDecimal(t), new BigDecimal("123.4500"));
        var base = result.getModeledBaseBreakdown();
        assertEquals(0, new BigDecimal(raw).compareTo(base.derivedRawTaxableIncome()));
        assertSame(result.getTaxableIncome(), base.modeledClampedTaxableIncome());
        assertEquals(new BigDecimal("123.4500"), result.getFederalIncomeTax());
        assertEquals(FederalTaxBaseBreakdown.Coverage.MODELED_STANDARD_DEDUCTION_ONLY_V1, base.coverage());
    }

    @Test
    void legacyInconsistentResultStillConstructsButCannotClaimObserverCoverage() {
        var result = new FederalTaxCalculation(BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, result.getTaxableIncome());
        assertThrows(IllegalArgumentException.class, result::getModeledBaseBreakdown);
    }

    @Test
    void observerIsLazyNonserializedAndNotShared() throws Exception {
        var result = new FederalTaxCalculation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        assertNotSame(result.getModeledBaseBreakdown(), result.getModeledBaseBreakdown());
        var json = new ObjectMapper().valueToTree(result);
        assertEquals(5, json.size());
        assertFalse(json.has("modeledBaseBreakdown"));
    }
}
