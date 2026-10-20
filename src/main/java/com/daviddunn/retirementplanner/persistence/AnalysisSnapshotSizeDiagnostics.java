package com.daviddunn.retirementplanner.persistence;

import com.fasterxml.jackson.core.*;
import java.io.IOException;
import java.security.*;
import java.util.*;

/** Bounded, scalar-only attribution of already serialized Exhaustive bytes. Never logs content or paths to files. */
public final class AnalysisSnapshotSizeDiagnostics {
    private AnalysisSnapshotSizeDiagnostics() { }
    private static final int MAX_SCAN_BYTES = 128 * 1024 * 1024;
    private static final int MAX_SCAN_TOKENS = 8_000_000;
    private static final int MAX_REUSE_KEYS = 100_000;
    private static final Set<String> ANNUAL_FIELDS = Arrays.stream(
            com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot.Annual.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toUnmodifiableSet());
    public record Summary(long rawBytes, boolean complete, int candidates, int retained, int projections,
            int annualRows, int maximumYears, int maximumAccounts, int maximumNumberLength,
            long tokens, Map<String, Long> bytes, Map<String, Long> duplicateBytes) {
        public Summary { bytes = Collections.unmodifiableMap(new TreeMap<>(bytes)); duplicateBytes = Collections.unmodifiableMap(new TreeMap<>(duplicateBytes)); }
    }
    private record Frame(String path, long start) { }

    public static Summary measure(byte[] raw) throws IOException {
        if (raw.length > MAX_SCAN_BYTES) return new Summary(raw.length, false, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of());
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(AnalysisSnapshotCodec.MAX_DEPTH).maxNumberLength(AnalysisSnapshotCodec.MAX_NUMBER_LENGTH)
                .maxStringLength(AnalysisSnapshotCodec.MAX_STRING_LENGTH).build()).build();
        var totals = new TreeMap<String, Long>(); var duplicates = new TreeMap<String, Long>();
        var seen = new HashSet<String>(); var stack = new ArrayDeque<Frame>();
        int candidates = 0, retained = 0, projections = 0, rows = 0, years = 0, accounts = 0, numberLength = 0;
        long tokens = 0; boolean complete = true;
        try (var p = factory.createParser(raw)) {
            for (JsonToken t; (t = p.nextToken()) != null;) {
                if (++tokens > MAX_SCAN_TOKENS) { complete = false; break; }
                String path;
                long start;
                if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY) {
                    path = path(p.getParsingContext().getParent());
                    start = p.currentTokenLocation().getByteOffset();
                    stack.push(new Frame(path, start));
                    if (t == JsonToken.START_OBJECT && path.equals("/payload/entries/*")) candidates++;
                    if (t == JsonToken.START_OBJECT && path.equals("/payload/entries/*/detail")) retained++;
                    if (t == JsonToken.START_OBJECT && projection(path)) projections++;
                    if (t == JsonToken.START_OBJECT && path.endsWith("/years/*")) rows++;
                    continue;
                }
                if (t == JsonToken.FIELD_NAME) continue;
                if (t.isNumeric()) numberLength = Math.max(numberLength, p.getTextLength());
                if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
                    var f = stack.pop(); path = f.path(); start = f.start();
                } else { path = path(p.getParsingContext()); start = p.currentTokenLocation().getByteOffset(); }
                p.finishToken();
                long length = p.currentLocation().getByteOffset() - start;
                String category = category(path);
                if (category != null && category.equals("retainedDetails") && t == JsonToken.VALUE_NULL) category = null;
                if (category != null) totals.merge(category, length, Long::sum);
                if (path.endsWith("/accounts") && t == JsonToken.END_ARRAY) {
                    // Counting account definitions is independent of account balances or names.
                    int count = 0;
                    try (var definitions = factory.createParser(raw, (int) start, (int) length)) {
                        definitions.nextToken(); while (definitions.nextToken() != JsonToken.END_ARRAY) { definitions.skipChildren(); count++; }
                    }
                    accounts = Math.max(accounts, count);
                }
                if (path.endsWith("/years") && t == JsonToken.END_ARRAY) {
                    int count = 0;
                    try (var annuals = factory.createParser(raw, (int) start, (int) length)) {
                        annuals.nextToken(); while (annuals.nextToken() != JsonToken.END_ARRAY) { annuals.skipChildren(); count++; }
                    }
                    years = Math.max(years, count);
                }
                if (Set.of("/payload/entries/*/metrics", "/payload/entries/*/differences", "/payload/entries/*/strategy").contains(path)
                        || path.endsWith("/accounts") || path.endsWith("/years")) {
                    if (seen.size() >= MAX_REUSE_KEYS) { complete = false; continue; }
                    String key = path.replace("/payload/baseline", "/projection").replace("/payload/entries/*/detail", "/projection")
                            + ":" + digest(raw, (int) start, (int) length);
                    if (!seen.add(key)) duplicates.merge(category == null ? "other" : category, length, Long::sum);
                }
            }
        }
        if (complete) {
            totals.put("compactEntriesIncludingSyntax", totals.getOrDefault("entries", 0L) - totals.getOrDefault("retainedDetails", 0L));
            totals.put("envelopeAndOtherMetadata", (long) raw.length - totals.getOrDefault("entries", 0L) - totals.getOrDefault("baseline", 0L));
        }
        return new Summary(raw.length, complete, candidates, retained, projections, rows, years, accounts, numberLength, tokens, totals, duplicates);
    }

    private static boolean projection(String p) { return p.equals("/payload/baseline") || p.equals("/payload/entries/*/detail"); }
    private static String category(String p) {
        if (p.startsWith("/payload/tables/")) {
            if (Set.of("/payload/tables/metrics", "/payload/tables/accounts", "/payload/tables/years").contains(p))
                return "internTable." + p.substring(p.lastIndexOf('/') + 1);
            return null;
        }
        return switch (p) {
            case "/payload/entries" -> "entries";
            case "/payload/baseline" -> "baseline";
            case "/payload/entries/*/detail" -> "retainedDetails";
            case "/payload/entries/*/metrics" -> "candidateMetrics";
            case "/payload/entries/*/differences" -> "candidateDifferences";
            case "/payload/entries/*/strategy" -> "candidateStrategies";
            case "/payload/report", "/context" -> "frozenReportAndContext";
            case "/payload/socialSecurityReference" -> "socialSecurityCrossReference";
            default -> {
                if (p.endsWith("/accounts")) yield "accountDefinitions";
                if (p.endsWith("/years")) yield "projectionAnnualGraphs";
                if (p.endsWith("/years/*/balances")) yield "annualAccountSnapshots";
                if (p.contains("/years/*/") && p.substring(p.indexOf("/years/*/") + 9).indexOf('/') < 0)
                    yield "annualField." + (ANNUAL_FIELDS.contains(p.substring(p.indexOf("/years/*/") + 9))
                            ? p.substring(p.indexOf("/years/*/") + 9) : "<unrecognized>");
                yield null;
            }
        };
    }
    private static String path(JsonStreamContext context) {
        var parts = new LinkedList<String>();
        for (var c = context; c != null; c = c.getParent()) {
            if (c.inArray()) parts.addFirst("*");
            else if (c.inObject() && c.getCurrentName() != null) parts.addFirst(c.getCurrentName());
        }
        return ("/" + String.join("/", parts)).replace("/payload/document", "/payload");
    }
    private static String digest(byte[] raw, int start, int length) {
        try { var d = MessageDigest.getInstance("SHA-256"); d.update(raw, start, length); return HexFormat.of().formatHex(d.digest()); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
