package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseEscalationCompatibilityTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test void preChangeLegacyInputAndAnalyzerDigestsRemainExact() throws Exception {
        // Captured twice from the unchanged pre-14A production source; not regenerated expected financial outputs.
        var expected = Map.of(
                GrowthCategory.GENERAL, List.of("55adfe6f0884d0f10a8dbcae51dc73970572eb8ec164d28fcf03a9339a914f89", "c4e1afc9e33f45322eb6bfaac43915339f22d3e2f68bbbdd3be9c5260bc4ae45", "828687dbe64af493e7e79cd87bc15a77825f960b997ee3d146778b5b082c8850"),
                GrowthCategory.HEALTHCARE, List.of("82fa9a6b4dc17b957acc422b1599a9f5049c184ba843a4914ea2f5470da75051", "9fe08fe562cda04fe156791d740c3c32f97353ddeb99a50568d05e468e3d337e", "c29d4784c79707c2e068464d815f114bc260f9d504c61965e14be16615a96da7"));
        for (var category : expected.keySet()) {
            var plan = MonteCarloFixtures.household();
            for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
            plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("80000.0100"), category));
            assertEquals(expected.get(category).get(0), CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan)));
            assertEquals(expected.get(category).get(1), new AnalysisFingerprintService().fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none()));
            var settings = new MonteCarloSettings(3, 123L, new BigDecimal("0.045"), new BigDecimal("0.10"));
            assertEquals(expected.get(category).get(2), new MonteCarloFingerprintService().assess(
                    MonteCarloFingerprintService.standalone(plan, settings, null), MonteCarloFingerprintService.Contract.EXACT_V1).fingerprint());
            assertEquals("{\"description\":\"Spending\",\"annualAmount\":80000.0100,\"growthCategory\":\"" + category.name()
                    + "\",\"startDate\":null,\"endDate\":null,\"expenseType\":\"RECURRING\"}", JSON.writeValueAsString(plan.getHousehold().getExpenses().getFirst()));
        }
    }

    @Test void noneRoundTripsThroughPlanAndFrozenInputsWithoutInventingExtraProperties() throws Exception {
        var plan = MonteCarloFixtures.household();
        var before = CompleteProjectionSnapshot.freeze(plan);
        var fingerprints = new AnalysisFingerprintService();
        var integratedBefore = fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none());
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE,
                java.time.LocalDate.of(2027, 1, 1), java.time.LocalDate.of(2027, 1, 1), new BigDecimal("0.01"),
                null, null, List.of(), List.of(), null);
        var ssBefore = fingerprints.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, inputs);
        var mcSettings = new MonteCarloSettings(3, 123L, new BigDecimal("0.045"), new BigDecimal("0.10"));
        var mcBefore = new MonteCarloFingerprintService().assess(MonteCarloFingerprintService.standalone(plan, mcSettings, null)).fingerprint();
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(), GrowthCategory.valueOf("NONE"), old.getStartDate(), old.getEndDate(), old.getExpenseType()));
        var configured = CompleteProjectionSnapshot.freeze(plan);
        assertNotEquals(before, configured);
        assertNotEquals(integratedBefore, fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none()));
        assertNotEquals(mcBefore, new MonteCarloFingerprintService().assess(MonteCarloFingerprintService.standalone(plan, mcSettings, null)).fingerprint());
        assertEquals(ssBefore, fingerprints.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, inputs));
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("plan.json"); repository.save(plan, file);
        var restored = repository.load(file);
        assertEquals(configured, CompleteProjectionSnapshot.freeze(restored));
        assertEquals("NONE", restored.getHousehold().getExpenses().getFirst().getGrowthCategory().name());
        assertEquals(configured, CompleteProjectionSnapshot.freeze(CompleteProjectionSnapshot.restoreInputs(configured)));
        assertEquals(before, CompleteProjectionSnapshot.freeze(CompleteProjectionSnapshot.restoreInputs(before)));
        assertFalse(JSON.writeValueAsString(restored.getHousehold().getExpenses().getFirst()).contains("volatility"));
    }
}
