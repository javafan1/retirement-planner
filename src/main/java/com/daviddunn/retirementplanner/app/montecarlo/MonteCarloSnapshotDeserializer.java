package com.daviddunn.retirementplanner.app.montecarlo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot.*;

/** Streaming compatibility for optional capability fields; original strict creator requirements remain. */
public final class MonteCarloSnapshotDeserializer extends JsonDeserializer<MonteCarloSnapshot> {
    private static final Set<String> ORIGINAL = Set.of("version", "mode", "hasSpouse", "settings", "request",
            "provenance", "fixed", "mortality", "paired", "display", "startedAt", "completedAt");

    @Override
    public MonteCarloSnapshot deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) return (MonteCarloSnapshot) context.handleUnexpectedToken(MonteCarloSnapshot.class, parser);
        int version = 0; boolean spouse = false;
        Mode mode = null; Settings settings = null; Request request = null; Provenance provenance = null;
        Fixed fixed = null; Mortality mortality = null; Paired paired = null; Display display = null;
        Instant started = null; Instant completed = null; ExpenseExecution expenseExecution = null;
        PairedExpenseExecution pairedExpenseExecution = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME))
                context.reportInputMismatch(MonteCarloSnapshot.class, "Snapshot property name required.");
            String name = parser.currentName(); parser.nextToken();
            if (!seen.add(name)) context.reportInputMismatch(MonteCarloSnapshot.class, "Duplicate snapshot property.");
            switch (name) {
                case "version" -> version = context.readValue(parser, int.class);
                case "hasSpouse" -> spouse = context.readValue(parser, boolean.class);
                case "mode" -> mode = context.readValue(parser, Mode.class);
                case "settings" -> settings = context.readValue(parser, Settings.class);
                case "request" -> request = context.readValue(parser, Request.class);
                case "provenance" -> provenance = context.readValue(parser, Provenance.class);
                case "fixed" -> fixed = context.readValue(parser, Fixed.class);
                case "mortality" -> mortality = context.readValue(parser, Mortality.class);
                case "paired" -> paired = context.readValue(parser, Paired.class);
                case "display" -> display = context.readValue(parser, Display.class);
                case "startedAt" -> started = context.readValue(parser, Instant.class);
                case "completedAt" -> completed = context.readValue(parser, Instant.class);
                case "expenseExecution" -> expenseExecution = context.readValue(parser, ExpenseExecution.class);
                case "pairedExpenseExecution" -> pairedExpenseExecution = context.readValue(parser, PairedExpenseExecution.class);
                default -> context.handleUnknownProperty(parser, this, MonteCarloSnapshot.class, name);
            }
        }
        if (context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES) && !seen.containsAll(ORIGINAL))
            context.reportInputMismatch(MonteCarloSnapshot.class, "Missing original snapshot creator property.");
        return new MonteCarloSnapshot(version, mode, spouse, settings, request, provenance, fixed, mortality, paired,
                display, started, completed, expenseExecution, pairedExpenseExecution);
    }
}
