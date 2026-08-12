package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.testutil.FederalTaxCalculationBuilder;
import com.daviddunn.retirementplanner.testutil.MichiganTaxCalculationBuilder;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionYearTest {

    @Test
    void calculatesCombinedEffectiveTaxRate() {

        var federalTaxCalculation =
                FederalTaxCalculationBuilder
                        .aFederalTaxCalculation()
                        .withAdjustedGrossIncome(200000)
                        .withFederalIncomeTax(30000)
                        .build();

        var michiganTaxCalculation =
                MichiganTaxCalculationBuilder
                        .aMichiganTaxCalculation()
                        .withIncomeTax(10000)
                        .build();

        ProjectionYear year =
                ProjectionYearBuilder
                        .aProjectionYear()
                        .withFederalTaxCalculation(
                                federalTaxCalculation)
                        .withMichiganTaxCalculation(
                                michiganTaxCalculation)
                        .build();

        assertEquals(
                0,
                new BigDecimal("0.20")
                        .compareTo(
                                year.getCombinedEffectiveTaxRate()));
    }

    @Test
    void combinedEffectiveTaxRateIsZeroWhenAgiIsZero() {

        var federalTaxCalculation =
                FederalTaxCalculationBuilder
                        .aFederalTaxCalculation()
                        .withAdjustedGrossIncome(0)
                        .withFederalIncomeTax(0)
                        .build();

        var michiganTaxCalculation =
                MichiganTaxCalculationBuilder
                        .aMichiganTaxCalculation()
                        .withIncomeTax(0)
                        .build();

        ProjectionYear year =
                ProjectionYearBuilder
                        .aProjectionYear()
                        .withFederalTaxCalculation(
                                federalTaxCalculation)
                        .withMichiganTaxCalculation(
                                michiganTaxCalculation)
                        .build();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        year.getCombinedEffectiveTaxRate()));
    }
}