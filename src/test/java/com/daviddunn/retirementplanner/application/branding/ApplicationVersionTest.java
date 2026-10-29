package com.daviddunn.retirementplanner.application.branding;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationVersionTest {
    private static final String COMMIT = "123456789abcdef0123456789abcdef012345678";

    private ApplicationVersion read(String text) {
        return ApplicationVersion.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void mavenFiltersProductVersionAndPackagedMetadata() {
        var current = ApplicationVersion.current();
        assertEquals("0.91.0", current.productVersion());
        assertSame(current, ApplicationVersion.current());
        assertNotEquals("unknown", current.buildTimestamp().map(Instant::toString).orElse("unknown"));
        if (!current.commitHash().equals("unknown")) {
            assertTrue(current.commitHash().startsWith(current.abbreviatedCommit()));
            assertEquals(12, current.abbreviatedCommit().length());
            assertEquals("git-" + current.abbreviatedCommit(), current.buildIdentifier());
        }
    }

    @Test void stableIdentityDoesNotIncludeTimestampOrDesignation() {
        String base = "product.version=0.91.0\nbuild.commit=" + COMMIT + "\nbuild.commit.abbrev=123456789abc\n";
        var development = read(base + "build.designation=development\nbuild.timestamp=2026-10-29T12:00:00Z\n");
        var release = read(base + "build.designation=release\nbuild.timestamp=2026-10-30T12:00:00Z\n");
        assertEquals("git-123456789abc", development.buildIdentifier());
        assertEquals(development.buildIdentifier(), release.buildIdentifier());
        assertEquals(development.productVersion(), release.productVersion());
        assertNotEquals(development.buildTimestamp(), release.buildTimestamp());
        assertEquals(ApplicationVersion.Designation.DEVELOPMENT, development.designation());
        assertEquals(ApplicationVersion.Designation.RELEASE, release.designation());
        assertTrue(release.displaySummary().endsWith("Release"));
    }

    @Test void missingGitRetainsMavenVersionWithoutInventedIdentity() {
        var version = read("product.version=0.91.0\nbuild.commit=unknown\nbuild.commit.abbrev=unknown\n");
        assertEquals("0.91.0", version.productVersion());
        assertEquals("unknown", version.buildIdentifier());
        assertEquals("unknown", version.commitHash());
        assertTrue(version.buildTimestamp().isEmpty());
        assertEquals("Development", version.designationLabel());
    }

    @Test void unresolvedIdeResourcesAndMalformedOrMissingDocumentsAreExplicitlyUnknown() {
        var absent = ApplicationVersion.read(null);
        for (var invalid : new ApplicationVersion[] {
                absent,
                read("product.version=${project.version}\nbuild.commit=${git.commit.id}\nbuild.timestamp=${maven.build.timestamp}\n"),
                read("product.version=0.91.0\nbroken=\\uZZZZ"),
                read("x".repeat(8193)) }) {
            assertEquals("unknown", invalid.productVersion());
            assertEquals("unknown", invalid.buildIdentifier());
            assertTrue(invalid.buildTimestamp().isEmpty());
        }
        assertEquals(absent, ApplicationVersion.read(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Unreadable metadata"); }
        }));
    }

    @Test void invalidFieldsDoNotPoisonIndependentValidMetadata() {
        var version = read("product.version=0.91.0\nbuild.commit=" + COMMIT
                + "\nbuild.commit.abbrev=ffffffffffff\nbuild.designation=unexpected\nbuild.timestamp=yesterday\n");
        assertEquals("0.91.0", version.productVersion());
        assertEquals("unknown", version.buildIdentifier());
        assertEquals("Development", version.designationLabel());
        assertTrue(version.buildTimestamp().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ApplicationVersion(
                "0.91.0", COMMIT, "ffffffffffff", ApplicationVersion.Designation.RELEASE, java.util.Optional.empty()));
    }
}
