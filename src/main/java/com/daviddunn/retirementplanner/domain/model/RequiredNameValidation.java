package com.daviddunn.retirementplanner.domain.model;

/** Validation used when a person-facing name is explicitly entered or edited. */
public final class RequiredNameValidation {
    private RequiredNameValidation() { }

    /** Returns the name with only surrounding whitespace removed. */
    public static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.strip();
    }
}
