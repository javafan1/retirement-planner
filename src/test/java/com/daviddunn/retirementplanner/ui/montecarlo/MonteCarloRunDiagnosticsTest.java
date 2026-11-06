package com.daviddunn.retirementplanner.ui.montecarlo;

import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloRunDiagnosticsTest {
    private static MonteCarloRunDiagnostics.State state() {
        return new MonteCarloRunDiagnostics.State(MonteCarloMode.FIXED_LIFESPAN,
                MonteCarloSession.State.RUNNING, MonteCarloSession.State.FAILED,
                true, true, true, true, false);
    }

    @Test
    void formatterUsesOnlyApprovedMetadataAndNeverReadsExceptionText() {
        var failure = new IllegalStateException("PRIVATE financial input and path") {
            @Override public String getMessage() { throw new AssertionError("Exception messages must not be inspected"); }
            @Override public String toString() { throw new AssertionError("Throwable formatting must not be used"); }
        };
        String record = MonteCarloRunDiagnostics.format(MonteCarloRunDiagnostics.Event.SESSION_CHANGED,
                state(), MonteCarloRunDiagnostics.Category.WORKER, failure);
        assertTrue(record.contains("after=FAILED"));
        assertTrue(record.contains("statusVisible=false"));
        assertTrue(record.contains("workerSubmitted=true"));
        assertTrue(record.contains(failure.getClass().getName()));
        assertFalse(record.contains("PRIVATE"));
        assertFalse(record.contains("financial input"));
        assertTrue(record.length() < 1024);
    }

    @Test
    void failingLoggingHandlerCannotMaskRunFailure() {
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { throw new IllegalStateException("Diagnostic sink unavailable"); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        MonteCarloRunDiagnostics.LOG.addHandler(handler);
        try {
            assertDoesNotThrow(() -> MonteCarloRunDiagnostics.record(MonteCarloRunDiagnostics.Event.SESSION_CHANGED,
                    state(), MonteCarloRunDiagnostics.Category.WORKER, new IllegalStateException()));
        } finally { MonteCarloRunDiagnostics.LOG.removeHandler(handler); }
    }
}
