package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

public class GovernmentRulesRepository {

    private final ObjectMapper mapper;

    public GovernmentRulesRepository() {

        mapper = new ObjectMapper();

        mapper.registerModule(
                new JavaTimeModule());
    }

    public GovernmentRules load(
            String resourcePath)
            throws IOException {

        try (InputStream inputStream =
                     getClass()
                             .getResourceAsStream(
                                     resourcePath)) {

            if (inputStream == null) {

                throw new IOException(
                        "Government rules resource not found: "
                                + resourcePath);
            }

            return mapper.readValue(
                    inputStream,
                    GovernmentRules.class);
        }
    }
}