package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.io.*;
import java.util.*;

public final class AnalysisVersions {
    private AnalysisVersions() { }
    public static Map<String, String> algorithms(AnalysisKind kind) {
        var versions = new TreeMap<String, String>();
        versions.put("monthly-social-security", "v1");
        versions.put("analysis-kind", kind.name() + "-v1");
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
            versions.put("projection", "v1");
        return Map.copyOf(versions);
    }
    /** Classpath reads belong on the I/O worker, not JavaFX. No remote reference data. */
    public static Map<String, String> references(AnalysisKind kind) {
        List<String> resources = new ArrayList<>();
        if (kind != AnalysisKind.COUPLE_EXHAUSTIVE && kind != AnalysisKind.INDIVIDUAL_DETERMINISTIC)
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
