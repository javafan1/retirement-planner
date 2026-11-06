package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.io.*;
import java.util.*;

public final class AnalysisVersions {
    private AnalysisVersions() { }
    public static boolean usesOrdinaryPensionSelection(AnalysisKind kind) {
        return kind == AnalysisKind.COUPLE_QUICK || kind == AnalysisKind.COUPLE_EXHAUSTIVE
                || kind.isMonteCarlo() && kind.name().endsWith("FIXED");
    }
    /** Only fresh calculation completions use this map; never apply it to a restored artifact. */
    public static Map<String, String> calculatedAlgorithms(AnalysisKind kind, AnalysisPayload payload) {
        var result = new TreeMap<>(algorithms(kind, payload));
        if (usesOrdinaryPensionSelection(kind)) result.put(
                com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY,
                com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.CORRECTED);
        return Map.copyOf(result);
    }
    public static boolean supportedMonteCarloAlgorithms(AnalysisKind kind, AnalysisPayload payload, Map<String, String> versions) {
        return versions.equals(algorithms(kind, payload)) || versions.equals(calculatedAlgorithms(kind, payload));
    }
    /** Payload-specific capabilities leave every legacy kind's version map unchanged. */
    public static Map<String, String> algorithms(AnalysisKind kind, AnalysisPayload payload) {
        var result = new TreeMap<>(algorithms(kind));
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.expenseExecution() != null)
            result.put("recurring-expenses", com.daviddunn.retirementplanner.app.montecarlo.ExpenseExecution.ALGORITHM);
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.pairedExpenseExecution() != null)
            result.put("paired-recurring-expenses", com.daviddunn.retirementplanner.app.montecarlo.PairedExpenseExecution.ALGORITHM);
        return Map.copyOf(result);
    }
    public static Map<String, String> algorithms(AnalysisKind kind) {
        var versions = new TreeMap<String, String>();
        versions.put("monthly-social-security", "v1");
        versions.put("analysis-kind", kind.name() + "-v1");
        if (kind.isMonteCarlo()) {
            versions.put("monte-carlo", "v1");
            versions.put("random-streams", "v1");
            versions.put("statistics", "v1");
        }
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
            versions.put("projection", "v1");
        return Map.copyOf(versions);
    }
    /** Classpath reads belong on the I/O worker, not JavaFX. No remote reference data. */
    public static Map<String, String> references(AnalysisKind kind) {
        List<String> resources = new ArrayList<>();
        if (kind.isMonteCarlo() ? kind.name().endsWith("LONGEVITY")
                : kind != AnalysisKind.COUPLE_EXHAUSTIVE && kind != AnalysisKind.INDIVIDUAL_DETERMINISTIC)
            resources.add("mortality/ssa-period-life-table-2022.csv");
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY) {
            resources.add("rules/government-rules-2026.json");
            resources.add("tax/state/state-tax-2026.json");
            resources.add("tax/state/iowa-tax-2026.json");
        }
        Map<String, String> result = new TreeMap<>();
        for (String name : resources) {
            try (var stream = AnalysisVersions.class.getClassLoader().getResourceAsStream(name)) {
                if (stream == null) throw new IllegalStateException("Missing shipped analysis reference: " + name);
                result.put(name, AnalysisSnapshotCodec.sha256(stream.readAllBytes()));
            } catch (IOException failure) { throw new IllegalStateException("Cannot fingerprint shipped analysis references.", failure); }
        }
        return Map.copyOf(result);
    }
}
