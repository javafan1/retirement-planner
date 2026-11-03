package com.daviddunn.retirementplanner.ui.pdf;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Default-viewer bridge. It never invokes a shell or runs on the FX thread by itself. */
public final class DesktopPdfOpener implements PdfOpener {
    @Override public void open(Path pdf) throws IOException {
        if (pdf == null || !Files.isRegularFile(pdf)) throw new IOException("The generated PDF is unavailable.");
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("This system does not provide a desktop PDF viewer association.");
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) throw new IOException("Opening files with the system viewer is not supported.");
            desktop.open(pdf.toFile());
        } catch (SecurityException | UnsupportedOperationException failure) {
            throw new IOException("The system could not start the default PDF viewer.", failure);
        }
    }
}
