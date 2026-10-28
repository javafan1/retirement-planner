package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Read-only, bounded deployment overrides. Never writes or migrates preferences. */
public final class ApplicationBrandingLoader {
    public static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> KEYS = Set.of("app.title", "app.productName", "app.tagline", "app.logo");
    public record Loaded(ApplicationBranding branding, List<String> warnings) {
        public Loaded { warnings = List.copyOf(warnings); }
    }
    private final Path external;
    private final Function<String, InputStream> resources;

    public ApplicationBrandingLoader() {
        this(Path.of(System.getProperty("user.home"), ".retirement-planner", "application-branding.properties"),
                name -> ApplicationBrandingLoader.class.getResourceAsStream("/" + name));
    }

    public ApplicationBrandingLoader(Path external, Function<String, InputStream> resources) {
        this.external = Objects.requireNonNull(external);
        this.resources = Objects.requireNonNull(resources);
    }

    public Loaded load() {
        var warnings = new ArrayList<String>();
        var defaults = ApplicationBranding.defaults();
        try (var input = resources.apply("application-branding.properties")) {
            if (input == null) throw new IOException("Missing defaults");
            defaults = apply(defaults, read(input), warnings);
        } catch (IOException | RuntimeException failure) {
            warnings.add("Packaged branding could not be loaded; built-in defaults were used.");
        }
        try {
            if (Files.notExists(external)) return new Loaded(defaults, warnings);
            try (var input = Files.newInputStream(external)) {
                return new Loaded(apply(defaults, read(input), warnings), warnings);
            }
        } catch (IOException | RuntimeException failure) {
            warnings.add("External branding could not be loaded; packaged defaults were used. The file was preserved.");
            return new Loaded(defaults, warnings);
        }
    }

    private Properties read(InputStream input) throws IOException {
        var bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Branding document exceeds limit");
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate branding key");
                return super.put(key, value);
            }
        };
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        properties.load(new InputStreamReader(new ByteArrayInputStream(bytes), decoder));
        return properties;
    }

    private ApplicationBranding apply(ApplicationBranding fallback, Properties properties, List<String> warnings) {
        if (properties.stringPropertyNames().stream().anyMatch(key -> !KEYS.contains(key))) {
            warnings.add("Unknown branding properties were ignored.");
        }
        String title = value(properties, "app.title", fallback.applicationTitle(), 160, warnings);
        String product = value(properties, "app.productName", fallback.productName(), 160, warnings);
        String tagline = value(properties, "app.tagline", fallback.tagline(), 240, warnings);
        String logo = value(properties, "app.logo", fallback.logoResource(), 256, warnings);
        try { new ApplicationBranding(title, product, tagline, logo); }
        catch (IllegalArgumentException failure) {
            warnings.add("Invalid logo resource path; packaged logo selection was retained.");
            logo = fallback.logoResource();
        }
        return new ApplicationBranding(title, product, tagline, logo);
    }

    private String value(Properties properties, String key, String fallback, int limit, List<String> warnings) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try { return ApplicationBranding.text(value, limit); }
        catch (IllegalArgumentException failure) {
            warnings.add("Invalid " + key + "; packaged value was retained.");
            return fallback;
        }
    }
}
