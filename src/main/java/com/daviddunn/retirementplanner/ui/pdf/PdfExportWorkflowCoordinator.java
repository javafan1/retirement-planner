package com.daviddunn.retirementplanner.ui.pdf;

import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Generic JavaFX workflow orchestration. It only captures supplied report data and writes it. */
public final class PdfExportWorkflowCoordinator<R> {
    private final ChoicePrompt choicePrompt;
    private final DestinationChooser destinationChooser;
    private final PdfPreviewStore previewStore;
    private final PdfOpener opener;
    private final Executor worker;
    private final Consumer<Runnable> fxDispatch;
    private boolean busy;

    public PdfExportWorkflowCoordinator(ChoicePrompt choicePrompt, DestinationChooser destinationChooser,
                                        PdfPreviewStore previewStore, PdfOpener opener, Executor worker,
                                        Consumer<Runnable> fxDispatch) {
        this.choicePrompt = Objects.requireNonNull(choicePrompt);
        this.destinationChooser = Objects.requireNonNull(destinationChooser);
        this.previewStore = Objects.requireNonNull(previewStore);
        this.opener = Objects.requireNonNull(opener);
        this.worker = Objects.requireNonNull(worker);
        this.fxDispatch = Objects.requireNonNull(fxDispatch);
    }

    /** Called on the FX thread. Readiness, choice, chooser, capture and busy state remain FX-owned. */
    public boolean start(Request<R> request) {
        Objects.requireNonNull(request);
        if (busy || !request.ready().getAsBoolean()) return false;
        PdfExportChoice choice;
        try { choice = choicePrompt.choose(request.owner()); }
        catch (RuntimeException failure) { request.failed().accept(new PdfExportFailure(failure, null)); return false; }
        if (choice == null || choice == PdfExportChoice.CANCEL || !request.ready().getAsBoolean()) return false;

        Path destination = null;
        if (choice == PdfExportChoice.SAVE) {
            Optional<Path> selected;
            try { selected = destinationChooser.choose(request.owner(), request.suggestedFilename()); }
            catch (RuntimeException failure) { request.failed().accept(new PdfExportFailure(failure, null)); return false; }
            if (selected == null || selected.isEmpty() || !request.ready().getAsBoolean()) return false;
            destination = selected.get().toAbsolutePath().normalize();
        }

        final R report;
        try {
            if (!request.ready().getAsBoolean()) return false;
            report = Objects.requireNonNull(request.capture().get(), "Captured PDF report is required.");
        } catch (RuntimeException failure) {
            request.failed().accept(new PdfExportFailure(failure, null));
            return false;
        }
        final Path savePath = destination;
        busy = true;
        request.busyChanged().accept(true);
        try {
            worker.execute(() -> perform(request, choice, report, savePath));
            return true;
        } catch (RuntimeException rejected) {
            busy = false;
            request.busyChanged().accept(false);
            request.failed().accept(new PdfExportFailure(rejected, null));
            return false;
        }
    }

    public boolean isBusy() { return busy; }

    private void perform(Request<R> request, PdfExportChoice choice, R report, Path savePath) {
        Path completedPath = savePath;
        try {
            if (choice == PdfExportChoice.VIEW) {
                completedPath = previewStore.create(path -> request.renderer().render(report, path));
                opener.open(completedPath);
            } else {
                request.renderer().render(report, completedPath);
            }
            Path result = completedPath;
            fxDispatch.accept(() -> complete(request, choice, result, null));
        } catch (Throwable failure) {
            Path result = completedPath;
            fxDispatch.accept(() -> complete(request, choice, result, failure));
        }
    }

    private void complete(Request<R> request, PdfExportChoice choice, Path path, Throwable failure) {
        busy = false;
        request.busyChanged().accept(false);
        if (!request.stillValid().getAsBoolean()) return;
        if (failure == null) request.completed().complete(choice, path);
        else request.failed().accept(new PdfExportFailure(failure, choice == PdfExportChoice.VIEW && path != null && path.toFile().exists() ? path : null));
    }

    public record Request<R>(Window owner, String suggestedFilename, BooleanSupplier ready,
                             Supplier<R> capture, PdfDocumentWriter<R> renderer,
                             BooleanSupplier stillValid, CompletionHandler completed,
                             Consumer<PdfExportFailure> failed, Consumer<Boolean> busyChanged) {
        public Request {
            Objects.requireNonNull(suggestedFilename); Objects.requireNonNull(ready); Objects.requireNonNull(capture);
            Objects.requireNonNull(renderer); Objects.requireNonNull(stillValid); Objects.requireNonNull(completed);
            Objects.requireNonNull(failed); Objects.requireNonNull(busyChanged);
        }
    }

    @FunctionalInterface public interface ChoicePrompt { PdfExportChoice choose(Window owner); }
    @FunctionalInterface public interface DestinationChooser { Optional<Path> choose(Window owner, String suggestedFilename); }
    @FunctionalInterface public interface PdfDocumentWriter<R> { void render(R report, Path destination) throws Exception; }
    @FunctionalInterface public interface CompletionHandler { void complete(PdfExportChoice choice, Path path); }
    public record PdfExportFailure(Throwable cause, Path retainedPreview) { }
}
