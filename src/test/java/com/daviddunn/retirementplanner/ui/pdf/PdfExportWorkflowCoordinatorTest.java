package com.daviddunn.retirementplanner.ui.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportWorkflowCoordinatorTest {
    @TempDir Path temporary;

    @Test void cancelAndEscapeEquivalentChoicePerformsNoCaptureChooserRenderOrFileCreation() {
        Path storeDirectory = temporary.resolve("unused");
        AtomicInteger capture = new AtomicInteger(); AtomicInteger chooser = new AtomicInteger(); AtomicInteger render = new AtomicInteger();
        var coordinator = new PdfExportWorkflowCoordinator<String>((owner) -> PdfExportChoice.CANCEL,
                (owner, name) -> { chooser.incrementAndGet(); return Optional.empty(); },
                new PdfPreviewStore(storeDirectory), path -> fail("opener should not be called"), Runnable::run, Runnable::run);
        assertFalse(coordinator.start(request(PdfExportChoice.CANCEL, Optional.empty(), capture, render, chooser, new AtomicReference<>(), new AtomicReference<>())));
        assertEquals(0, capture.get()); assertEquals(0, chooser.get()); assertEquals(0, render.get());
        assertFalse(Files.exists(storeDirectory));
    }

    @Test void viewCapturesOnceSkipsSaveChooserRendersInWorkerAndOpensOnlyCompletePdf() {
        var chooserCalls = new AtomicInteger(); var captureCalls = new AtomicInteger(); var renderCalls = new AtomicInteger();
        var worker = new QueuedExecutor(); var dispatched = new QueuedExecutor();
        var opened = new AtomicReference<Path>(); var completed = new AtomicReference<Path>();
        Path directory = temporary.resolve("view");
        var coordinator = new PdfExportWorkflowCoordinator<String>(owner -> PdfExportChoice.VIEW,
                (owner, name) -> { chooserCalls.incrementAndGet(); return Optional.empty(); }, new PdfPreviewStore(directory),
                path -> { assertTrue(Files.readString(path).startsWith("%PDF-")); opened.set(path); }, worker, dispatched::execute);
        var request = new PdfExportWorkflowCoordinator.Request<String>(null, "finance.pdf", () -> true,
                () -> { captureCalls.incrementAndGet(); return "frozen"; }, (report, path) -> {
                    renderCalls.incrementAndGet(); assertEquals("frozen", report); writePdf(path, report);
                }, () -> true, (choice, path) -> completed.set(path), failure -> fail(failure.cause()), ignored -> { });
        assertTrue(coordinator.start(request)); assertEquals(1, captureCalls.get()); assertTrue(coordinator.isBusy());
        assertEquals(0, chooserCalls.get()); assertEquals(0, renderCalls.get());
        worker.runNext(); assertEquals(1, renderCalls.get()); assertNotNull(opened.get()); assertTrue(coordinator.isBusy());
        dispatched.runNext(); assertFalse(coordinator.isBusy()); assertEquals(opened.get(), completed.get());
    }

    @Test void saveCancellationAndReadinessRejectionNeverCapture() {
        AtomicInteger captures = new AtomicInteger(); AtomicInteger renders = new AtomicInteger(); AtomicInteger choices = new AtomicInteger();
        var coordinator = new PdfExportWorkflowCoordinator<String>(owner -> { choices.incrementAndGet(); return PdfExportChoice.SAVE; },
                (owner, name) -> Optional.empty(), new PdfPreviewStore(temporary.resolve("save")), path -> fail(), Runnable::run, Runnable::run);
        var request = request(PdfExportChoice.SAVE, Optional.empty(), captures, renders, new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>());
        assertFalse(coordinator.start(request)); assertEquals(0, captures.get()); assertEquals(0, renders.get());
        assertFalse(coordinator.start(new PdfExportWorkflowCoordinator.Request<>(null, "x.pdf", () -> false,
                () -> { captures.incrementAndGet(); return "x"; }, (value, path) -> renders.incrementAndGet(), () -> true,
                (choice, path) -> { }, failure -> fail(), ignored -> { })));
        assertEquals(1, choices.get()); assertEquals(0, captures.get());
    }

    @Test void reentrantStartIsRejectedWhileWorkerIsBusy() {
        var worker = new QueuedExecutor(); var dispatched = new QueuedExecutor(); var captures = new AtomicInteger();
        var coordinator = new PdfExportWorkflowCoordinator<String>(owner -> PdfExportChoice.SAVE,
                (owner, name) -> Optional.of(temporary.resolve("saved.pdf")), new PdfPreviewStore(temporary.resolve("preview")),
                path -> fail(), worker, dispatched::execute);
        var request = request(PdfExportChoice.SAVE, Optional.of(temporary.resolve("saved.pdf")), captures,
                new AtomicInteger(), new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>());
        assertTrue(coordinator.start(request)); assertFalse(coordinator.start(request)); assertEquals(1, captures.get());
        worker.runNext(); dispatched.runNext(); assertFalse(coordinator.isBusy());
    }

    @Test void viewerFailureRetainsTheCompletedPreviewAndReportsItsPath() {
        var failure = new AtomicReference<PdfExportWorkflowCoordinator.PdfExportFailure>();
        var store = new PdfPreviewStore(temporary.resolve("open-failure"));
        var coordinator = new PdfExportWorkflowCoordinator<String>(owner -> PdfExportChoice.VIEW,
                (owner, name) -> fail("View has no Save chooser"), store, path -> { throw new java.io.IOException("no association"); },
                Runnable::run, Runnable::run);
        var request = new PdfExportWorkflowCoordinator.Request<String>(null, "x.pdf", () -> true,
                () -> "snapshot", (report, path) -> writePdf(path, report), () -> true,
                (choice, path) -> fail("viewer failure must not report success"), failure::set, ignored -> { });
        assertTrue(coordinator.start(request)); assertNotNull(failure.get());
        assertTrue(Files.exists(failure.get().retainedPreview()));
        assertTrue(failure.get().cause().getMessage().contains("no association"));
    }

    private static PdfExportWorkflowCoordinator.Request<String> request(PdfExportChoice choice, Optional<Path> destination,
            AtomicInteger captures, AtomicInteger renders, AtomicInteger chooser, AtomicReference<Path> success,
            AtomicReference<PdfExportWorkflowCoordinator.PdfExportFailure> failure) {
        return new PdfExportWorkflowCoordinator.Request<>(null, "suggested.pdf", () -> true,
                () -> { captures.incrementAndGet(); return "report"; }, (report, path) -> { renders.incrementAndGet(); writePdf(path, report); },
                () -> true, (selected, path) -> success.set(path), failure::set, ignored -> { });
    }

    private static void writePdf(Path path, String content) throws java.io.IOException {
        Files.writeString(path, "%PDF-1.7\n" + content + "\n%%EOF\n");
    }

    private static final class QueuedExecutor implements Executor {
        private Runnable task;
        @Override public void execute(Runnable next) { if (task != null) throw new AssertionError("one task expected"); task = next; }
        void runNext() { Runnable next = task; task = null; if (next == null) throw new AssertionError("no task queued"); next.run(); }
    }
}
