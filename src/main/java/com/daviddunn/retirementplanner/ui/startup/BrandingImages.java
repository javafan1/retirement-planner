package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import javafx.scene.image.Image;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

/** Bounded classpath PNG decoding; missing artwork never prevents startup. */
public final class BrandingImages {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final String ICON = "images/branding/aledrix-icon.png";
    private BrandingImages() { }

    public static Optional<Image> load(String resource) {
        return load(resource, name -> BrandingImages.class.getResourceAsStream("/" + name));
    }

    public static Optional<Image> load(String resource, Function<String, InputStream> resources) {
        try {
            new ApplicationBranding("Title", "Product", "Tagline", resource);
            try (var input = resources.apply(resource)) {
                if (input == null) return Optional.empty();
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES || bytes.length < 33) return Optional.empty();
                var header = ByteBuffer.wrap(bytes);
                if (header.getLong() != 0x89504e470d0a1a0aL || header.getInt() != 13
                        || header.getInt() != 0x49484452) return Optional.empty();
                int width = header.getInt(), height = header.getInt();
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return Optional.empty();
                var image = new Image(new ByteArrayInputStream(bytes));
                return image.isError() ? Optional.empty() : Optional.of(image);
            }
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }
}
