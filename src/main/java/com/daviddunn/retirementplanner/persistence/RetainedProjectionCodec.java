package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import java.io.IOException;

/** Gate-1 standalone fidelity codec. No repository slot, completion or analyzer integration. */
public final class RetainedProjectionCodec {
    private final AnalysisSnapshotCodec documents = new AnalysisSnapshotCodec();
    public AnalysisSnapshotCodec.Encoded encode(RetainedProjectionSnapshot snapshot) throws IOException {
        validate(snapshot);
        return documents.encodeDocument(snapshot);
    }
    public RetainedProjectionSnapshot decode(byte[] bytes) throws IOException {
        var snapshot = documents.decodeDocument(bytes, RetainedProjectionSnapshot.class);
        validate(snapshot);
        return snapshot;
    }
    private void validate(RetainedProjectionSnapshot snapshot) throws IOException {
        try { snapshot.restoreProjection(); }
        catch (RuntimeException invalid) { throw new IOException("Retained projection fidelity validation failed.", invalid); }
    }
}
