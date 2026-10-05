package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.testing.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SinglePersonPlanPersistenceTest {
    @TempDir Path directory;
    private final JsonRetirementPlanRepository repository = new JsonRetirementPlanRepository();
    private final ObjectMapper json = new ObjectMapper();

    private RetirementPlan plan(boolean couple) {
        var primary = new Person("Alex", "Example", LocalDate.of(1960, 2, 3));
        primary.setMortalityCategory(MortalityCategory.MALE);
        var spouse = new Person("Sam", "Example", LocalDate.of(1962, 4, 5));
        spouse.setMortalityCategory(MortalityCategory.FEMALE);
        var primaryAccount = AccountFactory.create(AccountType.TRADITIONAL_IRA, "Primary IRA",
                AccountOwnership.PRIMARY, new BigDecimal("123456.78"));
        primary.addAccount(primaryAccount);
        primary.addIncomeSource(new Pension("Primary pension", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), null, new BigDecimal("1500"), BigDecimal.ZERO));
        var portfolio = new AccountPortfolio(List.of(primaryAccount));
        if (couple) {
            portfolio.addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Spouse Roth",
                    AccountOwnership.SPOUSE, new BigDecimal("25000")));
        }
        return new RetirementPlan(new Household(primary, couple ? spouse : null), portfolio,
                TestDataFactory.planningAssumptions());
    }

    @Test
    void singleRoundTripPreservesRoleDataAndOwnershipWithoutSynthesizingSpouse() throws Exception {
        var original = plan(false);
        var file = directory.resolve("single.json");
        repository.save(original, file);
        var tree = json.readTree(file.toFile());
        assertTrue(tree.path("household").has("spouse"));
        assertTrue(tree.path("household").get("spouse").isNull());
        assertEquals(3, tree.path("household").size()); // original primaryPerson/spouse/expenses shape
        var loaded = repository.load(file);
        assertFalse(loaded.getHousehold().hasSpouse());
        assertTrue(loaded.getHousehold().spouse().isEmpty());
        assertEquals("Alex", loaded.getHousehold().getPrimaryPerson().getFirstName());
        assertEquals(LocalDate.of(1960, 2, 3), loaded.getHousehold().getPrimaryPerson().getBirthDate());
        assertEquals(MortalityCategory.MALE, loaded.getHousehold().getPrimaryPerson().getMortalityCategory());
        assertEquals(AccountOwnership.PRIMARY, loaded.getAccountPortfolio().getAccounts().getFirst().getOwnership());
        assertEquals(new BigDecimal("123456.78"), loaded.getHousehold().getPrimaryPerson().getAccounts().getFirst().getCurrentBalance());
        assertEquals(AccountOwnership.PRIMARY, loaded.getHousehold().getPrimaryPerson().getIncomeSources().getFirst().getOwnership());
        var second = directory.resolve("single-again.json");
        repository.save(loaded, second);
        assertEquals(tree, json.readTree(second.toFile()));
    }

    @Test
    void absentPropertyAlsoMeansNoSpouseButMissingPrimaryFails() throws Exception {
        var file = directory.resolve("missing.json");
        repository.save(plan(false), file);
        var tree = (ObjectNode) json.readTree(file.toFile());
        ((ObjectNode) tree.path("household")).remove("spouse");
        json.writeValue(file.toFile(), tree);
        assertFalse(repository.load(file).getHousehold().hasSpouse());
        ((ObjectNode) tree.path("household")).remove("primaryPerson");
        json.writeValue(file.toFile(), tree);
        assertThrows(java.io.IOException.class, () -> repository.load(file));
    }

    @Test
    void existingCoupleShapeAndAllDataRemainStable() throws Exception {
        var original = plan(true);
        var file = directory.resolve("couple.json");
        repository.save(original, file);
        var before = json.readTree(file.toFile());
        assertEquals(3, before.path("household").size());
        assertEquals("Sam", before.path("household").path("spouse").path("firstName").asText());
        var loaded = repository.load(file);
        assertTrue(loaded.getHousehold().hasSpouse());
        assertEquals("Alex", loaded.getHousehold().getPrimaryPerson().getFirstName());
        assertEquals("Sam", loaded.getHousehold().getSpouse().getFirstName());
        assertSame(loaded.getHousehold().getSpouse(), loaded.getHousehold().spouse().orElseThrow());
        assertEquals(AccountOwnership.SPOUSE, loaded.getAccountPortfolio().getAccounts().get(1).getOwnership());
        var afterFile = directory.resolve("couple-again.json");
        repository.save(loaded, afterFile);
        assertEquals(before, json.readTree(afterFile.toFile()));
    }

    @Test
    void malformedOwnershipOnLoadIsRejectedNotReassigned() throws Exception {
        var file = directory.resolve("invalid.json");
        repository.save(plan(true), file);
        var tree = (ObjectNode) json.readTree(file.toFile());
        ((ObjectNode) tree.path("household")).putNull("spouse");
        json.writeValue(file.toFile(), tree);
        var failure = assertThrows(java.io.IOException.class, () -> repository.load(file));
        assertTrue(failure.getMessage().contains("absent spouse"));
        assertEquals("SPOUSE", json.readTree(file.toFile()).path("accountPortfolio").path("accounts").get(1).path("ownership").asText());
    }

    @Test
    void invalidMutablePlanDoesNotOverwriteFile() throws Exception {
        var plan = plan(false);
        var file = directory.resolve("safe.json");
        repository.save(plan, file);
        String original = Files.readString(file);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Invalid", AccountOwnership.SPOUSE,
                LocalDate.of(2026, 1, 1), null, BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> repository.save(plan, file));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void singleBaselineRoundTripsAndInvalidBaselineIsRejected() throws Exception {
        var plan = plan(false);
        var baselinePlan = plan(false);
        plan.setBaseline(new ProjectionBaseline(new RetirementPlanSnapshot(baselinePlan.getHousehold(),
                baselinePlan.getAccountPortfolio(), baselinePlan.getPlanningAssumptions(), null, List.of()),
                LocalDateTime.of(2026, 1, 1, 0, 0), "Single baseline"));
        var file = directory.resolve("baseline.json");
        repository.save(plan, file);
        assertFalse(repository.load(file).getBaseline().getSnapshot().getHousehold().hasSpouse());
        baselinePlan.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Invalid baseline",
                AccountOwnership.SPOUSE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> repository.save(plan, file));
    }

    @Test
    void invalidPersonOwnedAccountAndCoupleDeathScenarioAreRejectedOnLoad() throws Exception {
        var file = directory.resolve("person-invalid.json");
        repository.save(plan(false), file);
        var tree = (ObjectNode) json.readTree(file.toFile());
        var account = (ObjectNode) tree.path("household").path("primaryPerson").path("accounts").get(0);
        account.put("ownership", "SPOUSE");
        json.writeValue(file.toFile(), tree);
        assertTrue(assertThrows(java.io.IOException.class, () -> repository.load(file)).getMessage().contains("absent spouse"));
        repository.save(plan(false), file);
        tree = (ObjectNode) json.readTree(file.toFile());
        var death = (ObjectNode) tree.path("planningAssumptions").path("deathScenarioAssumptions");
        death.put("deathScenario", "SPOUSE_DIES");
        death.put("deathYear", 2030);
        death.put("survivorClaimingAge", 67);
        json.writeValue(file.toFile(), tree);
        assertTrue(assertThrows(java.io.IOException.class, () -> repository.load(file)).getMessage().contains("Couple death scenarios"));
    }
}
