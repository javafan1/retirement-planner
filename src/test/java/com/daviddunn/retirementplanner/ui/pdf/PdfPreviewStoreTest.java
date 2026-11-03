package com.daviddunn.retirementplanner.ui.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PdfPreviewStoreTest {
    @TempDir Path temporary;

    @Test void previewsAreUniqueAndOnlyPublishedAfterPdfSignatureValidation() throws Exception {
        var store = new PdfPreviewStore(temporary.resolve("previews"));
        var first = store.create(path -> writePdf(path, "first"));
        var second = store.create(path -> writePdf(path, "second"));
        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().matches("preview-[0-9a-f-]{36}\\.pdf"));
        assertTrue(Files.readString(first).startsWith("%PDF-"));
        assertTrue(Files.exists(first.resolveSibling(first.getFileName() + ".aledrix-owner")));
        assertThrows(Exception.class, () -> store.create(path -> Files.writeString(path, "not a pdf")));
        try (var files = Files.list(store.directory())) {
            assertEquals(5, files.count(), "store marker and two complete PDF/owner pairs remain; failed partial files are removed");
        }
    }

    @Test void concurrentInstancesCreateDistinctPreviewsWithoutOverwriting() throws Exception {
        Path directory = temporary.resolve("concurrent");
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Future<Path>>();
            for (int i = 0; i < 32; i++) {
                tasks.add(pool.submit(() -> new PdfPreviewStore(directory).create(path -> writePdf(path, "parallel"))));
            }
            var paths = new HashSet<Path>();
            for (var task : tasks) paths.add(task.get(10, TimeUnit.SECONDS));
            assertEquals(32, paths.size());
            assertEquals(32, paths.stream().filter(Files::exists).count());
        } finally { pool.shutdownNow(); }
    }

    @Test void cleanupHonorsThirtyDayBoundaryAndOnlyDeletesStoreOwnedFiles() throws Exception {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        var store = new PdfPreviewStore(temporary.resolve("cleanup"), Clock.fixed(now, ZoneOffset.UTC));
        Path stale = store.create(path -> writePdf(path, "stale"));
        Path fresh = store.create(path -> writePdf(path, "fresh"));
        Path unrelated = store.directory().resolve("preview-not-owned.pdf");
        Files.writeString(unrelated, "%PDF-1.4\nkeep");
        Path symlinkTarget = temporary.resolve("outside.pdf");
        Files.writeString(symlinkTarget, "%PDF-1.4\noutside");
        Path staleMarker = stale.resolveSibling(stale.getFileName() + ".aledrix-owner");
        Files.setLastModifiedTime(stale, FileTime.from(now.minusSeconds(30L * 24 * 60 * 60)));
        Files.setLastModifiedTime(staleMarker, FileTime.from(now.minusSeconds(30L * 24 * 60 * 60)));
        Files.setLastModifiedTime(fresh, FileTime.from(now.minusSeconds(30L * 24 * 60 * 60 - 1)));
        Files.setLastModifiedTime(fresh.resolveSibling(fresh.getFileName() + ".aledrix-owner"),
                FileTime.from(now.minusSeconds(30L * 24 * 60 * 60 - 1)));
        try {
            Path linked = store.directory().resolve("preview-linked.pdf");
            Files.createSymbolicLink(linked, symlinkTarget);
            Files.writeString(linked.resolveSibling(linked.getFileName() + ".aledrix-owner"),
                    Files.readString(staleMarker));
            Files.setLastModifiedTime(linked.resolveSibling(linked.getFileName() + ".aledrix-owner"),
                    FileTime.from(now.minusSeconds(31L * 24 * 60 * 60)));
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException unavailable) {
            // Symlink creation is platform/permission dependent; all other cleanup assertions still apply.
        }

        var result = store.cleanupStale();
        assertTrue(result.deleted() >= 1);
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(symlinkTarget));
    }

    @Test void missingDirectoryCleanupIsNoOpAndBadDirectoryIsNotFollowed() throws Exception {
        var missing = new PdfPreviewStore(temporary.resolve("missing"));
        assertEquals(0, missing.cleanupStale().deleted());
        Path target = temporary.resolve("real"); Files.createDirectory(target);
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
            assertEquals(0, new PdfPreviewStore(link).cleanupStale().deleted());
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException unavailable) { }
    }

    private static void writePdf(Path path, String contents) throws Exception {
        Files.writeString(path, "%PDF-1.7\n" + contents + "\n%%EOF\n");
    }
}
