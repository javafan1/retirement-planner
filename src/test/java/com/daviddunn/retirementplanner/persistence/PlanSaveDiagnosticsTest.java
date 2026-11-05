package com.daviddunn.retirementplanner.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class PlanSaveDiagnosticsTest {
    @TempDir Path directory;

    @Test
    void diagnosticFormattingExcludesMessagesPathsAndNonApplicationFrames() throws Exception {
        String sensitive = "C:\\Users\\PrivateName\\Retirement\\plan.json account 987654";
        var failure = new CompletionException("wrapped " + sensitive, new IOException(sensitive));
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.daviddunn.retirementplanner.ui.MainWindow", "onSave", "MainWindow.java", 700),
                new StackTraceElement("java.nio.file.Files", "move", "Files.java", 100)
        });
        var diagnostics = new PlanSaveDiagnostics(PlanSaveDiagnostics.Operation.SAVE_AS);
        diagnostics.stage(PlanSaveDiagnostics.Stage.JSON_ROUND_TRIP_VERIFICATION);

        String result = PlanSaveDiagnostics.format(diagnostics, failure);

        assertTrue(result.contains("operation=SAVE_AS"));
        assertTrue(result.contains("java.util.concurrent.CompletionException"));
        assertTrue(result.contains("java.io.IOException"));
        assertTrue(result.contains("MainWindow#onSave"));
        assertFalse(result.contains("java.nio.file.Files"));
        assertFalse(result.contains(sensitive));
        assertFalse(result.contains("account"));
        assertFalse(result.contains("message="));
        Path diagnosticDirectory = directory.resolve("private-home").resolve(".retirement-planner").resolve("diagnostics");
        PlanSaveDiagnostics.writeRecord(diagnosticDirectory, result);
        String stored = Files.readString(diagnosticDirectory.resolve("plan-save-diagnostics.log"));
        assertTrue(stored.contains("java.io.IOException"));
        assertFalse(stored.contains("PrivateName"));
        assertFalse(stored.contains("987654"));
    }

    @Test
    void diagnosticFileIsSeparateAndSizeBounded() throws Exception {
        Path diagnosticDirectory = directory.resolve("application-data").resolve("diagnostics");
        PlanSaveDiagnostics.writeRecord(diagnosticDirectory, "safe-diagnostic-record");
        assertTrue(Files.readString(diagnosticDirectory.resolve("plan-save-diagnostics.log"))
                .contains("safe-diagnostic-record"));

        PlanSaveDiagnostics.writeRecord(diagnosticDirectory, "x".repeat(300_000));
        Path log = diagnosticDirectory.resolve("plan-save-diagnostics.log");
        assertTrue(Files.size(log) <= 256 * 1024);
        assertTrue(Files.readString(log).contains("x".repeat(100)));
    }

    @Test
    void atomicWriterContextDistinguishesBeforeWritingAndReplacementAttempt() throws Exception {
        Path beforeWrite = directory.resolve("before.json");
        var createFailure = new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.CREATE) throw new IOException("private path must not be logged");
        });
        var createContext = new PlanSaveDiagnostics(PlanSaveDiagnostics.Operation.SAVE);
        assertThrows(IOException.class, () -> createFailure.write(beforeWrite, new byte[] {1}, path -> { }, false,
                path -> { }, createContext));
        assertEquals(PlanSaveDiagnostics.Stage.TEMP_FILE_CREATE, createContext.stage());
        assertFalse(createContext.fileWritingStarted());
        assertFalse(createContext.atomicReplacementAttempted());

        Path afterWrite = directory.resolve("after.json");
        var replaceFailure = new AtomicFileWriter((stage, path) -> {
            if (stage == AtomicFileWriter.Stage.REPLACE) throw new IOException("private path must not be logged");
        });
        var replaceContext = new PlanSaveDiagnostics(PlanSaveDiagnostics.Operation.SAVE_AS);
        assertThrows(IOException.class, () -> replaceFailure.write(afterWrite, new byte[] {2}, path -> { }, false,
                path -> { }, replaceContext));
        assertEquals(PlanSaveDiagnostics.Stage.ATOMIC_TARGET_REPLACEMENT, replaceContext.stage());
        assertTrue(replaceContext.fileWritingStarted());
        assertFalse(replaceContext.atomicReplacementAttempted(), "Fault injected before Files.move means replacement was not attempted.");
        assertFalse(Files.exists(afterWrite));

        var successContext = new PlanSaveDiagnostics(PlanSaveDiagnostics.Operation.SAVE);
        new AtomicFileWriter().write(directory.resolve("success.json"), new byte[] {3}, path -> { }, false,
                path -> { }, successContext);
        assertTrue(successContext.fileWritingStarted());
        assertTrue(successContext.atomicReplacementAttempted());
    }

    @Test
    void diagnosticFrameCountIsBounded() {
        var failure = new RuntimeException();
        var frames = new StackTraceElement[40];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement("com.daviddunn.retirementplanner.persistence.Fake", "step" + index,
                    "Fake.java", index + 1);
        }
        failure.setStackTrace(frames);
        String result = PlanSaveDiagnostics.format(
                new PlanSaveDiagnostics(PlanSaveDiagnostics.Operation.SAVE), failure);
        assertEquals(12, result.split("com\\.daviddunn\\.retirementplanner\\.persistence\\.Fake#", -1).length - 1);
        assertFalse(result.contains("step12"));
    }
}
