package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.io.IOException;
import java.util.*;

public interface AnalysisSnapshotRepository {
    Optional<AnalysisSnapshot> load(UUID planId, AnalysisKind kind) throws IOException;
    void save(AnalysisSnapshot snapshot) throws IOException;
    /** In-memory/test implementations serialize through this monitor; filesystem repositories override atomically. */
    default boolean saveIfAbsent(AnalysisSnapshot snapshot) throws IOException {
        synchronized (this) {
            if (load(snapshot.planId(), snapshot.kind()).isPresent()) return false;
            save(snapshot); return true;
        }
    }
    Optional<AnalysisSnapshot> loadBackup(UUID planId, AnalysisKind kind) throws IOException;
    default void claimPlan(UUID planId, java.nio.file.Path planFile) throws IOException { }
}
