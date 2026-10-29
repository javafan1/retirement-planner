package com.daviddunn.retirementplanner.application.branding;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Immutable packaged build metadata. Never reads a plan, preferences, filesystem Git repository or clock. */
public record ApplicationVersion(
        String productVersion,
        String commitHash,
        String abbreviatedCommit,
        Designation designation,
        Optional<Instant> buildTimestamp) {

    public enum Designation { DEVELOPMENT, RELEASE }

    private static final int MAX_BYTES = 8 * 1024;

    public ApplicationVersion {
        Objects.requireNonNull(productVersion);
        Objects.requireNonNull(commitHash);
        Objects.requireNonNull(abbreviatedCommit);
        Objects.requireNonNull(designation);
        Objects.requireNonNull(buildTimestamp);
        if (!productVersion.equals("unknown")
                && !productVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) {
            throw new IllegalArgumentException("Invalid product version.");
        }
        boolean unknown = commitHash.equals("unknown") && abbreviatedCommit.equals("unknown");
        if (!unknown && (!commitHash.matches("[0-9a-f]{40}|[0-9a-f]{64}")
                || !abbreviatedCommit.matches("[0-9a-f]{7,16}")
                || !commitHash.startsWith(abbreviatedCommit))) {
            throw new IllegalArgumentException("Invalid build commit identity.");
        }
    }

    private static final class Packaged {
        private static final ApplicationVersion VALUE = read(
                ApplicationVersion.class.getResourceAsStream("/application-version.properties"));
    }

    public static ApplicationVersion current() {
        return Packaged.VALUE;
    }

    /** Reads and closes a bounded metadata stream. Invalid fields fall back without inventing identity. */
    public static ApplicationVersion read(InputStream input) {
        var properties = new Properties();
        if (input != null) {
            try (input) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Metadata exceeds limit.");
                properties.load(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            } catch (IOException | IllegalArgumentException failure) {
                properties.clear();
            }
        }
        String version = properties.getProperty("product.version", "unknown");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) version = "unknown";
        String commit = properties.getProperty("build.commit", "unknown");
        String abbreviated = properties.getProperty("build.commit.abbrev", "unknown");
        if (!commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")
                || !abbreviated.matches("[0-9a-f]{7,16}") || !commit.startsWith(abbreviated)) {
            commit = "unknown";
            abbreviated = "unknown";
        }
        var designation = "release".equals(properties.getProperty("build.designation"))
                ? Designation.RELEASE : Designation.DEVELOPMENT;
        Optional<Instant> timestamp;
        try {
            timestamp = Optional.of(Instant.parse(properties.getProperty("build.timestamp", "")));
        } catch (java.time.format.DateTimeParseException failure) {
            timestamp = Optional.empty();
        }
        return new ApplicationVersion(version, commit, abbreviated, designation, timestamp);
    }

    public String buildIdentifier() {
        return abbreviatedCommit.equals("unknown") ? "unknown" : "git-" + abbreviatedCommit;
    }

    public String designationLabel() {
        return designation == Designation.RELEASE ? "Release" : "Development";
    }

    public String displaySummary() {
        return "Version " + productVersion + " | Build " + buildIdentifier() + " | " + designationLabel();
    }
}
