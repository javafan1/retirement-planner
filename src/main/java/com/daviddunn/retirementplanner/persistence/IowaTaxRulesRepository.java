package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.tax.state.iowa.IowaTaxRules;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Loads and validates immutable rules once, outside annual funding trials. */
public final class IowaTaxRulesRepository {

    public IowaTaxRules load() {
        return Cache.RULES;
    }

    IowaTaxRules parse(InputStream input) throws IOException {
        return new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .readValue(Objects.requireNonNull(input), IowaTaxRules.class);
    }

    private static final class Cache {
        private static final IowaTaxRules RULES = loadResource();

        private static IowaTaxRules loadResource() {
            try (var input = IowaTaxRulesRepository.class.getResourceAsStream("/tax/state/iowa-tax-2026.json")) {
                if (input == null) {
                    throw new IOException("Missing Iowa tax rules.");
                }
                return new IowaTaxRulesRepository().parse(input);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
