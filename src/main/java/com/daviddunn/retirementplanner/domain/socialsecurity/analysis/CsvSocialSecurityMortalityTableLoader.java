package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict offline CSV loader for age,male_qx,female_qx mortality resources. */
public final class CsvSocialSecurityMortalityTableLoader {

    private static final String EXPECTED_HEADER = "age,male_qx,female_qx";

    public SocialSecurityMortalityTable loadResource(
            String resourcePath,
            SocialSecurityMortalityTableMetadata metadata) throws IOException {
        Objects.requireNonNull(resourcePath, "Mortality resource path is required.");
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException(
                        "Mortality table resource not found: " + resourcePath);
            }
            return load(input, metadata);
        }
    }

    public SocialSecurityMortalityTable load(
            InputStream input,
            SocialSecurityMortalityTableMetadata metadata) throws IOException {
        Objects.requireNonNull(input, "Mortality table input is required.");
        Objects.requireNonNull(metadata, "Mortality table metadata is required.");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!EXPECTED_HEADER.equals(header)) {
                throw new IOException(
                        "Mortality table header must be " + EXPECTED_HEADER + ".");
            }

            List<SocialSecurityMortalityTableEntry> entries = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] values = line.split(",", -1);
                if (values.length != 3) {
                    throw new IOException(
                            "Mortality table line "
                                    + lineNumber
                                    + " must contain age,male_qx,female_qx.");
                }
                try {
                    entries.add(new SocialSecurityMortalityTableEntry(
                            Integer.parseInt(values[0].trim()),
                            new BigDecimal(values[1].trim()),
                            new BigDecimal(values[2].trim())));
                } catch (NumberFormatException exception) {
                    throw new IOException(
                            "Invalid mortality numeric value on line " + lineNumber + ".",
                            exception);
                } catch (IllegalArgumentException exception) {
                    throw new IOException(
                            "Invalid mortality data on line "
                                    + lineNumber
                                    + ": "
                                    + exception.getMessage(),
                            exception);
                }
            }
            try {
                return new SocialSecurityMortalityTable(metadata, entries);
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Invalid mortality table: " + exception.getMessage(),
                        exception);
            }
        }
    }
}
