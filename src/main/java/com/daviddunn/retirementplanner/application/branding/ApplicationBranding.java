package com.daviddunn.retirementplanner.application.branding;

import java.util.Objects;

/** Restart-scoped presentation metadata, deliberately independent of plan inputs/preferences. */
public record ApplicationBranding(String applicationTitle, String productName, String tagline, String logoResource) {
    public static final String COMPANY_ATTRIBUTION = "A product of ZaZoot LLC, USA";
    public ApplicationBranding {
        applicationTitle = text(applicationTitle, 160);
        productName = text(productName, 160);
        tagline = text(tagline, 240);
        logoResource = text(logoResource, 256);
        if (!logoResource.matches("images/branding/[A-Za-z0-9_-]+\\.png")
                || logoResource.equals("images/branding/aledrix-logo-reference.png")) {
            throw new IllegalArgumentException("Use a production PNG in images/branding.");
        }
    }

    public static ApplicationBranding defaults() {
        return new ApplicationBranding("Aledrix Retirement Planning Science", "Aledrix Retirement Planning",
                "The Science of Retirement Planning", "images/branding/aledrix-logo.png");
    }

    public static String text(String value, int maximum) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid branding text.");
        }
        return value;
    }
}
