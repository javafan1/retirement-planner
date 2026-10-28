package com.daviddunn.retirementplanner.app.baseline;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.*;

/** Presentation differences only. Never compares or ranks financial outcomes. */
public final class BaselineParameterComparison {
    public enum State { CHANGED, UNCHANGED, UNKNOWN, ADDED, REMOVED }
    public enum Confidence { DIRECT, EXACT_VALUES, UNIQUE_STRUCTURE, UNCERTAIN }
    public enum Units { CURRENCY, RATE, YEARS, DAYS, NUMBER, TEXT }
    public record Row(String category, String key, String label, Value baseline, Value current,
                      BigDecimal difference, Units units, State state, Confidence confidence, String explanation) { }
    private final List<Row> rows = new ArrayList<>();

    public static List<Row> compare(ProjectionInputs baseline, ProjectionInputs current) {
        var comparison = new BaselineParameterComparison();
        comparison.walk("", "", baseline == null ? Empty.NOT_RECORDED : baseline.root(),
                current == null ? Empty.NOT_RECORDED : current.root(), Confidence.DIRECT, "", null);
        return List.copyOf(comparison.rows);
    }
    private void walk(String path, String prefix, Value before, Value after, Confidence confidence, String explanation, String category) {
        if (before instanceof Fields || after instanceof Fields) {
            Map<String, Value> b = before instanceof Fields f ? f.values() : Map.of();
            Map<String, Value> c = after instanceof Fields f ? f.values() : Map.of();
            var keys = new LinkedHashSet<>(b.keySet()); keys.addAll(c.keySet());
            for (String key : keys) walk(path.isEmpty() ? key : path + "." + key, prefix,
                    b.getOrDefault(key, before instanceof Empty ? before : Empty.NOT_RECORDED),
                    c.getOrDefault(key, after instanceof Empty ? after : Empty.NOT_RECORDED), confidence, explanation, category);
            return;
        }
        if (before instanceof Items || after instanceof Items) {
            var b = before instanceof Items items ? items.values() : List.<Value>of();
            var c = after instanceof Items items ? items.values() : List.<Value>of();
            if (b.isEmpty() && c.isEmpty()) { add(path, prefix, before, after, confidence, explanation, category); return; }
            if (b.stream().allMatch(v -> v instanceof Fields) && c.stream().allMatch(v -> v instanceof Fields)) {
                collection(path, b, c, confidence, explanation, category, before instanceof Items && after instanceof Items); return;
            }
            for (int i = 0; i < Math.max(b.size(), c.size()); i++) walk(path + "[" + i + "]", prefix,
                    i < b.size() ? b.get(i) : Empty.NOT_RECORDED, i < c.size() ? c.get(i) : Empty.NOT_RECORDED,
                    confidence, explanation, category);
            return;
        }
        add(path, prefix, before, after, confidence, explanation, category);
    }
    private void collection(String path, List<Value> b, List<Value> c, Confidence parent, String explanation, String inheritedCategory, boolean bothRecorded) {
        var remaining = new LinkedHashSet<Integer>(); for (int j = 0; j < c.size(); j++) remaining.add(j);
        var matches = new LinkedHashMap<Integer, Integer>();
        var exact = new HashSet<Integer>();
        for (int i = 0; i < b.size(); i++) for (int j : List.copyOf(remaining)) {
            if (b.get(i).equals(c.get(j))) { matches.put(i, j); exact.add(i); remaining.remove(j); break; }
        }
        // Owner/type must be unique on BOTH complete sides.
        // Names never establish correspondence, including duplicate names.
        for (int i = 0; i < b.size(); i++) if (!matches.containsKey(i)) {
            String signature = signature(b.get(i));
            if (signature != null && b.stream().filter(v -> signature.equals(signature(v))).count() == 1
                    && c.stream().filter(v -> signature.equals(signature(v))).count() == 1) {
                for (int j : List.copyOf(remaining)) if (signature.equals(signature(c.get(j)))) {
                    matches.put(i, j); remaining.remove(j); break;
                }
            }
        }
        boolean unmatchedBefore = matches.size() != b.size();
        boolean knownCollections = bothRecorded && parent != Confidence.UNCERTAIN;
        for (int i = 0; i < b.size(); i++) {
            Integer j = matches.get(i);
            var item = (Fields) b.get(i);
            String itemCategory = itemCategory(path, item, inheritedCategory);
            String label = describe(item) + " (baseline " + (i + 1) + (j == null ? "" : ", current " + (j + 1)) + "): ";
            if (j != null) walk(path + "[b" + i + "/c" + j + "]", label, item, c.get(j),
                    parent == Confidence.UNCERTAIN ? parent : exact.contains(i) ? Confidence.EXACT_VALUES : Confidence.UNIQUE_STRUCTURE,
                    exact.contains(i) ? "Exact configured values; no durable entity identity is asserted." : "Unique structural correspondence, not a durable ID.", itemCategory);
            else walk(path + "[b" + i + "]", label, item, Empty.NOT_RECORDED, knownCollections && remaining.isEmpty() ? Confidence.DIRECT : Confidence.UNCERTAIN,
                    knownCollections && remaining.isEmpty() ? "Removed: no unmatched current record remains." : "Uncertain correspondence or missing context; records cannot be paired safely.", itemCategory);
        }
        for (int j : remaining) {
            var item = (Fields) c.get(j);
            walk(path + "[c" + j + "]", describe(item) + " (current " + (j + 1) + "): ", Empty.NOT_RECORDED, item,
                    knownCollections && !unmatchedBefore ? Confidence.DIRECT : Confidence.UNCERTAIN,
                    knownCollections && !unmatchedBefore ? "Added: no unmatched baseline record remains." : "Uncertain correspondence or missing context; records are shown separately.", itemCategory(path, item, inheritedCategory));
        }
    }
    private static String signature(Value value) {
        var f = ((Fields) value).values();
        if (f.containsKey("accountType") && f.containsKey("ownership")) return f.get("accountType") + "/" + f.get("ownership");
        if (f.containsKey("incomeType") && f.containsKey("ownership")) return f.get("incomeType") + "/" + f.get("ownership");
        return null;
    }
    private static String describe(Fields item) {
        var f = item.values();
        return List.of("ownership", "accountType", "incomeType", "name", "description").stream()
                .filter(f::containsKey).map(k -> display(f.get(k))).reduce((a, b) -> a + " / " + b).orElse("Record");
    }
    private static String itemCategory(String path, Fields item, String inherited) {
        String type = display(item.values().getOrDefault("incomeType", Empty.NULL));
        if (type.equals("socialSecurity")) return "Social Security assumptions and elections";
        if (type.equals("pension")) return "Pensions and guaranteed income";
        return inherited;
    }
    private void add(String path, String prefix, Value b, Value c, Confidence confidence, String explanation, String explicitCategory) {
        String lower = path.toLowerCase(Locale.ROOT);
        Units units = lower.contains("rate") || lower.contains("factor") ? Units.RATE
                : lower.contains("age") || lower.endsWith("year") || lower.endsWith("years") ? Units.YEARS
                : lower.contains("balance") || lower.contains("amount") || lower.contains("benefit") || lower.endsWith("currentvalue") ? Units.CURRENCY : Units.NUMBER;
        State state = b == Empty.NOT_RECORDED || c == Empty.NOT_RECORDED || confidence == Confidence.UNCERTAIN ? State.UNKNOWN : equal(b, c) ? State.UNCHANGED : State.CHANGED;
        if (explanation.startsWith("Added:")) state = State.ADDED;
        if (explanation.startsWith("Removed:")) state = State.REMOVED;
        if (state == State.ADDED && b == Empty.NOT_RECORDED) b = Empty.ABSENT;
        if (state == State.REMOVED && c == Empty.NOT_RECORDED) c = Empty.ABSENT;
        BigDecimal difference = null;
        if (confidence != Confidence.UNCERTAIN && b instanceof NumberValue x && c instanceof NumberValue y) difference = y.value().subtract(x.value());
        if (b instanceof TextValue x && c instanceof TextValue y) {
            try { difference = confidence == Confidence.UNCERTAIN ? null : BigDecimal.valueOf(ChronoUnit.DAYS.between(LocalDate.parse(x.value()), LocalDate.parse(y.value()))); units = Units.DAYS; }
            catch (java.time.format.DateTimeParseException notDate) { units = Units.TEXT; }
        } else if (!(b instanceof NumberValue && c instanceof NumberValue)) units = Units.TEXT;
        String leaf = path.substring(path.lastIndexOf('.') + 1);
        if (prefix.isEmpty() && path.startsWith("household.primaryPerson.")) prefix = "Primary person — ";
        if (prefix.isEmpty() && path.startsWith("household.spouse.")) prefix = "Spouse — ";
        rows.add(new Row(explicitCategory == null ? category(path) : explicitCategory, path, prefix + label(leaf), b, c, difference, units, state, confidence, explanation));
    }
    private static boolean equal(Value b, Value c) {
        return b instanceof NumberValue x && c instanceof NumberValue y ? x.value().compareTo(y.value()) == 0 : b.equals(c);
    }
    private static String category(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains("rothconversionrequest")) return "Roth conversion settings";
        if (p.contains("rmd") || p.contains("inheritedaccount") || p.contains("retirementparticipant")) return "RMD settings";
        if (p.contains("economicassumptions")) return "Economic assumptions";
        if (p.contains("taxassumptions")) return "Tax assumptions";
        if (p.contains("projection") || p.contains("deathscenario")) return "Retirement dates and projection horizon";
        if (p.contains("expenses")) return "Expenses";
        if (p.contains("accounts")) return "Financial accounts and opening balances";
        if (p.contains("noninvestable") || p.contains("withdrawal")) return "Other projection-relevant inputs";
        return "Household and persons";
    }
    public static String label(String key) {
        if (key.equals("currentBalance")) return "Opening balance";
        String text = key.replaceAll("([a-z])([A-Z])", "$1 $2");
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    public static String display(Value value) {
        if (value == Empty.NOT_RECORDED) return "Not recorded";
        if (value == Empty.NULL) return "Not configured";
        if (value == Empty.ABSENT) return "Not present";
        if (value instanceof TextValue t) return t.value();
        if (value instanceof BooleanValue b) return b.value() ? "Yes" : "No";
        if (value instanceof NumberValue n) return n.value().toPlainString();
        return "None";
    }
}
