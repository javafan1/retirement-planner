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
    @org.junit.jupiter.api.io.TempDir
    Path directory;

    @Test
    void pendingScheduleRoundTripsThroughPlanAndFrozenInputs() throws Exception {
        var plan = RothConversionDemoFactory.createRetirementPlan();
        var schedule = new RothConversionRequest.Scheduling(1, 2100,
                com.daviddunn.retirementplanner.domain.roth.RothRmdSchedulingPolicy.SKIP_POSITIVE_RMD_YEARS);
        var request = new RothConversionRequest(false, 2026, new BigDecimal("5000.00"), null,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL, null, schedule);
        plan.setRothConversionRequest(request);
        var repository = new JsonRetirementPlanRepository();
        var path = directory.resolve("dormant-roth.json");
        repository.save(plan, path);
        var reopened = repository.load(path);
        var frozen = com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.freeze(reopened);
        var snapshot = com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.restoreInputs(frozen);
        assertTrue(com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.same(request,
                reopened.getRothConversionRequest()));
        assertTrue(com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.same(request,
                snapshot.getRothConversionRequest()));
    }

    @Test
    void rothConversionSurvivesJsonRoundTrip()
            throws Exception {

        RetirementPlan original =
                RothConversionDemoFactory
                        .createRetirementPlan();

        Path file =
                directory.resolve("roth-conversion-test.json");

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
                directory.resolve("roth-conversion-test.json");

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
                directory.resolve("roth-conversion-test.json");

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
                directory.resolve("roth-conversion-test.json");

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

    @Test
    void persistsCustomTargetTaxableIncomeRothConversionRequest()
            throws Exception {

        RetirementPlan originalPlan =
                RothConversionDemoFactory
                        .createRetirementPlan();

        BigDecimal target =
                new BigDecimal("180000.00");

        originalPlan.setRothConversionRequest(
                new RothConversionRequest(
                        true,
                        2026,
                        BigDecimal.ZERO,
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD,
                        RothConversionStrategy
                                .CUSTOM_TAXABLE_INCOME_TARGET,
                        RothConversionFrequency.ANNUAL,
                        target));

        Path file =
                directory.resolve("roth-conversion-test.json");

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

            assertNotNull(loadedRequest);

            assertEquals(
                    RothConversionStrategy
                            .CUSTOM_TAXABLE_INCOME_TARGET,
                    loadedRequest.getStrategy());

            assertEquals(
                    target,
                    loadedRequest
                            .getCustomTargetTaxableIncome());

        } finally {

            Files.deleteIfExists(file);
        }
    }
}
