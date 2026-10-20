package com.daviddunn.retirementplanner.persistence;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;

public class JsonRetirementPlanRepository
        implements RetirementPlanRepository {

    private final ObjectMapper mapper;
    private final AtomicFileWriter writer;
    private java.util.List<String> warnings = java.util.List.of();

    public JsonRetirementPlanRepository() {
        this(new AtomicFileWriter(), new ObjectMapper());
    }

    JsonRetirementPlanRepository(AtomicFileWriter writer, ObjectMapper mapper) {

        this.writer = java.util.Objects.requireNonNull(writer);
        this.mapper = java.util.Objects.requireNonNull(mapper);

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void save(RetirementPlan plan,
                     Path file) throws IOException {

        saveWithIdentity(plan, file, plan.getPlanId() == null
                ? java.util.UUID.randomUUID() : RetirementPlan.validatedPlanId(plan.getPlanId()));
    }

    /** Identity is published to memory only after the verified file has committed. */
    public void saveWithIdentity(RetirementPlan plan, Path file, java.util.UUID identity) throws IOException {

        synchronized (AtomicFileWriter.SAVE_LOCK) {
            if (originalForBackup(file).isPresent()) {
                throw new IOException("Application backups are recovery sources. Use Save As to a different filename.");
            }
            plan.validateHouseholdReferences();
            byte[] original = mapper.writeValueAsBytes(plan);
            byte[] captured = original;
            if (!identity.toString().equals(plan.getPlanId())) {
                // Preserve original numeric tokens; a tree round trip could change monetary scale.
                com.fasterxml.jackson.core.JsonFactory factory = mapper.getFactory();
                var output = new java.io.ByteArrayOutputStream();
                try (var parser = factory.createParser(original); var generator = factory.createGenerator(output)) {
                    generator.setPrettyPrinter(new com.fasterxml.jackson.core.util.DefaultPrettyPrinter());
                    parser.nextToken();
                    generator.writeStartObject();
                    while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                        String name = parser.currentName();
                        parser.nextToken();
                        if (name.equals("planId")) parser.skipChildren();
                        else {
                            generator.writeFieldName(name);
                            int depth = 0;
                            do {
                                var token = parser.currentToken();
                                if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT
                                        || token == com.fasterxml.jackson.core.JsonToken.START_ARRAY) depth++;
                                if (token == com.fasterxml.jackson.core.JsonToken.END_OBJECT
                                        || token == com.fasterxml.jackson.core.JsonToken.END_ARRAY) depth--;
                                if (token.isNumeric()) generator.writeNumber(parser.getText());
                                else generator.copyCurrentEvent(parser);
                                if (depth > 0) parser.nextToken();
                            } while (depth > 0);
                        }
                    }
                    generator.writeStringField("planId", identity.toString());
                    generator.writeEndObject();
                }
                captured = output.toByteArray();
            }
            warnings = writer.write(file, captured, this::verify, true);
            plan.assignPlanId(identity);
        }
    }

    private void verify(Path path) throws IOException {
        try {
            load(path);
        } catch (RuntimeException invalid) {
            throw new IOException("The plan file failed validation. Existing data was preserved.", invalid);
        }
    }

    public java.util.List<String> takeWarnings() {
        synchronized (AtomicFileWriter.SAVE_LOCK) {
            var result = warnings;
            warnings = java.util.List.of();
            return java.util.List.copyOf(result);
        }
    }

    public Path backupPath(Path file) {
        return AtomicFileWriter.backup(file.toAbsolutePath().normalize());
    }

    public java.util.Optional<Path> originalForBackup(Path file) throws IOException {
        return AtomicFileWriter.originalForBackup(file);
    }

    /** Read-only validation; never makes a backup authoritative or writes over the primary. */
    public RetirementPlan loadBackup(Path primary) throws IOException {
        Path backup = backupPath(primary);
        AtomicFileWriter.regularOrMissing(backup);
        return load(backup);
    }

    @Override
    public RetirementPlan load(Path file)
            throws IOException {

        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        var json = mapper.readTree(bytes);
        if (json == null) {
            throw new IOException("Plan JSON is empty.");
        }
        validateExplicitStateConfiguration(json);
        // Deserialize original numeric tokens, preserving existing BigDecimal precision and scale.
        RetirementPlan plan = mapper.readValue(bytes, RetirementPlan.class);
        plan.validateHouseholdReferences();
        return plan;
    }

    private static void validateExplicitStateConfiguration(com.fasterxml.jackson.databind.JsonNode node) throws IOException {
        if (node.isObject() && node.has("stateTaxAssumptions") && node.get("stateTaxAssumptions").isNull()) {
            throw new IOException("Explicit stateTaxAssumptions must not be null; omit the field only for legacy Michigan plans.");
        }
        for (var child : node) {
            validateExplicitStateConfiguration(child);
        }
    }

    private static ObjectMapper createObjectMapper() {

        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }

   // private final ObjectMapper mapper = createObjectMapper();
}
