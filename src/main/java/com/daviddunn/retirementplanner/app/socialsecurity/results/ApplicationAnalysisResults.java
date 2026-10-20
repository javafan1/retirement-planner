package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;

/** Lazy application lifetime service; constructing a controller performs no snapshot filesystem I/O. */
public final class ApplicationAnalysisResults {
    private ApplicationAnalysisResults() { }
    private static final class Holder {
        static final AnalysisResultStore STORE = new AnalysisResultStore(
                new JsonAnalysisSnapshotRepository(AnalysisStorageLocation.systemDefault()));
    }
    public static AnalysisResultStore store() { return Holder.STORE; }
}
