package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.data.RothConversionDemoFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
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
    void preservesAnnualFixedAmountRothConversionRequestThroughJsonRoundTrip()
            throws Exception {

        RetirementPlan originalPlan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        new BigDecimal("50000"),
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .FIXED_AMOUNT,
                        RothConversionFrequency
                                .ANNUAL);

        originalPlan.setRothConversionRequest(
                request);

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

            RetirementPlan loaded =
                    repository.load(file);

            RothConversionRequest loadedRequest =
                    loaded.getRothConversionRequest();

            assertNotNull(
                    loadedRequest);

            assertTrue(
                    loadedRequest.isEnabled());

            assertEquals(
                    2026,
                    loadedRequest.getStartYear());

            assertEquals(
                    0,
                    new BigDecimal("50000")
                            .compareTo(
                                    loadedRequest
                                            .getAnnualAmount()));

            assertEquals(
                    RothConversionStopRule
                            .FIRST_HOUSEHOLD_RMD,
                    loadedRequest.getStopRule());

            assertEquals(
                    RothConversionStrategy
                            .FIXED_AMOUNT,
                    loadedRequest.getStrategy());

            assertEquals(
                    RothConversionFrequency
                            .ANNUAL,
                    loadedRequest.getFrequency());

        } finally {

            Files.deleteIfExists(file);
        }
    }


    @Test
    void persistsOneTimeRothConversionRequest()
            throws IOException {

        RetirementPlan originalPlan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2027,
                        new BigDecimal("50000"),
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .FIXED_AMOUNT,
                        RothConversionFrequency
                                .ONE_TIME);

        originalPlan.setRothConversionRequest(
                request);

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
                    loadedPlan
                            .getRothConversionRequest();

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
                    RothConversionStopRule
                            .FIRST_HOUSEHOLD_RMD,
                    loadedRequest.getStopRule());

            assertEquals(
                    RothConversionStrategy
                            .FIXED_AMOUNT,
                    loadedRequest.getStrategy());

            assertEquals(
                    RothConversionFrequency
                            .ONE_TIME,
                    loadedRequest.getFrequency());

        } finally {

            Files.deleteIfExists(file);
        }
    }


    @Test
    void persistsFill22PercentBracketRothConversionRequest()
            throws Exception {

        RetirementPlan originalPlan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        RothConversionRequest request =
                new RothConversionRequest(
                        true,
                        2026,
                        BigDecimal.ZERO,
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .FILL_22_PERCENT_BRACKET,
                        RothConversionFrequency
                                .ANNUAL);

        originalPlan.setRothConversionRequest(
                request);

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
                    loadedPlan
                            .getRothConversionRequest();

            assertNotNull(
                    loadedRequest);

            assertTrue(
                    loadedRequest.isEnabled());

            assertEquals(
                    2026,
                    loadedRequest.getStartYear());

            assertEquals(
                    0,
                    BigDecimal.ZERO.compareTo(
                            loadedRequest
                                    .getAnnualAmount()));

            assertEquals(
                    RothConversionStopRule
                            .FIRST_HOUSEHOLD_RMD,
                    loadedRequest.getStopRule());

            assertEquals(
                    RothConversionStrategy
                            .FILL_22_PERCENT_BRACKET,
                    loadedRequest.getStrategy());

            assertEquals(
                    RothConversionFrequency
                            .ANNUAL,
                    loadedRequest.getFrequency());

        } finally {

            Files.deleteIfExists(file);
        }
    }
}