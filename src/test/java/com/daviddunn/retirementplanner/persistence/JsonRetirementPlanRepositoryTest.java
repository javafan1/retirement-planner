package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaseline;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset;

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
    void preservesJointNonRetirementAccountOwnershipWhenPlanIsSavedAndLoaded()
            throws Exception {

        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();

        plan.getAccountPortfolio().addAccount(
                AccountFactory.create(
                        AccountType.BROKERAGE,
                        "Joint Brokerage",
                        AccountOwnership.JOINT,
                        new BigDecimal("100000")));

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file = tempDirectory.resolve("joint-brokerage-plan.json");
        repository.save(plan, file);

        RetirementPlan loadedPlan = repository.load(file);

        assertEquals(
                AccountOwnership.JOINT,
                loadedPlan.getAccountPortfolio().getAccounts().getFirst()
                        .getOwnership());
    }

    @Test
    void preservesOpeningRmdAccountDataWhenPlanIsSavedAndLoaded()
            throws Exception {

        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();

        var account = AccountFactory.create(
                AccountType.TRADITIONAL_IRA,
                "Traditional IRA",
                AccountOwnership.PRIMARY,
                new BigDecimal("900000"));
        account.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026,
                new BigDecimal("1000000"),
                new BigDecimal("15000")));
        plan.getAccountPortfolio().addAccount(account);

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();
        Path file = tempDirectory.resolve("opening-rmd-plan.json");
        repository.save(plan, file);

        var openingData = repository.load(file)
                .getAccountPortfolio().getAccounts().getFirst()
                .getOpeningRmdAccountData();

        assertEquals(2026, openingData.getDistributionYear());
        assertEquals(new BigDecimal("1000000"),
                openingData.getPriorDecember31Balance());
        assertEquals(new BigDecimal("15000"),
                openingData.getRmdAlreadyDistributedBeforeProjection());
    }

    @Test
    void rejectsLegacyJointRetirementAccountWithoutSilentlyChoosingAnOwner()
            throws Exception {

        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();

        plan.getAccountPortfolio().addAccount(
                AccountFactory.create(
                        AccountType.TRADITIONAL_IRA,
                        "Legacy Joint IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000")));

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file = tempDirectory.resolve("legacy-joint-ira-plan.json");
        repository.save(plan, file);

        String invalidLegacyJson = java.nio.file.Files.readString(file)
                .replaceFirst(
                        "\\\"ownership\\\"\\s*:\\s*\\\"PRIMARY\\\"",
                        "\\\"ownership\\\" : \\\"JOINT\\\"");
        java.nio.file.Files.writeString(file, invalidLegacyJson);

        IOException exception = assertThrows(
                IOException.class,
                () -> repository.load(file));

        assertTrue(exception.getMessage().contains(
                "JOINT ownership is not permitted"));
    }

    @Test
    void preservesFutureFederalMarginalRateChangeAndDefaultsLegacyJson()
            throws Exception {

        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();

        TaxAssumptions tax = plan.getPlanningAssumptions()
                .getTaxAssumptions();

        plan.setPlanningAssumptions(new PlanningAssumptions(
                plan.getPlanningAssumptions().getEconomicAssumptions(),
                new TaxAssumptions(
                        tax.getFederalTaxBracketGrowthRate(),
                        tax.getStandardDeductionGrowthRate(),
                        tax.getStateIncomeTaxRate(),
                        tax.getLocalIncomeTaxRate(),
                        tax.getFilingStatus(),
                        tax.getEstimatedHeirTaxRateOnTaxDeferredAssets(),
                        new BigDecimal("0.03"),
                        2031),
                plan.getPlanningAssumptions().getWithdrawalAssumptions(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                plan.getPlanningAssumptions().getProjectionLengthYears(),
                plan.getPlanningAssumptions().getProjectionStartDate()));

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path currentFile = tempDirectory.resolve("current-plan.json");
        repository.save(plan, currentFile);

        RetirementPlan loaded = repository.load(currentFile);

        assertEquals(
                new BigDecimal("0.03"),
                loaded.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateAdjustment());

        assertEquals(
                2031,
                loaded.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateEffectiveYear());

        TaxAssumptions loadedTax =
                loaded.getPlanningAssumptions()
                        .getTaxAssumptions();

        loaded.setPlanningAssumptions(
                new PlanningAssumptions(
                        loaded.getPlanningAssumptions()
                                .getEconomicAssumptions(),
                        new TaxAssumptions(
                                loadedTax.getFederalTaxBracketGrowthRate(),
                                loadedTax.getStandardDeductionGrowthRate(),
                                loadedTax.getStateIncomeTaxRate(),
                                loadedTax.getLocalIncomeTaxRate(),
                                loadedTax.getFilingStatus(),
                                loadedTax.getEstimatedHeirTaxRateOnTaxDeferredAssets(),
                                null,
                                null),
                        loaded.getPlanningAssumptions()
                                .getWithdrawalAssumptions(),
                        loaded.getPlanningAssumptions()
                                .getDeathScenarioAssumptions(),
                        loaded.getPlanningAssumptions()
                                .getProjectionLengthYears(),
                        loaded.getPlanningAssumptions()
                                .getProjectionStartDate()));

        Path clearedFile =
                tempDirectory.resolve(
                        "cleared-plan.json");

        repository.save(loaded, clearedFile);

        RetirementPlan cleared =
                repository.load(clearedFile);

        assertEquals(
                null,
                cleared.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateAdjustment());

        assertEquals(
                null,
                cleared.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateEffectiveYear());

        String legacyJson = java.nio.file.Files.readString(currentFile)
                .replaceAll(
                        "(?s),\\s*\"futureFederalMarginalRateAdjustment\"\\s*:\\s*[^,\\n]+\\s*,\\s*\"futureFederalMarginalRateEffectiveYear\"\\s*:\\s*[^\\n}]+",
                        "");

        Path legacyFile = tempDirectory.resolve("legacy-plan.json");
        java.nio.file.Files.writeString(legacyFile, legacyJson);

        RetirementPlan legacyPlan = repository.load(legacyFile);

        assertEquals(
                null,
                legacyPlan.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateAdjustment());

        assertEquals(
                null,
                legacyPlan.getPlanningAssumptions().getTaxAssumptions()
                        .getFutureFederalMarginalRateEffectiveYear());
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

    @Test
    void preservesNonInvestableAssetsWhenPlanIsSavedAndLoaded()
            throws Exception {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        plan.addNonInvestableAsset(
                new NonInvestableAsset(
                        "Primary Residence",
                        new BigDecimal("800000"),
                        new BigDecimal("0.03")));

        plan.addNonInvestableAsset(
                new NonInvestableAsset(
                        "Comic Collection",
                        new BigDecimal("100000"),
                        new BigDecimal("0.04")));

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file =
                tempDirectory.resolve(
                        "non-investable-assets.json");

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
                loadedPlan.getNonInvestableAssets());

        assertEquals(
                2,
                loadedPlan
                        .getNonInvestableAssets()
                        .size());

        var home =
                loadedPlan
                        .getNonInvestableAssets()
                        .get(0);

        assertEquals(
                "Primary Residence",
                home.getName());

        assertEquals(
                new BigDecimal("800000"),
                home.getCurrentValue());

        assertEquals(
                new BigDecimal("0.03"),
                home.getAnnualGrowthRate());

        var comics =
                loadedPlan
                        .getNonInvestableAssets()
                        .get(1);

        assertEquals(
                "Comic Collection",
                comics.getName());

        assertEquals(
                new BigDecimal("100000"),
                comics.getCurrentValue());

        assertEquals(
                new BigDecimal("0.04"),
                comics.getAnnualGrowthRate());
    }

    @Test
    void preservesProjectionBaselineWhenPlanIsSavedAndLoaded()
            throws Exception {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        RetirementPlanSnapshot snapshot =
                new RetirementPlanSnapshot(
                        plan.getHousehold(),
                        plan.getAccountPortfolio(),
                        plan.getPlanningAssumptions(),
                        plan.getRothConversionRequest(),
                        plan.getNonInvestableAssets());

        ProjectionBaseline baseline =
                new ProjectionBaseline(
                        snapshot,
                        LocalDateTime.of(
                                2026,
                                8,
                                22,
                                16,
                                0),
                        "Original Retirement Plan");

        plan.setBaseline(
                baseline);

        JsonRetirementPlanRepository repository =
                new JsonRetirementPlanRepository();

        Path file =
                tempDirectory.resolve(
                        "baseline-plan.json");

        repository.save(
                plan,
                file);

        RetirementPlan loadedPlan =
                repository.load(
                        file);

        assertNotNull(
                loadedPlan.getBaseline());

        assertEquals(
                "Original Retirement Plan",
                loadedPlan.getBaseline()
                        .getDescription());

        assertEquals(
                LocalDateTime.of(
                        2026,
                        8,
                        22,
                        16,
                        0),
                loadedPlan.getBaseline()
                        .getSavedAt());

        assertNotNull(
                loadedPlan.getBaseline()
                        .getSnapshot());

        assertNotNull(
                loadedPlan.getBaseline()
                        .getSnapshot()
                        .getHousehold());

        assertNotNull(
                loadedPlan.getBaseline()
                        .getSnapshot()
                        .getAccountPortfolio());

        assertNotNull(
                loadedPlan.getBaseline()
                        .getSnapshot()
                        .getPlanningAssumptions());

        assertNotNull(
                loadedPlan.getBaseline()
                        .getSnapshot()
                        .getNonInvestableAssets());
    }

}
