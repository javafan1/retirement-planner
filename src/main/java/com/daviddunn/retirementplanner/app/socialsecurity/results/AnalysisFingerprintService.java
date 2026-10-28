package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Version 1 uses exact decimal value AND scale. No analysis/projection engine is invoked. */
public final class AnalysisFingerprintService {
    public static final int VERSION = 1;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    public record Inputs(BigDecimal primaryFactor, BigDecimal spouseFactor,
            LocalDate conditioning, LocalDate valuation, BigDecimal discount,
            Integer candidateCount, String prerequisiteFingerprint, List<Candidate> candidates,
            List<SnapshotContext.Section> baseline, Integer primaryDeathYear) {
        public Inputs { candidates = List.copyOf(candidates); baseline = List.copyOf(baseline); }
        public static Inputs none() { return new Inputs(null, null, null, null, null, null, null, List.of(), List.of(), null); }
    }
    public record Candidate(WeightedSnapshots.Strategy strategy, int rank,
            com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue values) {
        public Candidate {
            Objects.requireNonNull(values);
            if (!strategy.restore().hasSpouse() || rank < 1) throw new IllegalArgumentException("Invalid ranked couple candidate.");
        }
    }
    public record Assessment(String fingerprint, String contextFingerprint, String problem) {
        public boolean valid() { return problem == null; }
    }

    /** Callers with invalid pending editor text must supply its problem, never a last-valid fallback. */
    public Assessment assess(AnalysisKind kind, RetirementPlan plan, Inputs inputs, String pendingProblem) {
        if (pendingProblem != null) return new Assessment(null, null, "Inputs need attention: " + pendingProblem);
        try { return new Assessment(fingerprint(kind, plan, inputs), effectiveContextFingerprint(kind, plan), null); }
        catch (RuntimeException invalid) { return new Assessment(null, null, "Inputs need attention."); }
    }
    public String fingerprint(AnalysisKind kind, RetirementPlan plan, Inputs next) {
        if (kind.isMonteCarlo()) throw new IllegalArgumentException("Monte Carlo currency requires its own effective-input assessment.");
        Objects.requireNonNull(kind); Objects.requireNonNull(plan); Objects.requireNonNull(next);
        if ((kind.shape() == AnalysisKind.Shape.COUPLE) != plan.getHousehold().hasSpouse())
            throw new IllegalArgumentException("Household shape mismatch.");
        var groups = mapper.createObjectNode();
        groups.put("version", VERSION); groups.put("kind", kind.name());
        groups.set("S", socialSecurity(plan));
        boolean mortality = switch (kind) {
            case COUPLE_SOCIAL_SECURITY, COUPLE_WEIGHTED, INDIVIDUAL_SOCIAL_SECURITY, INDIVIDUAL_WEIGHTED -> true;
            default -> false;
        };
        if (mortality) {
            Objects.requireNonNull(next.primaryFactor()); Objects.requireNonNull(next.conditioning());
            Objects.requireNonNull(next.valuation()); Objects.requireNonNull(next.discount());
            var m = groups.putObject("M");
            m.set("primaryFactor", tree(next.primaryFactor()));
            m.set("primaryCategory", tree(plan.getHousehold().getPrimaryPerson().getMortalityCategory()));
            if (plan.getHousehold().hasSpouse()) {
                Objects.requireNonNull(next.spouseFactor());
                m.set("spouseFactor", tree(next.spouseFactor()));
                m.set("spouseCategory", tree(plan.getHousehold().getSpouse().getMortalityCategory()));
            }
            m.set("conditioning", tree(next.conditioning()));
            var v = groups.putObject("V"); v.set("date", tree(next.valuation())); v.set("discount", tree(next.discount()));
        }
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY) {
            var f = (ObjectNode) tree(plan);
            var allowed = Set.of("planId", "household", "accountPortfolio", "planningAssumptions", "rothConversionRequest", "baseline", "nonInvestableAssets", "analyzerConfigurations", "projectionState");
            f.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException("Unclassified plan field: " + name); });
            f.remove(List.of("planId", "baseline", "analyzerConfigurations", "projectionState"));
            removeDisplayNames(f);
            if (kind == AnalysisKind.COUPLE_WEIGHTED || kind == AnalysisKind.INDIVIDUAL_WEIGHTED) {
                var assumptions = (ObjectNode) f.get("planningAssumptions");
                assumptions.remove("projectionLengthYears");
                // Scenario timing overrides these elections, but recurring spending still uses the expense factor.
                ((ObjectNode) assumptions.get("deathScenarioAssumptions")).remove(List.of("deathScenario", "deathYear", "survivorClaimingAge"));
            }
            groups.set("F", f);
        }
        if (kind == AnalysisKind.COUPLE_QUICK) {
            if (next.candidateCount() == null || next.candidateCount() < 1 || next.candidateCount() > 20
                    || next.prerequisiteFingerprint() == null || !next.prerequisiteFingerprint().matches("[0-9a-f]{64}")
                    || next.candidates().isEmpty()) throw new IllegalArgumentException("Current SS prerequisite is required.");
            var q = groups.putObject("Q"); q.put("count", next.candidateCount());
            q.put("sourceFingerprint", next.prerequisiteFingerprint()); q.set("candidates", tree(next.candidates()));
        }
        if (kind == AnalysisKind.COUPLE_WEIGHTED) groups.set("B", tree(next.baseline()));
        if (kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) groups.set("deathOverride", tree(next.primaryDeathYear()));
        return canonicalDigest(groups);
    }
    private JsonNode socialSecurity(RetirementPlan plan) {
        var s = mapper.createObjectNode();
        s.set("cola", tree(plan.getPlanningAssumptions().getSocialSecurityColaRate()));
        var people = s.putArray("people");
        var persons = new ArrayList<Person>(); persons.add(plan.getHousehold().getPrimaryPerson());
        plan.getHousehold().spouse().ifPresent(persons::add);
        for (var p : persons) {
            var person = people.addObject(); person.set("dob", tree(p.getBirthDate()));
            var records = person.putArray("records");
            for (var source : p.getIncomeSources()) if (source instanceof SocialSecurityIncome ss) {
                var r = records.addObject(); r.set("owner", tree(ss.getOwnership()));
                r.set("fraMonthly", tree(ss.getFullRetirementMonthlyBenefit()));
                r.put("claimingAge", ss.getClaimingAge()); r.set("claimDate", tree(ss.getStartDate()));
                r.put("valuationYear", ss.getBenefitValuationYear());
            }
        }
        return s;
    }
    private static void removeDisplayNames(JsonNode node) {
        if (node.isObject()) ((ObjectNode) node).remove(List.of("firstName", "lastName", "name", "description"));
        node.forEach(AnalysisFingerprintService::removeDisplayNames);
    }
    private JsonNode tree(Object value) {
        try { return mapper.readTree(mapper.writeValueAsBytes(value)); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot capture effective inputs.", e); }
    }
    public String canonicalDigest(JsonNode node) {
        StringBuilder b = new StringBuilder(); canonical(node, b);
        return AnalysisSnapshotCodec.sha256(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Reference-only assumptions still shown by completed results, separate from financial inputs. */
    public String effectiveContextFingerprint(AnalysisKind kind, RetirementPlan plan) {
        var context = mapper.createObjectNode();
        if (kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
            context.set("displayedGeneralInflation", tree(plan.getPlanningAssumptions().getGeneralInflationRate()));
        if (kind == AnalysisKind.COUPLE_WEIGHTED || kind == AnalysisKind.INDIVIDUAL_WEIGHTED) {
            context.put("configuredLength", plan.getPlanningAssumptions().getProjectionLengthYears());
            context.set("configuredTiming", tree(plan.getPlanningAssumptions().getDeathScenarioAssumptions()));
        }
        return canonicalDigest(context);
    }
    private static void canonical(JsonNode node, StringBuilder b) {
        if (node.isObject()) {
            b.append('{'); var names = new ArrayList<String>(); node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) { b.append(name.length()).append(':').append(name); canonical(node.get(name), b); }
            b.append('}');
        } else if (node.isArray()) {
            b.append('['); node.forEach(n -> canonical(n, b)); b.append(']');
        } else if (node.isFloatingPointNumber()) {
            var d = node.decimalValue(); b.append('d').append(d.unscaledValue()).append(':').append(d.scale()).append(';');
        } else { String text = node.toString(); b.append(text.length()).append(':').append(text); }
    }
}
