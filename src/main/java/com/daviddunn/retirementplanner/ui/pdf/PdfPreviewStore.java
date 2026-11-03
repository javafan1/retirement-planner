package com.daviddunn.retirementplanner.ui.pdf;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisStorageLocation;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/** Private app-owned PDF previews. Only marker-verified previews are eligible for stale cleanup. */
public final class PdfPreviewStore {
    private static final String STORE_MARKER = ".aledrix-preview-store-v1";
    private static final String MARKER_CONTENT_PREFIX = "Aledrix managed PDF preview v1\n";
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final String PREFIX = "preview-";
    private final Path directory;
    private final Clock clock;
    private volatile String storeIdentity;

    public PdfPreviewStore(Path directory) { this(directory, Clock.systemUTC()); }

    public PdfPreviewStore(Path directory, Clock clock) {
        this.directory = directory.toAbsolutePath().normalize();
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public static Path systemDefaultDirectory() {
        Path analysis = AnalysisStorageLocation.systemDefault();
        return analysis.getParent().getParent().resolve("pdf-previews").resolve("v1");
    }

    public Path directory() { return directory; }

    /** Renders to a private partial file and publishes it only after PDF signature validation. */
    public Path create(PreviewWriter writer) throws Exception {
        java.util.Objects.requireNonNull(writer);
        ensureDirectory();
        String name = PREFIX + UUID.randomUUID() + ".pdf";
        Path target = child(name);
        Path marker = child(name + ".aledrix-owner");
        Path partial = child("." + name + ".part");
        Path partialMarker = child(partial.getFileName() + ".aledrix-owner");
        boolean markerCreated = false;
        try {
            Files.createFile(partial);
            restrictFile(partial);
            writeOwnedMarker(partialMarker);
            writer.write(partial);
            validatePdf(partial);
            Files.move(partialMarker, marker);
            markerCreated = true;
            Files.move(partial, target); // no REPLACE_EXISTING: a collision never overwrites a preview.
            return target;
        } catch (Exception failure) {
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
            try { Files.deleteIfExists(partialMarker); } catch (IOException ignored) { }
            if (markerCreated) try { Files.deleteIfExists(marker); } catch (IOException ignored) { }
            throw failure;
        }
    }

    /** Best-effort deletion of marker-verified complete previews older than the retention period. */
    public CleanupResult cleanupStale() {
        int deleted = 0;
        int retained = 0;
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return new CleanupResult(0, 0);
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                return new CleanupResult(0, 0);
            if (!loadStoreIdentity()) return new CleanupResult(0, 0);
            Instant cutoff = clock.instant().minus(RETENTION);
            try (var entries = Files.newDirectoryStream(directory, "*.aledrix-owner")) {
                for (Path marker : entries) {
                    try {
                        if (!ownedMarker(marker)) { retained++; continue; }
                        String markerName = marker.getFileName().toString();
                        String dataName = markerName.substring(0, markerName.length() - ".aledrix-owner".length());
                        Path data = child(dataName);
                        boolean partialFile = dataName.startsWith("." + PREFIX) && dataName.endsWith(".pdf.part");
                        boolean completeFile = dataName.startsWith(PREFIX) && dataName.endsWith(".pdf");
                        if ((!partialFile && !completeFile) || Files.isSymbolicLink(data)
                                || !Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS)) {
                            retained++;
                            continue;
                        }
                        Instant modified = Files.getLastModifiedTime(data, LinkOption.NOFOLLOW_LINKS).toInstant();
                        if (modified.isAfter(cutoff)) { retained++; continue; }
                        Files.delete(data);
                        Files.delete(marker);
                        deleted++;
                    } catch (IOException | RuntimeException unavailable) {
                        retained++;
                    }
                }
            }
        } catch (IOException | SecurityException ignored) {
            // Cleanup is explicitly best effort and must never prevent application startup.
        }
        return new CleanupResult(deleted, retained);
    }

    private boolean ownedMarker(Path marker) throws IOException {
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return false;
        return markerContent().equals(Files.readString(marker));
    }

    private String markerContent() throws IOException {
        if (storeIdentity == null && !loadStoreIdentity()) throw new IOException("PDF preview store identity is unavailable.");
        return MARKER_CONTENT_PREFIX + storeIdentity + "\n";
    }

    private synchronized boolean loadStoreIdentity() throws IOException {
        if (storeIdentity != null) return true;
        Path marker = directory.resolve(STORE_MARKER);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return false;
            String value = Files.readString(marker);
            if (!value.matches("[0-9a-fA-F-]{36}\\R?")) return false;
            storeIdentity = value.strip();
            return true;
        }
        String generated = UUID.randomUUID().toString();
        Path temporary = directory.resolve("." + STORE_MARKER + "-" + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(temporary, generated + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictFile(temporary);
            try { Files.move(temporary, marker); }
            catch (FileAlreadyExistsException concurrent) { Files.deleteIfExists(temporary); return loadStoreIdentity(); }
            storeIdentity = generated;
            return true;
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private void writeOwnedMarker(Path marker) throws IOException {
        Path temporary = child("." + UUID.randomUUID() + ".owner-tmp");
        try {
            Files.writeString(temporary, markerContent(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictFile(temporary);
            Files.move(temporary, marker);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("PDF preview directory is not a safe directory.");
        restrictDirectory(directory);
        if (!loadStoreIdentity()) throw new IOException("PDF preview directory ownership could not be verified.");
    }

    private Path child(String name) throws IOException {
        Path result = directory.resolve(name).normalize();
        if (!result.getParent().equals(directory)) throw new IOException("Invalid PDF preview path.");
        return result;
    }

    private static void validatePdf(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file) < 10)
            throw new IOException("PDF renderer did not create a complete file.");
        byte[] signature = new byte[5];
        try (var input = Files.newInputStream(file, StandardOpenOption.READ)) {
            if (input.read(signature) != signature.length || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F' || signature[4] != '-')
                throw new IOException("PDF renderer did not create a valid PDF file.");
        }
        long size = Files.size(file);
        int tailLength = (int) Math.min(size, 1024);
        ByteBuffer tail = ByteBuffer.allocate(tailLength);
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(size - tailLength);
            while (tail.hasRemaining() && channel.read(tail) >= 0) { }
        }
        String ending = new String(tail.array(), java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!ending.contains("%%EOF")) throw new IOException("PDF renderer did not finish writing the PDF.");
    }

    private static void restrictDirectory(Path path) {
        try { Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)); }
        catch (IOException | UnsupportedOperationException | SecurityException ignored) { }
    }

    private static void restrictFile(Path path) {
        try { Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
        catch (IOException | UnsupportedOperationException | SecurityException ignored) { }
    }

    @FunctionalInterface public interface PreviewWriter { void write(Path destination) throws Exception; }
    public record CleanupResult(int deleted, int retained) { }
}
