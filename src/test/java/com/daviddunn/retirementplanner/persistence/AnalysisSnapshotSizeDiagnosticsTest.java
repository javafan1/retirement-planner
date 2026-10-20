package com.daviddunn.retirementplanner.persistence;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSnapshotSizeDiagnosticsTest {
    @Test void exactUtf8AccountingCountsAndPrivacyWithoutFinancialValues() throws Exception {
        String accounts = "[{\"name\":\"PRIVATE_NAME_π\"}]";
        String years = "[{\"index\":1,\"balances\":[{\"account\":0,\"endingBalance\":123456.789}],\"PRIVATE_KEY\":\"PRIVATE_VALUE\"}]";
        String projection = "{\"accounts\":" + accounts + ",\"years\":" + years + "}";
        String metric = "{\"totalIncome\":123456.78900}";
        String entry = "{\"strategy\":{},\"metrics\":" + metric + ",\"differences\":" + metric + ",\"detail\":";
        String report = "{\"household\":\"PRIVATE_HOUSEHOLD\"}";
        String context = "{\"primary\":\"PRIVATE_PRIMARY\"}";
        String reference = "{\"explanation\":\"PRIVATE_EXPLANATION\"}";
        String raw = "{\"payload\":{\"baseline\":" + projection + ",\"entries\":[" + entry + "null}," + entry + projection
                + "}],\"report\":" + report + ",\"socialSecurityReference\":" + reference + "},\"context\":" + context + "}";
        var summary = AnalysisSnapshotSizeDiagnostics.measure(raw.getBytes(StandardCharsets.UTF_8));
        assertTrue(summary.complete()); assertEquals(2, summary.candidates()); assertEquals(1, summary.retained());
        assertEquals(2, summary.projections()); assertEquals(2, summary.annualRows()); assertEquals(1, summary.maximumYears()); assertEquals(1, summary.maximumAccounts());
        assertEquals(projection.getBytes(StandardCharsets.UTF_8).length, summary.bytes().get("baseline"));
        assertEquals(projection.getBytes(StandardCharsets.UTF_8).length, summary.bytes().get("retainedDetails"));
        assertEquals(report.length() + context.length(), summary.bytes().get("frozenReportAndContext"));
        assertEquals(reference.length(), summary.bytes().get("socialSecurityCrossReference"));
        assertEquals(2L * metric.length(), summary.bytes().get("candidateMetrics"));
        assertEquals(metric.length(), summary.duplicateBytes().get("candidateMetrics"));
        assertEquals(years.length(), summary.duplicateBytes().get("projectionAnnualGraphs"));
        assertEquals(raw.getBytes(StandardCharsets.UTF_8).length, summary.bytes().get("compactEntriesIncludingSyntax") + summary.bytes().get("retainedDetails")
                + summary.bytes().get("baseline") + summary.bytes().get("envelopeAndOtherMetadata"));
        assertFalse(summary.toString().contains("PRIVATE")); assertFalse(summary.toString().contains("123456.789"));
    }

    @Test void numericSafetyLimitRemainsInForceForDiagnostics() {
        byte[] raw = ("{\"value\":" + "1".repeat(4097) + "}").getBytes(StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> AnalysisSnapshotSizeDiagnostics.measure(raw));
        assertEquals(32 * 1024 * 1024, AnalysisSnapshotCodec.MAX_RAW_BYTES);
        assertEquals(16 * 1024 * 1024, AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES);
        assertEquals(4096, AnalysisSnapshotCodec.MAX_NUMBER_LENGTH);
        assertEquals(2_000_000, AnalysisSnapshotCodec.MAX_TOKENS);
    }
}
