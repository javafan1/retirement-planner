package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.data.RothConversionDemoFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RothConversionPersistenceTest {

    @Test
    void rothConversionSurvivesJsonRoundTrip()
            throws Exception {

        RetirementPlan original =
                RothConversionDemoFactory
                        .createRetirementPlan();

        Path file =
                Files.createTempFile(
                        "roth-conversion-test",
                        ".json");

        try {

            RetirementPlanRepository repository =
                    new JsonRetirementPlanRepository();

            repository.save(
                    original,
                    file);

            RetirementPlan loaded =
                    repository.load(file);

            assertNotNull(
                    loaded.getRothConversionRequest());

        } finally {

            Files.deleteIfExists(file);
        }
    }

    @Test
    void preservesRothConversionRequestThroughJsonRoundTrip()
            throws Exception {

        RetirementPlan original =
                RothConversionDemoFactory
                        .createRetirementPlan();

        Path file =
                Files.createTempFile(
                        "roth-conversion-test",
                        ".json");

        try {

            RetirementPlanRepository repository =
                    new JsonRetirementPlanRepository();

            repository.save(
                    original,
                    file);

            RetirementPlan loaded =
                    repository.load(file);

            assertNotNull(
                    loaded.getRothConversionRequest());

            assertTrue(
                    loaded.getRothConversionRequest()
                            .isEnabled());

            assertEquals(
                    2026,
                    loaded.getRothConversionRequest()
                            .getStartYear());

            assertEquals(
                    0,
                    new BigDecimal("50000")
                            .compareTo(
                                    loaded
                                            .getRothConversionRequest()
                                            .getAnnualAmount()));

            assertEquals(
                    RothConversionStopRule
                            .FIRST_HOUSEHOLD_RMD,
                    loaded
                            .getRothConversionRequest()
                            .getStopRule());

        } finally {

            Files.deleteIfExists(file);
        }
    }
}