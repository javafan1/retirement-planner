package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasRecipientDiagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArkansasRecipientDiagnosticFileTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void explicitApplicationPropertyControlsTheWorkerScope() {
        var property = ArkansasRecipientDiagnosticFile.PROPERTY;
        var previous = System.getProperty(property);
        try {
            System.clearProperty(property);
            assertFalse(ArkansasRecipientDiagnosticFile.evaluate(
                    AnalysisCancellationToken.none(), ArkansasRecipientDiagnostic::isEnabled));
            System.setProperty(property, "true");
            assertTrue(ArkansasRecipientDiagnosticFile.evaluate(
                    AnalysisCancellationToken.none(), ArkansasRecipientDiagnostic::isEnabled));
            assertFalse(ArkansasRecipientDiagnostic.isEnabled());
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    @Test
    void publishesOnlyACompleteRecord(@TempDir Path directory) throws Exception {
        String redacted = "phase=P3D3C4A\nyear=2056\n";
        ArkansasRecipientDiagnosticFile.write(directory, redacted);
        try (var files = Files.list(directory)) {
            var written = files.toList();
            assertEquals(1, written.size());
            assertTrue(written.getFirst().getFileName().toString().matches("arkansas-recipient-[a-f0-9-]+\\.txt"));
            assertEquals(redacted, Files.readString(written.getFirst()));
        }
    }
}
