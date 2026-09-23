package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PersonMortalityOwnershipTest {
    @TempDir Path directory;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);

    @Test
    void storesBothCategoriesAndRejectsIntentionalNull() {
        Person person = new Person();
        assertNull(person.getMortalityCategory());
        person.setMortalityCategory(MortalityCategory.MALE);
        assertEquals(MortalityCategory.MALE, person.getMortalityCategory());
        person.setMortalityCategory(MortalityCategory.FEMALE);
        assertEquals(MortalityCategory.FEMALE, person.getMortalityCategory());
        assertThrows(NullPointerException.class, () -> person.setMortalityCategory(null));
    }

    @Test
    void repositoryRoundTripAndLegacyMissingOrNullRemainUnresolved() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("plan.json");
        repository.save(LongevityWeightedAnalysisRequestFactoryTest.plan(), file);
        var loaded = repository.load(file);
        assertEquals(MortalityCategory.MALE, loaded.getHousehold().getPrimaryPerson().getMortalityCategory());
        assertEquals(MortalityCategory.FEMALE, loaded.getHousehold().getSpouse().getMortalityCategory());
        var mapper = new ObjectMapper();
        var json = mapper.readTree(file.toFile());
        ((ObjectNode) json.path("household").path("primaryPerson")).remove("mortalityCategory");
        ((ObjectNode) json.path("household").path("spouse")).putNull("mortalityCategory");
        mapper.writeValue(file.toFile(), json);
        loaded = repository.load(file);
        assertNull(loaded.getHousehold().getPrimaryPerson().getMortalityCategory());
        assertNull(loaded.getHousehold().getSpouse().getMortalityCategory());
        repository.save(loaded, file);
        assertNull(repository.load(file).getHousehold().getPrimaryPerson().getMortalityCategory());
    }

    @Test
    void personOverridesCompatibilityArgumentsAndNextRequestUsesChanges() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var first = context(plan);
        assertEquals(SocialSecurityMortalityCategory.MALE, first.primaryMortalityCategory());
        assertEquals(SocialSecurityMortalityCategory.FEMALE, first.spouseMortalityCategory());
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.MALE);
        var second = context(plan);
        assertEquals(SocialSecurityMortalityCategory.FEMALE, second.primaryMortalityCategory());
        assertEquals(SocialSecurityMortalityCategory.MALE, second.spouseMortalityCategory());
        assertEquals(first.primaryMortalityAdjustment(), second.primaryMortalityAdjustment());
        assertEquals(first.spouseMortalityAdjustment(), second.spouseMortalityAdjustment());
        assertNotEquals(first.request().retirementGridRequest().primaryMortality().probabilities(),
                second.request().retirementGridRequest().primaryMortality().probabilities());
        assertEquals(SocialSecurityMortalityCategory.MALE, first.primaryMortalityCategory());
    }

    @Test
    void bothFactoriesRejectEachMissingPersonCategoryBeforeProcessing() throws Exception {
        for (String owner : new String[] {"Primary", "Spouse", "Primary and Spouse"}) {
            var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
            // Model the legacy JSON path, rather than deliberately setting invalid new data.
            var mapper = new ObjectMapper().findAndRegisterModules();
            ObjectNode json = mapper.valueToTree(plan);
            if (owner.contains("Primary")) {
                ((ObjectNode) json.path("household").path("primaryPerson")).remove("mortalityCategory");
            }
            if (owner.contains("Spouse")) {
                ((ObjectNode) json.path("household").path("spouse")).remove("mortalityCategory");
            }
            var legacy = mapper.treeToValue(json, RetirementPlan.class);
            String expected = "Mortality category is required for " + owner
                    + " before running longevity analysis. Set the values in Person information.";
            assertEquals(expected, assertThrows(IllegalArgumentException.class, () -> context(legacy)).getMessage());
            assertEquals(expected, assertThrows(IllegalArgumentException.class,
                    () -> LongevityWeightedAnalysisRequestFactoryTest.capture(legacy)).getMessage());
        }
    }

    @Test
    void weightedSnapshotFreezesPersonValuesAndNextCaptureUsesEdits() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var first = LongevityWeightedAnalysisRequestFactoryTest.capture(plan);
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.MALE);
        var second = LongevityWeightedAnalysisRequestFactoryTest.capture(plan);
        var request = new LongevityWeightedAnalysisRequestFactory().create(first,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(SocialSecurityMortalityCategory.MALE, request.longevityScenarios().assumptions().primaryCategory());
        assertEquals(SocialSecurityMortalityCategory.FEMALE, request.longevityScenarios().assumptions().spouseCategory());
        assertEquals(SocialSecurityMortalityCategory.FEMALE, second.longevity.primaryCategory());
        assertEquals(SocialSecurityMortalityCategory.MALE, second.longevity.spouseCategory());
        assertEquals(first.longevity.primaryAdjustment(), second.longevity.primaryAdjustment());
    }

    @Test
    void unchangedCategoriesAndFactorsProduceExactlyTheExistingDistributionsAndJointScenarios() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var oldInputs = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), DATE, table.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        var factory = new HouseholdLongevityScenarioFactory(table);
        var expected = factory.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), oldInputs);
        var actual = context(plan).request().retirementGridRequest();
        assertEquals(expected.primary().distribution().probabilities(), actual.primaryMortality().probabilities());
        assertEquals(expected.spouse().distribution().probabilities(), actual.spouseMortality().probabilities());
        var weighted = new LongevityWeightedAnalysisRequestFactory();
        var snapshot = weighted.capture(plan, SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(), DATE, DATE, BigDecimal.ZERO, 0, 0,
                CurrentStrategyBaseline.fromPlan(plan));
        var scenarios = weighted.create(snapshot, AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                .longevityScenarios();
        assertEquals(expected.assumptions(), scenarios.assumptions());
        assertEquals(expected.scenarios(), scenarios.scenarios());
    }

    private static SocialSecurityStrategyAnalysisContext context(RetirementPlan plan) {
        // Intentionally opposite legacy arguments: Person must always win.
        return new SocialSecurityStrategyAnalysisRequestFactory().create(plan,
                SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityCategory.MALE,
                BigDecimal.ZERO, DATE);
    }
}
