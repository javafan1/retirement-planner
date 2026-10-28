package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationBrandingLoaderTest {
    @TempDir Path directory;
    private ApplicationBrandingLoader loader() {
        return new ApplicationBrandingLoader(directory.resolve("branding.properties"),
                name -> getClass().getResourceAsStream("/" + name));
    }
    @Test void packagedDefaultsMissingExternalAndImmutableWarnings() {
        var loaded = loader().load();
        assertEquals(ApplicationBranding.defaults(), loaded.branding());
        assertTrue(loaded.warnings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> loaded.warnings().add("change"));
        assertFalse(Files.exists(directory.resolve("branding.properties")));
    }
    @Test void partialUtf8OverridesAreRestartOnlyAndLeaveDocumentUntouched() throws Exception {
        var file = directory.resolve("branding.properties");
        Files.writeString(file, "app.title=Édition Aledrix\napp.tagline=Planning — tomorrow\n");
        var original = Files.readAllBytes(file);
        var first = loader().load();
        assertEquals("Édition Aledrix", first.branding().applicationTitle());
        assertEquals("Planning — tomorrow", first.branding().tagline());
        assertEquals(ApplicationBranding.defaults().productName(), first.branding().productName());
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.writeString(file, "app.title=Next edition\n");
        assertEquals("Édition Aledrix", first.branding().applicationTitle());
        assertEquals("Next edition", loader().load().branding().applicationTitle());
    }
    @ParameterizedTest @ValueSource(strings = {
            "app.title=Override\napp.title=Duplicate", "app.title=Override\napp.tagline=\\uQQQQ",
            "app.title=Override\napp.t\\u0069tle=Duplicate"})
    void malformedDocumentRejectsWholeOverride(String content) throws Exception {
        var file = directory.resolve("branding.properties");
        Files.writeString(file, content);
        var loaded = loader().load();
        assertEquals(ApplicationBranding.defaults(), loaded.branding());
        assertFalse(loaded.warnings().isEmpty());
        assertEquals(content, Files.readString(file));
    }
    @ParameterizedTest @ValueSource(strings = { "", "../logo.png", "file:/logo.png", "https://example.com/logo.png",
            "/images/branding/aledrix-logo.png", "images/branding/../logo.png", "images/branding/aledrix-logo-reference.png" })
    void invalidFieldsFallBackIndependently(String logo) throws Exception {
        Files.writeString(directory.resolve("branding.properties"), "app.title=Valid override\napp.productName=\napp.logo=" + logo);
        var loaded = loader().load();
        assertEquals("Valid override", loaded.branding().applicationTitle());
        assertEquals(ApplicationBranding.defaults().logoResource(), loaded.branding().logoResource());
        assertEquals(ApplicationBranding.defaults().productName(), loaded.branding().productName());
        assertFalse(loaded.warnings().isEmpty());
    }
    @Test void controlsLongFieldsAndUnknownKeysAreNotApplied() throws Exception {
        Files.writeString(directory.resolve("branding.properties"), "app.title=Bad\\u0000title\napp.productName=" + "x".repeat(161)
                + "\napp.tagline=" + "x".repeat(241) + "\nother.key=unknown\n");
        assertEquals(ApplicationBranding.defaults(), loader().load().branding());
        assertFalse(loader().load().warnings().isEmpty());
    }
    @Test void oversizedInvalidUtf8AndDirectoryInputsArePreserved() throws Exception {
        var file = directory.resolve("branding.properties");
        Files.writeString(file, "x".repeat(ApplicationBrandingLoader.MAX_BYTES + 1));
        assertEquals(ApplicationBranding.defaults(), loader().load().branding());
        Files.write(file, new byte[]{(byte)0xc3, (byte)0x28});
        assertEquals(ApplicationBranding.defaults(), loader().load().branding());
        var folder = directory.resolve("folder"); Files.createDirectory(folder);
        assertFalse(new ApplicationBrandingLoader(folder, name -> getClass().getResourceAsStream("/" + name)).load().warnings().isEmpty());
    }
    @Test void badPackagedDefaultsHaveValidatedEmergencyFallback() {
        var loaded = new ApplicationBrandingLoader(directory.resolve("missing"), name -> null).load();
        assertEquals(ApplicationBranding.defaults(), loaded.branding());
        assertFalse(loaded.warnings().isEmpty());
    }
}
