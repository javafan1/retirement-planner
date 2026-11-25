package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasRecipientDiagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.Supplier;

/** Development opt-in evaluated by the JavaFX application process. */
final class ArkansasRecipientDiagnosticFile {
    static final String PROPERTY = "aledrix.diagnostics.arkansasRecipient";

    private ArkansasRecipientDiagnosticFile() { }

    static <T> T evaluate(AnalysisCancellationToken cancellation, Supplier<T> work) {
        if (!Boolean.getBoolean(PROPERTY)) return work.get();
        return ArkansasRecipientDiagnostic.capture(cancellation,
                redacted -> write(Path.of("target", "phase6e-p3d3c4a"), redacted), work);
    }

    static void write(Path directory, String redacted) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("arkansas-recipient-" + UUID.randomUUID() + ".txt");
            Path incomplete = Files.createTempFile(directory, "incomplete-recipient-", ".tmp");
            try {
                Files.writeString(incomplete, redacted);
                Files.move(incomplete, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(incomplete);
            }
        } catch (IOException | SecurityException unavailable) {
            System.getLogger(ArkansasRecipientDiagnosticFile.class.getName()).log(System.Logger.Level.WARNING,
                    "Unable to write the optional Arkansas recipient diagnostic file.");
        }
    }
}
