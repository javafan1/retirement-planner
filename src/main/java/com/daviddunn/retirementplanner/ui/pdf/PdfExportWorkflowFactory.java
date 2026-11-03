package com.daviddunn.retirementplanner.ui.pdf;

import javafx.application.Platform;

import java.util.concurrent.Executor;

/** Creates a per-export-surface coordinator with the production desktop adapters. */
public final class PdfExportWorkflowFactory {
    private PdfExportWorkflowFactory() { }

    public static <R> PdfExportWorkflowCoordinator<R> create(String chooserTitle) {
        return create(chooserTitle, PdfExportDialogs.saveChooser(chooserTitle));
    }

    public static <R> PdfExportWorkflowCoordinator<R> create(String chooserTitle,
            PdfExportWorkflowCoordinator.DestinationChooser chooser) {
        Executor worker = command -> Thread.ofVirtual()
                .name("aledrix-pdf-export-")
                .start(command);
        return new PdfExportWorkflowCoordinator<>(PdfExportDialogs.choicePrompt(),
                chooser,
                new PdfPreviewStore(PdfPreviewStore.systemDefaultDirectory()),
                new DesktopPdfOpener(), worker, Platform::runLater);
    }
}
