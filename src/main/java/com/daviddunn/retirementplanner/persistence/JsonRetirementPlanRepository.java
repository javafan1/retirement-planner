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
    private final java.util.concurrent.ConcurrentMap<Path, String> durableStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<Path, java.util.Optional<com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations>> durableConfigurations = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, java.util.UUID> latestParameters = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.List<String> warnings = java.util.List.of();

    public JsonRetirementPlanRepository() {
        this(new AtomicFileWriter(), new ObjectMapper());
    }

    JsonRetirementPlanRepository(AtomicFileWriter writer, ObjectMapper mapper) {

        this.writer = java.util.Objects.requireNonNull(writer);
        this.mapper = java.util.Objects.requireNonNull(mapper);

        mapper.registerModule(new JavaTimeModule());

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
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
            file = file.toAbsolutePath().normalize();
            AtomicFileWriter.regularOrMissing(file);
            try (var lock = PlanConfigurationTransaction.lock(file)) {
                String expected = durableStates.get(file);
                // Explicit ordinary Save may recreate a missing primary (existing recovery contract).
                // Automatic metadata writes never take this path and still require an intact binding.
                if (!java.nio.file.Files.exists(file)) expected = null;
                if (expected == null && java.nio.file.Files.exists(file)) expected = state(file);
                final String expectedState = expected;
                checkState(file, expectedState);
                if (originalForBackup(file).isPresent()) {
                    throw new IOException("Application backups are recovery sources. Use Save As to a different filename.");
                }
                plan.validateHouseholdReferences();
                var projectionObjects = new ProjectionSnapshotComponents().prepare(plan,
                        plan.getProjectionComponentSource() == null ? file : plan.getProjectionComponentSource(), file, identity);
                byte[] original = mapper.writeValueAsBytes(plan);
                if (projectionObjects.manifest() != null) original = PlanConfigurationTransaction.replace(original,
                        "projectionState", mapper.writeValueAsBytes(projectionObjects.manifest()));
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
                var committedParameters = readBytes(captured).getAnalyzerConfigurations();
                warnings = writer.write(file, captured, this::verify, true, path -> checkState(path, expectedState));
                plan.assignPlanId(identity);
                if (projectionObjects.manifest() != null) {
                    plan.setProjectionState(projectionObjects.manifest());
                    plan.installProjectionComponents(projectionObjects.components());
                    plan.installPendingPinnedProjections(java.util.Map.of());
                    plan.installAssociatedAnalyzerResults(projectionObjects.analyzers());
                    plan.installPendingAnalyzerAssociations(java.util.Map.of());
                    plan.setProjectionComponentSource(file);
                }
                durableStates.put(file, PlanConfigurationTransaction.digest(captured));
                durableConfigurations.put(file, java.util.Optional.ofNullable(committedParameters));
            }
        }
    }

    private void verify(Path path) throws IOException {
        try {
            read(path);
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

        synchronized (AtomicFileWriter.SAVE_LOCK) {
            byte[] original = java.nio.file.Files.readAllBytes(file);
            RetirementPlan plan = readBytes(original);
            plan.setProjectionComponentSource(originalForBackup(file).orElse(file).toAbsolutePath().normalize());
            durableStates.put(file.toAbsolutePath().normalize(), PlanConfigurationTransaction.digest(original));
            durableConfigurations.put(file.toAbsolutePath().normalize(), java.util.Optional.ofNullable(plan.getAnalyzerConfigurations()));
            return plan;
        }
    }

    private RetirementPlan read(Path file) throws IOException {
        return readBytes(java.nio.file.Files.readAllBytes(file));
    }

    private RetirementPlan readBytes(byte[] bytes) throws IOException {

        var json = mapper.readTree(bytes);
        if (json == null) {
            throw new IOException("Plan JSON is empty.");
        }
        // This tree is for financial validation only; future metadata may use unrelated field names.
        if (json.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove(java.util.List.of("analyzerConfigurations", "projectionState"));
        validateExplicitStateConfiguration(json);
        // Deserialize original numeric tokens, preserving existing BigDecimal precision and scale.
        RetirementPlan plan = mapper.readValue(bytes, RetirementPlan.class);
        plan.validateHouseholdReferences();
        if (plan.getProjectionState() != null && !plan.getProjectionState().owner().toString().equals(plan.getPlanId()))
            throw new IOException("Projection role ownership does not match the plan.");
        return plan;
    }

    /** Fast acceptance registration; no disk access and no financial-state mutation. */
    public void registerParameters(Path file, com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration configuration) {
        latestParameters.put(slot(file, configuration.kind()), configuration.acceptedRunId());
    }

    public void saveParameters(Path selected, java.util.UUID identity,
            com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration configuration) throws IOException {
        synchronized (AtomicFileWriter.SAVE_LOCK) {
            Path file = selected.toAbsolutePath().normalize();
            AtomicFileWriter.regularOrMissing(file);
            try (var lock = PlanConfigurationTransaction.lock(file)) {
                String expected = durableStates.get(file);
                if (expected == null) throw new IOException("Plan must be opened or saved before automatic configuration writes.");
                checkState(file, expected);
                RetirementPlan durable = read(file);
                if (!identity.toString().equals(durable.getPlanId())) throw new IOException("Plan identity changed; configuration was not saved.");
                if ((configuration.shape() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.Shape.COUPLE)
                        != durable.getHousehold().hasSpouse()) throw new IOException("Configuration household mismatch.");
                checkLatest(file, configuration);
                var configurations = durable.getAnalyzerConfigurations();
                if (configurations == null) configurations = com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations.empty();
                var next = configurations.with(configuration);
                byte[] captured = PlanConfigurationTransaction.replace(java.nio.file.Files.readAllBytes(file),
                        "analyzerConfigurations", next.json().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                warnings = writer.write(file, captured, this::verify, true, path -> {
                    checkState(path, expected); checkLatest(file, configuration);
                });
                durableStates.put(file, PlanConfigurationTransaction.digest(captured));
                durableConfigurations.put(file, java.util.Optional.of(next));
            }
        }
    }

    /** Exact metadata from the last verified commit/load, not mutable controls or a later acceptance. */
    public java.util.Optional<com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations> committedConfigurations(Path file) {
        return durableConfigurations.getOrDefault(file.toAbsolutePath().normalize(), java.util.Optional.empty());
    }

    private void checkLatest(Path file, com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration configuration) throws IOException {
        if (!configuration.acceptedRunId().equals(latestParameters.get(slot(file, configuration.kind())))) {
            throw new IOException("Configuration acceptance was superseded.");
        }
    }
    private static String slot(Path file, com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind) {
        return file.toAbsolutePath().normalize() + ":" + kind.name();
    }
    private static String state(Path file) throws IOException { return PlanConfigurationTransaction.digest(java.nio.file.Files.readAllBytes(file)); }
    private static void checkState(Path file, String expected) throws IOException {
        boolean exists = java.nio.file.Files.exists(file);
        if (expected == null ? exists : !exists || !expected.equals(state(file))) {
            throw new IOException("The plan file changed externally. Reopen it before saving; existing data was preserved.");
        }
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
