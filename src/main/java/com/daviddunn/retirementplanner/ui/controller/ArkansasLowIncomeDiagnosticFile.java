package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.domain.projection.ArkansasLowIncomeDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Local opt-in for a development launch from the repository root. */
final class ArkansasLowIncomeDiagnosticFile {
    private static final String PROPERTY = "aledrix.diagnostics.arkansasLowIncome";

    private ArkansasLowIncomeDiagnosticFile() { }

    static Optional<Consumer<ArkansasLowIncomeDiagnostic>> forRun() {
        if (!Boolean.getBoolean(PROPERTY)) return Optional.empty();
        return Optional.of(ArkansasLowIncomeDiagnosticFile::write);
    }

    private static void write(ArkansasLowIncomeDiagnostic diagnostic) {
        try {
            Path directory = Files.createDirectories(Path.of("target", "phase6e-p3d3c2"));
            Path file = directory.resolve("low-income-" + UUID.randomUUID() + ".txt");
            Path incomplete = Files.createTempFile(directory, "incomplete-low-income-", ".tmp");
            try {
                Files.writeString(incomplete, diagnostic.safeText());
                Files.move(incomplete, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(incomplete);
            }
        } catch (IOException unavailable) {
            System.getLogger(ArkansasLowIncomeDiagnosticFile.class.getName()).log(System.Logger.Level.WARNING,
                    "Unable to write the optional Arkansas low-income diagnostic file.");
        }
    }
}
