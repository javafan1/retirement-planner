package com.daviddunn.retirementplanner.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Shares the existing package-local fault seam with UI lifecycle tests. */
public final class AnalysisSnapshotFaults {
    private AnalysisSnapshotFaults() { }
    public static JsonAnalysisSnapshotRepository failVerification(Path root, BooleanSupplier fail) {
        return new JsonAnalysisSnapshotRepository(root, new AtomicFileWriter((stage, path) -> {
            if (fail.getAsBoolean() && stage == AtomicFileWriter.Stage.VERIFY && path.getFileName().toString().contains(".json.gz"))
                throw new IOException("injected verification failure");
        }));
    }
}
