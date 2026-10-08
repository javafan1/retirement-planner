package com.daviddunn.retirementplanner;

import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.junit.jupiter.api.Assertions.*;

/** Shared opt-in page previews and semantic checks over actual export files. */
public final class Stage4cPdfChecks {
    public static Path path(String name) throws Exception {
        var root = Path.of("target/stage4c-preview"); Files.createDirectories(root); return root.resolve(name + ".pdf");
    }
    public static String inspect(Path path) throws Exception {
        try (var doc = Loader.loadPDF(path.toFile())) {
            var text = new PDFTextStripper().getText(doc);
            assertFalse(text.toLowerCase().contains("spouse"), text);
            assertFalse(text.toLowerCase().contains("survivor"), text);
            assertFalse(text.toLowerCase().contains("second death"), text);
            assertFalse(text.toLowerCase().contains("second-death"), text);
            assertTrue(doc.getNumberOfPages() > 0);
            Files.writeString(path.resolveSibling(path.getFileName() + ".pages"), "" + doc.getNumberOfPages());
            if (Boolean.getBoolean("single.stage4c.preview")) {
                var renderer = new PDFRenderer(doc);
                for (int page = 0; page < doc.getNumberOfPages(); page++) javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 100),
                        "png", path.resolveSibling(path.getFileName().toString().replace(".pdf", "-" + (page + 1) + ".png")).toFile());
            }
            return text;
        }
    }
}
