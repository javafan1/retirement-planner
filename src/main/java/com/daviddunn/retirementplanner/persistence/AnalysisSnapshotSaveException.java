package com.daviddunn.retirementplanner.persistence;

import java.io.IOException;

/** Diagnostic context only; the original failure and its causes remain intact. */
public final class AnalysisSnapshotSaveException extends IOException {
    public enum Stage {
        QUEUE("storage queue"), PLAN_BINDING("plan identity binding"), SNAPSHOT_CONSTRUCTION("snapshot construction"),
        DTO_VALIDATION("snapshot validation"), SERIALIZATION("JSON serialization"), COMPRESSION("gzip compression"),
        SIZE_LIMIT("snapshot safety limit"), FILE_LOCK("snapshot file locking"),
        EXISTING_SNAPSHOT("existing snapshot validation/compatibility"),
        ATOMIC_WRITE("transactional file writing"), VERIFICATION("written snapshot verification");

        private final String description;
        Stage(String description) { this.description = description; }
        public String description() { return description; }
    }

    private final Stage stage;
    private final long rawBytes;
    private final long compressedBytes;

    public AnalysisSnapshotSaveException(Stage stage, long rawBytes, long compressedBytes, Throwable cause) {
        super("Analysis snapshot save failed during " + stage.description() + ".", cause);
        this.stage = stage;
        this.rawBytes = rawBytes;
        this.compressedBytes = compressedBytes;
    }

    public Stage stage() { return stage; }
    public long rawBytes() { return rawBytes; }
    public long compressedBytes() { return compressedBytes; }

    public static AnalysisSnapshotSaveException at(Stage stage, long rawBytes, long compressedBytes, Exception cause) {
        return cause instanceof AnalysisSnapshotSaveException diagnostic ? diagnostic
                : new AnalysisSnapshotSaveException(stage, rawBytes, compressedBytes, cause);
    }
}
