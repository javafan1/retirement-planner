package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.data.RothConversionDemoFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

    @Test
    void persistsOneTimeRothConversionRequest() throws IOException {

        RetirementPlan originalPlan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2027,
                        new BigDecimal("50000"),
                        RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionFrequency.ONE_TIME);

        originalPlan.setRothConversionRequest(request);

        Path file =
                Files.createTempFile(
                        "roth-conversion-test",
                        ".json");

        try {

            RetirementPlanRepository repository =
                    new JsonRetirementPlanRepository();

            repository.save(
                    originalPlan,
                    file);

            RetirementPlan loadedPlan =
                    repository.load(file);

            RothConversionRequest loadedRequest =
                    loadedPlan.getRothConversionRequest();

            assertNotNull(
                    loadedRequest);

            assertTrue(
                    loadedRequest.isEnabled());

            assertEquals(
                    2027,
                    loadedRequest.getStartYear());

            assertEquals(
                    0,
                    new BigDecimal("50000")
                            .compareTo(
                                    loadedRequest
                                            .getAnnualAmount()));

            assertEquals(
                    RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                    loadedRequest.getStopRule());

        } finally {

            Files.deleteIfExists(file);
        }
    }
}