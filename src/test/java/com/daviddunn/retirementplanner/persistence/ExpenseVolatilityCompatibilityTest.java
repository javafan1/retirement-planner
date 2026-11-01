package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class ExpenseVolatilityCompatibilityTest {
    @TempDir Path directory;
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    static final String LEGACY = "{\"description\":\"Spending\",\"annualAmount\":80000.0100,\"growthCategory\":\"GENERAL\",\"startDate\":null,\"endDate\":null,\"expenseType\":\"RECURRING\"}";

    public static void volatility(RetirementPlan plan, String value) {
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(), old.getGrowthCategory(),
                old.getStartDate(), old.getEndDate(), old.getExpenseType(), new BigDecimal(value)));
    }

    @ParameterizedTest @ValueSource(strings = {"null", "0", "0.00", "0E+3"})
    void disabledSerializesExactlyAsLegacyAndStrictReadersKeepOriginalRequirements(String value) throws Exception {
        var strict = JSON.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        var disabled = LEGACY.substring(0, LEGACY.length() - 1) + ",\"annualExpenseVolatility\":" + value + "}";
        assertEquals(LEGACY, JSON.writeValueAsString(strict.readValue(disabled, Expense.class)));
        assertEquals(LEGACY, JSON.writeValueAsString(strict.readValue(LEGACY, Expense.class)));
        assertThrows(Exception.class, () -> strict.readValue(LEGACY.replace(",\"startDate\":null", ""), Expense.class));
        assertThrows(Exception.class, () -> strict.readValue(LEGACY.replace("80000.0100", "null"), Expense.class));
    }

    @Test void precisionPlanSaveReloadSaveAsCloneAndConfiguredDocuments() throws Exception {
        var plan = MonteCarloFixtures.household();
        volatility(plan, "0.1234567890123456789000");
        String original = CompleteProjectionSnapshot.freeze(plan);
        var repository = new JsonRetirementPlanRepository();
        var first = directory.resolve("first.json"); repository.save(plan, first);
        var loaded = repository.load(first);
        assertEquals(original, CompleteProjectionSnapshot.freeze(loaded));
        repository.save(loaded, directory.resolve("second.json"));
        assertEquals(original, CompleteProjectionSnapshot.freeze(repository.load(directory.resolve("second.json"))));
        assertEquals(original, CompleteProjectionSnapshot.freeze(new RetirementPlanScenarioCopyService().copy(plan)));
        assertEquals(original, CompleteProjectionSnapshot.freeze(CompleteProjectionSnapshot.restoreInputs(original)));
        var strict = JSON.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        var expense = loaded.getHousehold().getExpenses().getFirst();
        assertEquals(new BigDecimal("0.1234567890123456789000"), strict.readValue(JSON.writeValueAsString(expense), Expense.class).getAnnualExpenseVolatility());
    }

    @Test void validationAndZeroAmount() {
        for (String invalid : new String[]{"-0.01", "1.0001"}) assertThrows(IllegalArgumentException.class,
                () -> new Expense("x", BigDecimal.ONE, GrowthCategory.GENERAL, null, null, ExpenseType.RECURRING, new BigDecimal(invalid)));
        assertThrows(IllegalArgumentException.class, () -> new Expense("x", BigDecimal.ONE.negate(), GrowthCategory.GENERAL, null, null, ExpenseType.RECURRING, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Expense("x", BigDecimal.ONE, GrowthCategory.GENERAL, null, null, ExpenseType.ONE_TIME, BigDecimal.ONE));
        assertEquals(BigDecimal.ONE, new Expense("x", BigDecimal.ZERO, GrowthCategory.NONE, null, null, ExpenseType.RECURRING, BigDecimal.ONE).getAnnualExpenseVolatility());
        assertNull(new Expense("legacy credit", BigDecimal.ONE.negate()).getAnnualExpenseVolatility());
    }

    @Test void deterministicInputComparisonIsNarrowAndScaleSensitiveAndFinancialResultsUnchanged() {
        var plan = MonteCarloFixtures.household();
        var original = CompleteProjectionSnapshot.freeze(plan);
        var result = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(plan));
        var fingerprint = new AnalysisFingerprintService();
        String before = fingerprint.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none());
        var settings = new MonteCarloSettings(1, 123L, new BigDecimal("0.045"), new BigDecimal("0.1"));
        var mc = new MonteCarloFingerprintService();
        String mcBefore = mc.assess(MonteCarloFingerprintService.standalone(plan, settings, null)).fingerprint();
        var inputs = new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE,
                java.time.LocalDate.of(2027, 1, 1), java.time.LocalDate.of(2027, 1, 1), new BigDecimal("0.01"),
                null, null, java.util.List.of(), java.util.List.of(), null);
        String ssBefore = fingerprint.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, inputs);
        String weightedBefore = fingerprint.fingerprint(AnalysisKind.COUPLE_WEIGHTED, plan, inputs);
        volatility(plan, "0.25");
        var after = CompleteProjectionSnapshot.freeze(plan);
        assertNotEquals(original, after);
        assertNotEquals(CompleteProjectionSnapshot.digest(original), CompleteProjectionSnapshot.digest(after));
        assertTrue(CompleteProjectionSnapshot.sameDeterministicInputs(original, after));
        assertEquals(before, fingerprint.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none()));
        assertEquals(ssBefore, fingerprint.fingerprint(AnalysisKind.COUPLE_SOCIAL_SECURITY, plan, inputs));
        assertEquals(weightedBefore, fingerprint.fingerprint(AnalysisKind.COUPLE_WEIGHTED, plan, inputs));
        assertNotEquals(mcBefore, mc.assess(MonteCarloFingerprintService.standalone(plan, settings, null)).fingerprint());
        assertEquals(result, RetainedProjectionSnapshot.capture(new ProjectionEngine().project(plan)));
        assertFalse(CompleteProjectionSnapshot.sameDeterministicInputs(original, after.replace("80000.01", "80000.010")), "Financial decimal scale remains significant");
        assertFalse(CompleteProjectionSnapshot.sameDeterministicInputs(original, after.replace("80000.01", "80001.01")));
        assertFalse(CompleteProjectionSnapshot.sameDeterministicInputs(original, after.replace("\"growthCategory\":\"GENERAL\"", "\"growthCategory\":\"NONE\"")));
    }

    @Test void publicMonteCarloAndReferencePreparationRejectEnabledConfiguration() {
        var plan = MonteCarloFixtures.household(); volatility(plan, "0.20");
        var settings = new MonteCarloSettings(1, 123L, new BigDecimal("0.045"), new BigDecimal("0.1"));
        var failure = assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyze(plan, settings));
        assertTrue(failure.getMessage().contains("not yet available"));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyze(plan, settings, null,
                index -> { throw new AssertionError("Supplied world must not execute"); }));
        assertThrows(IllegalArgumentException.class, () -> new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRunService().run(plan, settings, null,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none()));
    }
}
