package com.daviddunn.retirementplanner.ui.pdf;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface PdfOpener {
    void open(Path pdf) throws IOException;
}
