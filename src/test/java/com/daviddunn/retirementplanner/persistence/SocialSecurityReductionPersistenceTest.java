package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityReductionPersistenceTest {
    @TempDir Path directory;

    @Test
    void planSaveLoadAndSecondSavePreserveNewValues() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var plan = planWith("17.2500", 2046);
        var first = directory.resolve("first.json");
        var second = directory.resolve("second.json");

        repository.save(plan, first);
        var loaded = repository.load(first);
        assertValues(loaded, "17.2500", 2046);
        repository.save(loaded, second);
        assertValues(repository.load(second), "17.2500", 2046);
    }

    @Test
    void legacyPlanDefaultsWithoutRewritingTheLoadedDocument() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        var plan = RetirementPlanFactory.createEmptyPlan();
        Path file = directory.resolve("legacy.json");
        repository.save(plan, file);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(file.toFile());
        var economics = (com.fasterxml.jackson.databind.node.ObjectNode)
                document.path("planningAssumptions").path("economicAssumptions");
        economics.remove("socialSecurityBenefitReductionPercent");
        economics.remove("socialSecurityBenefitReductionEffectiveYear");
        Files.write(file, mapper.writeValueAsBytes(document));
        byte[] beforeLoad = Files.readAllBytes(file);

        assertValues(repository.load(file), "0", 2032);
        assertArrayEquals(beforeLoad, Files.readAllBytes(file), "Loading legacy data must not rewrite the plan file.");
    }

    @Test
    void explicitNullPlanPropertiesUseCompatibilityDefaults() throws Exception {
        var repository = new JsonRetirementPlanRepository();
        Path file = directory.resolve("null-fields.json");
        repository.save(RetirementPlanFactory.createEmptyPlan(), file);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(file.toFile());
        var economics = (com.fasterxml.jackson.databind.node.ObjectNode)
                document.path("planningAssumptions").path("economicAssumptions");
        economics.putNull("socialSecurityBenefitReductionPercent");
        economics.putNull("socialSecurityBenefitReductionEffectiveYear");
        Files.write(file, mapper.writeValueAsBytes(document));

        assertValues(repository.load(file), "0", 2032);
    }

    @Test
    void baselineAndProjectionSnapshotsPreserveInputsAndConfiguredIdentity() throws Exception {
        RetirementPlan plan = planWith("0", 2040);
        var baseline = RetirementPlanSnapshot.fromRetirementPlan(plan);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var restoredBaseline = mapper.readValue(mapper.writeValueAsBytes(baseline), RetirementPlanSnapshot.class);
        assertEquals(new BigDecimal("0"), restoredBaseline.getPlanningAssumptions()
                .getEconomicAssumptions().getSocialSecurityBenefitReductionPercent());
        assertEquals(2040, restoredBaseline.getPlanningAssumptions()
                .getEconomicAssumptions().getSocialSecurityBenefitReductionEffectiveYear());

        String frozen = CompleteProjectionSnapshot.freeze(planWith("17.25", 2040));
        assertTrue(frozen.contains("socialSecurityBenefitReductionPercent"));
        assertTrue(frozen.contains("socialSecurityBenefitReductionEffectiveYear"));
        var restored = CompleteProjectionSnapshot.restoreInputs(frozen);
        assertEquals(2040, restored.getPlanningAssumptions().getEconomicAssumptions()
                .getSocialSecurityBenefitReductionEffectiveYear());
        assertEquals(CompleteProjectionSnapshot.digest(frozen),
                CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(
                        planWith("17.25", 2040))));
        assertNotEquals(CompleteProjectionSnapshot.digest(frozen),
                CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(
                        planWith("0", 2041))),
                "Effective year is configured input even when the percent is zero.");
    }

    private static RetirementPlan planWith(String percent, int year) {
        RetirementPlan plan = RetirementPlanFactory.createEmptyPlan();
        var old = plan.getPlanningAssumptions();
        var economic = old.getEconomicAssumptions();
        var replacement = new EconomicAssumptions(
                economic.getExpectedAnnualInvestmentReturn(),
                economic.getGeneralInflationRate(),
                economic.getHealthcareInflationRate(),
                economic.getSocialSecurityColaRate(),
                new BigDecimal(percent), year);
        plan.setPlanningAssumptions(new PlanningAssumptions(replacement,
                old.getTaxAssumptions(), old.getWithdrawalAssumptions(), old.getDeathScenarioAssumptions(),
                old.getProjectionLengthYears(), old.getProjectionStartDate(), old.getStateTaxAssumptions()));
        return plan;
    }

    private static void assertValues(RetirementPlan plan, String percent, int year) {
        var economics = plan.getPlanningAssumptions().getEconomicAssumptions();
        assertEquals(new BigDecimal(percent), economics.getSocialSecurityBenefitReductionPercent());
        assertEquals(year, economics.getSocialSecurityBenefitReductionEffectiveYear());
    }
}
