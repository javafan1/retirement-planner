package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonRetirementPlanRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void preservesWithdrawalStrategyWhenPlanIsSavedAndLoaded()
            throws Exception {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file =
                tempDirectory.resolve(
                        "retirement-plan.json");

        /*
         * Save the plan.
         */
        repository.save(
                plan,
                file);

        /*
         * Load a completely new plan object
         * from the JSON file.
         */
        RetirementPlan loadedPlan =
                repository.load(
                        file);

        assertNotNull(
                loadedPlan
                        .getPlanningAssumptions()
                        .getWithdrawalAssumptions());

        assertEquals(
                WithdrawalStrategyType.TAXABLE_FIRST,
                loadedPlan
                        .getPlanningAssumptions()
                        .getWithdrawalAssumptions()
                        .getWithdrawalStrategyType());
    }

    @Test
    void defaultsWithdrawalStrategyWhenLoadingOlderPlan()
            throws Exception {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path currentFile =
                tempDirectory.resolve(
                        "current-plan.json");

        /*
         * First create valid JSON using the
         * current application format.
         */
        repository.save(
                plan,
                currentFile);

        String json =
                java.nio.file.Files.readString(
                        currentFile);

        /*
         * Simulate an older saved plan by removing
         * the withdrawalAssumptions property.
         *
         * Because Jackson pretty-prints the JSON,
         * this pattern removes the complete property
         * including its trailing comma.
         */
        json =
                json.replaceAll(
                        "(?s)\\s*\"withdrawalAssumptions\"\\s*:\\s*\\{.*?\\}\\s*,",
                        "");

        Path legacyFile =
                tempDirectory.resolve(
                        "legacy-plan.json");

        java.nio.file.Files.writeString(
                legacyFile,
                json);

        /*
         * Load the simulated older plan.
         */
        RetirementPlan loadedPlan =
                repository.load(
                        legacyFile);

        assertNotNull(
                loadedPlan
                        .getPlanningAssumptions()
                        .getWithdrawalAssumptions());

        assertEquals(
                WithdrawalStrategyType.TAXABLE_FIRST,
                loadedPlan
                        .getPlanningAssumptions()
                        .getWithdrawalAssumptions()
                        .getWithdrawalStrategyType());
    }




}