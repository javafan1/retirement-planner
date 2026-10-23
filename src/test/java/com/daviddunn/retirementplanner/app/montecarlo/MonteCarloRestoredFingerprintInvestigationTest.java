package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostics expose structural paths only, never input values or plan contents. */
class MonteCarloRestoredFingerprintInvestigationTest {
    @TempDir Path directory;
    private final MonteCarloFingerprintService fingerprints = new MonteCarloFingerprintService();
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    @Test void unchangedSavedPlanAndReconstructedBaselineRetainCurrency() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(true);
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Original baseline"));
        var original = capture(plan, "12.00", "-2.00", "67", "67");
        var file = directory.resolve("plan.json");
        var repository = new JsonRetirementPlanRepository(); repository.save(plan, file);
        var reopened = repository.load(file);
        assertNotSame(plan.getBaseline(), reopened.getBaseline());
        var next = capture(reopened, "12.00", "-2.00", "67", "67");
        assertEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(next).fingerprint(),
                () -> "Mismatch paths: " + differences(original, next));
        var altered = capture(reopened, "12.00", "-2.00", "66", "67");
        assertNotEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(altered).fingerprint());
        assertEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(
                capture(reopened, "12.00", "-2.00", "67", "67")).fingerprint());
        var account = reopened.getAccountPortfolio().getAccounts().getFirst();
        var balance = account.getCurrentBalance();
        account.setCurrentBalance(balance.add(java.math.BigDecimal.ONE));
        assertNotEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(
                capture(reopened, "12.00", "-2.00", "67", "67")).fingerprint());
        account.setCurrentBalance(balance);
        assertEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(
                capture(reopened, "12.00", "-2.00", "67", "67")).fingerprint());
        var baselineAccount = reopened.getBaseline().getSnapshot().getAccountPortfolio().getAccounts().getFirst();
        baselineAccount.setCurrentBalance(baselineAccount.getCurrentBalance().add(java.math.BigDecimal.ONE));
        assertNotEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(
                capture(reopened, "12.00", "-2.00", "67", "67")).fingerprint());
    }

    @Test void identicalVisibleSettingsAfterReopeningShouldRetainCurrency() throws Exception {
        var plan = MonteCarloCapacityProbeTest.fixture(true);
        plan.setBaseline(ProjectionBaselineFactory.create(plan, "Original baseline"));
        // Typing -2 and leaving default -2.00 display the same rate, but produce different scales.
        var original = capture(plan, "12.00", "-2", "67", "67");
        var file = directory.resolve("plan.json");
        var repository = new JsonRetirementPlanRepository(); repository.save(plan, file);
        var reopened = repository.load(file);
        var next = capture(reopened, "12.00", "-2.00", "67", "67");
        // Frozen hashes from the pre-correction failing-test log; do not regenerate.
        assertEquals("c180667d83128144a3a3a1c35aa95d80f6e9077968a7f626aa696fb21affacd6",
                fingerprints.assess(original, MonteCarloFingerprintService.Contract.EXACT_V1).fingerprint());
        assertEquals("118d320d56f2c268b28fb23bdd5f4fcfcc6696fb8ea5e0388993b71217bed0f6",
                fingerprints.assess(next, MonteCarloFingerprintService.Contract.EXACT_V1).fingerprint());
        assertEquals(fingerprints.assess(original).fingerprint(), fingerprints.assess(next).fingerprint(),
                () -> "Mismatch paths: " + differences(original, next));
    }

    private static MonteCarloFingerprintService.Inputs capture(
            com.daviddunn.retirementplanner.domain.model.RetirementPlan plan,
            String volatility, String floor, String survivorA, String survivorB) {
        var settings = new MonteCarloInputs(5000, "5", volatility, "417")
                .settings(true, "2.75", "1.75", floor);
        return MonteCarloFingerprintService.comparison(MonteCarloStrategyComparisonRunService.capture(
                plan, settings, MonteCarloMode.LONGEVITY_ADJUSTED, "1", "1", survivorA, survivorB).request());
    }

    static List<String> differences(MonteCarloFingerprintService.Inputs a, MonteCarloFingerprintService.Inputs b) {
        var paths = new ArrayList<String>();
        compare("settings", JSON.valueToTree(a.settings()), JSON.valueToTree(b.settings()), paths);
        compare("mortality", JSON.valueToTree(a.request().mortality()), JSON.valueToTree(b.request().mortality()), paths);
        try {
            for (var key : List.of("CurrentPlan", "SavedBaseline")) {
                var left = JSON.readTree(key.equals("CurrentPlan") ? a.request().planA() : a.request().planB());
                var right = JSON.readTree(key.equals("CurrentPlan") ? b.request().planA() : b.request().planB());
                ((com.fasterxml.jackson.databind.node.ObjectNode) left).remove(List.of("planId", "baseline"));
                ((com.fasterxml.jackson.databind.node.ObjectNode) right).remove(List.of("planId", "baseline"));
                compare(key, left, right, paths);
            }
        } catch (Exception invalid) { paths.add("plan-document-unavailable"); }
        return List.copyOf(paths);
    }
    private static void compare(String path, JsonNode a, JsonNode b, List<String> paths) {
        if (paths.size() >= 32) return;
        if (a == null || b == null) { if (a != b) paths.add(path + " (presence)"); return; }
        if (a.isObject() && b.isObject()) {
            var keys = new TreeSet<String>(); a.fieldNames().forEachRemaining(keys::add); b.fieldNames().forEachRemaining(keys::add);
            for (var key : keys) compare(path + "." + key, a.get(key), b.get(key), paths);
        } else if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) paths.add(path + " (count)");
            else for (int i = 0; i < a.size(); i++) compare(path + "[" + i + "]", a.get(i), b.get(i), paths);
        } else if (a.isNumber() && b.isNumber()) {
            if (!a.decimalValue().equals(b.decimalValue())) paths.add(path + (a.decimalValue().compareTo(b.decimalValue()) == 0
                    ? " (scale-only)" : " (numeric value)"));
        } else if (!a.equals(b)) paths.add(path + " (value)");
    }
}
