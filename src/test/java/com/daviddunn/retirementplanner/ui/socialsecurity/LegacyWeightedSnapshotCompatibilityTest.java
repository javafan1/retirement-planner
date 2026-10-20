package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Captured with the unchanged 1ff71f3 codec before the 9D-1 extension. Never regenerate. */
class LegacyWeightedSnapshotCompatibilityTest {
    @Test void frozenCoupleBytesKeepFieldOrderScaleAndDigest() throws Exception {
        verify("weighted-couple-v1.json.gz", "fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a");
    }
    @Test void frozenIndividualBytesKeepFieldOrderScaleAndDigest() throws Exception {
        verify("weighted-individual-v1.json.gz", "f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07");
    }
    private void verify(String name, String hash) throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/analysis-snapshots/" + name)) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        assertEquals(hash, AnalysisSnapshotCodec.sha256(bytes));
        var codec = new AnalysisSnapshotCodec();
        var restored = codec.decode(bytes);
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] frozenRaw = gzip.readAllBytes();
            byte[] restoredRaw = codec.raw(restored);
            assertArrayEquals(frozenRaw, restoredRaw);
            // BigDecimal.equals compares both unscaled value and scale, across every numeric token.
            assertEquals(exactNumbers(frozenRaw), exactNumbers(restoredRaw));
        }
        assertEquals(restored.payloadDigest(), codec.digest(restored.payload(), restored.context()));
        assertArrayEquals(bytes, codec.encode(restored));
    }
    private java.util.List<java.math.BigDecimal> exactNumbers(byte[] raw) throws Exception {
        var numbers = new java.util.ArrayList<java.math.BigDecimal>();
        try (var parser = new com.fasterxml.jackson.core.JsonFactory().createParser(raw)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken().isNumeric()) numbers.add(parser.getDecimalValue());
            }
        }
        assertFalse(numbers.isEmpty());
        return numbers;
    }
}
