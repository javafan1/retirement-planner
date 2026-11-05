package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class EconomicAssumptionsTest {

    private static final BigDecimal INVESTMENT = new BigDecimal("0.070");
    private static final BigDecimal INFLATION = new BigDecimal("0.025");
    private static final BigDecimal HEALTHCARE = new BigDecimal("0.035");
    private static final BigDecimal COLA = new BigDecimal("0.020");

    @Test
    void defaultsNewFieldsAndRetainsEveryExistingConstructorSignature() {
        assertDefaults(new EconomicAssumptions(INVESTMENT, INFLATION));
        assertDefaults(new EconomicAssumptions(INVESTMENT, INFLATION, HEALTHCARE, COLA));
        assertDefaults(new EconomicAssumptions(INVESTMENT, null, INFLATION, HEALTHCARE, COLA));
    }

    @Test
    void acceptsBoundaryAndDecimalPercentagesAndFourDigitYears() {
        for (String percent : new String[]{"0", "0.1", "10", "17", "22", "100"}) {
            for (int year : new int[]{1000, 2032, 9999}) {
                var assumptions = create(percent, year);
                assertEquals(new BigDecimal(percent), assumptions.getSocialSecurityBenefitReductionPercent());
                assertEquals(year, assumptions.getSocialSecurityBenefitReductionEffectiveYear());
            }
        }
    }

    @Test
    void rejectsOutOfRangePercentagesAndYears() {
        assertThrows(IllegalArgumentException.class, () -> create("-0.01", 2032));
        assertThrows(IllegalArgumentException.class, () -> create("100.01", 2032));
        assertThrows(IllegalArgumentException.class, () -> create("1", 999));
        assertThrows(IllegalArgumentException.class, () -> create("1", 10000));
    }

    @Test
    void jacksonDefaultsMissingAndNullValuesAndRejectsInvalidExplicitValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String legacy = """
                {"expectedAnnualInvestmentReturn":0.070,"generalInflationRate":0.025,
                 "healthcareInflationRate":0.035,"socialSecurityColaRate":0.020}
                """;
        assertDefaults(mapper.readValue(legacy, EconomicAssumptions.class));

        String explicitNull = """
                {"expectedAnnualInvestmentReturn":0.070,"generalInflationRate":0.025,
                 "healthcareInflationRate":0.035,"socialSecurityColaRate":0.020,
                 "socialSecurityBenefitReductionPercent":null,
                 "socialSecurityBenefitReductionEffectiveYear":null}
                """;
        assertDefaults(mapper.readValue(explicitNull, EconomicAssumptions.class));

        for (String invalid : new String[]{"-1", "100.01"}) {
            String json = legacy.substring(0, legacy.lastIndexOf('}'))
                    + ",\"socialSecurityBenefitReductionPercent\":" + invalid + "}";
            assertThrows(Exception.class, () -> mapper.readValue(json, EconomicAssumptions.class));
        }
        for (int invalid : new int[]{999, 10000}) {
            String json = legacy.substring(0, legacy.lastIndexOf('}'))
                    + ",\"socialSecurityBenefitReductionEffectiveYear\":" + invalid + "}";
            assertThrows(Exception.class, () -> mapper.readValue(json, EconomicAssumptions.class));
        }
    }

    @Test
    void jsonRoundTripPreservesValuesAndScaleAsImmutableValue() throws Exception {
        var source = create("17.2500", 2046);
        var mapper = new ObjectMapper();
        var restored = mapper.readValue(mapper.writeValueAsBytes(source), EconomicAssumptions.class);

        assertEquals(source, restored);
        assertEquals(source.hashCode(), restored.hashCode());
        assertEquals(new BigDecimal("17.2500"), restored.getSocialSecurityBenefitReductionPercent());
        assertEquals(2046, restored.getSocialSecurityBenefitReductionEffectiveYear());
        assertTrue(mapper.writeValueAsString(source).contains("socialSecurityBenefitReductionPercent"));
        assertTrue(mapper.writeValueAsString(source).contains("socialSecurityBenefitReductionEffectiveYear"));
    }

    private static EconomicAssumptions create(String percent, int year) {
        return new EconomicAssumptions(INVESTMENT, INFLATION, HEALTHCARE, COLA,
                new BigDecimal(percent), year);
    }

    private static void assertDefaults(EconomicAssumptions assumptions) {
        assertEquals(BigDecimal.ZERO, assumptions.getSocialSecurityBenefitReductionPercent());
        assertEquals(2032, assumptions.getSocialSecurityBenefitReductionEffectiveYear());
    }
}
