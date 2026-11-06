package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Only the new capability is optional. Every original envelope property remains mandatory. */
public final class AnalysisSnapshotReader extends JsonDeserializer<AnalysisSnapshot> {
    private static final Set<String> ORIGINAL = Set.of("schemaVersion", "fingerprintVersion", "planId", "kind",
            "shape", "runId", "completedAt", "applicationBuild", "algorithms", "references", "inputFingerprint",
            "contextFingerprint", "payloadDigest", "payload", "context", "originPlanId");

    @Override public AnalysisSnapshot deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken())
            return (AnalysisSnapshot) context.handleUnexpectedToken(AnalysisSnapshot.class, parser);
        int schema = 0, fingerprintVersion = 0;
        UUID plan = null, run = null, origin = null;
        AnalysisKind kind = null; AnalysisKind.Shape shape = null; Instant completed = null;
        String build = null, fingerprint = null, contextFingerprint = null, digest = null;
        Map<String, String> algorithms = null, references = null;
        AnalysisPayload payload = null; SnapshotContext frozen = null;
        ConditionalBenefitAnalysisExecution benefits = null;
        var seen = new HashSet<String>();
        var mapType = context.getTypeFactory().constructMapType(Map.class, String.class, String.class);
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME))
                context.reportInputMismatch(AnalysisSnapshot.class, "Envelope property name required.");
            String name = parser.currentName(); parser.nextToken();
            if (!seen.add(name)) context.reportInputMismatch(AnalysisSnapshot.class, "Duplicate envelope property.");
            switch (name) {
                case "schemaVersion" -> schema = context.readValue(parser, int.class);
                case "fingerprintVersion" -> fingerprintVersion = context.readValue(parser, int.class);
                case "planId" -> plan = context.readValue(parser, UUID.class);
                case "kind" -> kind = context.readValue(parser, AnalysisKind.class);
                case "shape" -> shape = context.readValue(parser, AnalysisKind.Shape.class);
                case "runId" -> run = context.readValue(parser, UUID.class);
                case "originPlanId" -> origin = context.readValue(parser, UUID.class);
                case "completedAt" -> completed = context.readValue(parser, Instant.class);
                case "applicationBuild" -> build = context.readValue(parser, String.class);
                case "algorithms" -> algorithms = context.readValue(parser, mapType);
                case "references" -> references = context.readValue(parser, mapType);
                case "inputFingerprint" -> fingerprint = context.readValue(parser, String.class);
                case "contextFingerprint" -> contextFingerprint = context.readValue(parser, String.class);
                case "payloadDigest" -> digest = context.readValue(parser, String.class);
                case "payload" -> payload = context.readValue(parser, AnalysisPayload.class);
                case "context" -> frozen = context.readValue(parser, SnapshotContext.class);
                case "benefitExecution" -> benefits = context.readValue(parser, ConditionalBenefitAnalysisExecution.class);
                default -> context.handleUnknownProperty(parser, this, AnalysisSnapshot.class, name);
            }
        }
        if (context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES) && !seen.containsAll(ORIGINAL))
            context.reportInputMismatch(AnalysisSnapshot.class, "Missing original envelope creator property.");
        return new AnalysisSnapshot(schema, fingerprintVersion, plan, kind, shape, run, completed, build, algorithms,
                references, fingerprint, contextFingerprint, digest, payload, frozen, origin, benefits);
    }
}
