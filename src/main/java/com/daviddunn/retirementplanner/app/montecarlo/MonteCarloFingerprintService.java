package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

/** Effective frozen inputs only. No simulation, mortality distribution or projection is evaluated. */
public final class MonteCarloFingerprintService {
    /** Legacy V1, read-only numeric V2, enabled standalone V3 and enabled ordered comparison V4. */
    public enum Contract { EXACT_V1, TEMPORARY_NUMERIC_V2, EXPENSE_MODEL_V3, PAIRED_EXPENSE_V4 }
    public record Inputs(AnalysisKind kind, MonteCarloSnapshot.Settings settings, MonteCarloSnapshot.Request request,
            String suppliedWorldFingerprint) {
        public Inputs(AnalysisKind kind, MonteCarloSnapshot.Settings settings, MonteCarloSnapshot.Request request) {
            this(kind, settings, request, null);
        }
    }
    private final ObjectMapper json = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                    .maxNumberLength(4096).maxNestingDepth(80).maxStringLength(256000).build()).build())
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    public static Inputs standalone(RetirementPlan plan, MonteCarloSettings settings, MonteCarloMortalityRequest mortality) {
        boolean sampled = mortality != null;
        int first = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        var request = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(plan), null, "Analyzed Plan", null,
                plan.getPlanningAssumptions().getProjectionStartDate(), sampled ? null
                : first + plan.getPlanningAssumptions().getProjectionLengthYears() - 1, null,
                sampled ? MonteCarloSnapshot.MortalityInputs.capture(mortality) : null);
        var kind = AnalysisKind.valueOf((plan.getHousehold().hasSpouse() ? "COUPLE" : "INDIVIDUAL")
                + "_MONTE_CARLO_" + (sampled ? "LONGEVITY" : "FIXED"));
        return new Inputs(kind, MonteCarloSnapshot.Settings.capture(settings), request);
    }

    public static Inputs comparison(MonteCarloStrategyComparisonRequest r) {
        var fixed = r.assumptions() instanceof MonteCarloStrategyComparisonRequest.Fixed f ? f : null;
        var mortality = r.assumptions() instanceof MonteCarloStrategyComparisonRequest.Longevity m ? m.mortality() : null;
        var request = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(r.strategyA().copyForRun()),
                MonteCarloPlanDocument.capture(r.strategyB().copyForRun()), r.strategyA().label(), r.strategyB().label(),
                r.assumptions().start(), fixed == null ? null : fixed.endingYear(), null,
                mortality == null ? null : MonteCarloSnapshot.MortalityInputs.capture(mortality));
        return new Inputs(AnalysisKind.valueOf((r.assumptions().hasSpouse() ? "COUPLE" : "INDIVIDUAL")
                + "_MONTE_CARLO_PAIRED_" + (mortality == null ? "FIXED" : "LONGEVITY")),
                MonteCarloSnapshot.Settings.capture(r.assumptions().settings()), request);
    }

    public AnalysisFingerprintService.Assessment assess(Inputs inputs) {
        try {
            var contract = acceptanceContract(inputs);
            return assess(inputs, contract == Contract.EXACT_V1 ? Contract.TEMPORARY_NUMERIC_V2 : contract);
        } catch (RuntimeException invalid) {
            return new AnalysisFingerprintService.Assessment(null, null, "Inputs need attention.");
        }
    }

    public Contract acceptanceContract(Inputs inputs) {
        if (inputs == null || inputs.request() == null) return Contract.EXACT_V1;
        boolean positiveA = ExpenseExecution.configured(MonteCarloPlanDocument.read(inputs.request().planA()));
        if (inputs.request().planB() != null)
            return positiveA || ExpenseExecution.configured(MonteCarloPlanDocument.read(inputs.request().planB()))
                    ? Contract.PAIRED_EXPENSE_V4 : Contract.EXACT_V1;
        return positiveA ? Contract.EXPENSE_MODEL_V3 : Contract.EXACT_V1;
    }

    public AnalysisFingerprintService.Assessment forAcceptance(Inputs inputs) {
        try { return assess(inputs, acceptanceContract(inputs)); }
        catch (RuntimeException invalid) { return new AnalysisFingerprintService.Assessment(null, null, "Inputs need attention."); }
    }

    public AnalysisFingerprintService.Assessment assess(Inputs inputs, Contract contract) {
        try {
            Objects.requireNonNull(inputs); Objects.requireNonNull(inputs.kind());
            Objects.requireNonNull(inputs.settings()); Objects.requireNonNull(inputs.request()); Objects.requireNonNull(contract);
            if (!inputs.kind().isMonteCarlo()) throw new IllegalArgumentException("Monte Carlo kind required.");
            var node = json.createObjectNode();
            var context = json.createObjectNode();
            node.put("version", contract == Contract.EXACT_V1 ? 1 : contract == Contract.TEMPORARY_NUMERIC_V2 ? 2
                    : contract == Contract.EXPENSE_MODEL_V3 ? 3 : 4);
            node.put("kind", inputs.kind().name());
            node.set("settings", json.valueToTree(inputs.settings()));
            boolean sampled = inputs.request().mortality() != null;
            boolean paired = inputs.request().planB() != null;
            node.set("A", plan(inputs.request().planA(), sampled, false, paired, context.putObject("A")));
            if (paired) node.set("B", plan(inputs.request().planB(), sampled, true, true, context.putObject("B")));
            node.set("mortality", json.valueToTree(inputs.request().mortality()));
            node.set("end", json.valueToTree(inputs.request().endingYear()));
            node.set("suppliedWorldFingerprint", json.valueToTree(inputs.suppliedWorldFingerprint()));
            if (contract == Contract.EXPENSE_MODEL_V3) {
                if (paired || inputs.suppliedWorldFingerprint() != null)
                    throw new IllegalArgumentException("Enabled expense fingerprint requires generated standalone inputs.");
                var capability = (ObjectNode) json.valueToTree(ExpenseExecution.fromDocument(inputs.request().planA()));
                // Exact request integrity is checked separately. Display names, baseline and plan IDs
                // in the original document must not become new effective financial dependencies.
                capability.remove("planDigest");
                node.set("expenseExecution", capability);
            }
            if (contract == Contract.PAIRED_EXPENSE_V4) {
                if (!paired || inputs.suppliedWorldFingerprint() != null)
                    throw new IllegalArgumentException("Paired expense fingerprint requires generated comparison inputs.");
                var capability = (ObjectNode) json.valueToTree(PairedExpenseExecution.capture(
                        inputs.request().planA(), inputs.request().planB()));
                for (String side : List.of("current", "baseline"))
                    if (capability.get(side) instanceof ObjectNode enabled) enabled.remove("planDigest");
                node.set("pairedExpenseExecution", capability);
                var worlds = node.putObject("worlds");
                worlds.put("source", "GENERATED");
                worlds.put("returns", MonteCarloScenarioGenerator.MODEL_VERSION);
                worlds.put("inflation", MonteCarloInflationGenerator.MODEL_VERSION);
                worlds.put("streams", "RPMC-SHA256-v1/legacy-market-v1");
                if (sampled) worlds.put("mortality", inputs.kind().shape() == AnalysisKind.Shape.COUPLE
                        ? "HOUSEHOLD_MORTALITY_V1" : "PRIMARY_MORTALITY_V1");
            }
            if (contract != Contract.EXACT_V1) {
                for (String path : List.of("/settings/expectedReturn", "/settings/volatility",
                        "/settings/inflation/expectedInflationRate", "/settings/inflation/inflationVolatility",
                        "/settings/inflation/minimumInflationRate", "/mortality/primary/mortalityAdjustment/factor",
                        "/mortality/coupleInputs/primaryAdjustment/factor", "/mortality/coupleInputs/spouseAdjustment/factor")) {
                    var value = node.at(path);
                    if (value.isMissingNode() || value.isNull()) continue;
                    if (!value.isNumber()) throw new IllegalArgumentException("Invalid numeric analyzer input.");
                    int split = path.lastIndexOf('/');
                    ((ObjectNode) node.at(path.substring(0, split))).set(path.substring(split + 1),
                            DecimalNode.valueOf(value.decimalValue().stripTrailingZeros()));
                }
            }
            var canonical = new AnalysisFingerprintService();
            return new AnalysisFingerprintService.Assessment(canonical.canonicalDigest(node),
                    canonical.canonicalDigest(context), null,
                    AnalysisVersions.usesOrdinaryPensionSelection(inputs.kind())
                            && (com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.potentiallyAffected(
                                    MonteCarloPlanDocument.read(inputs.request().planA()),
                                    inputs.request().start().getYear(), inputs.request().endingYear())
                            || inputs.request().planB() != null
                            && com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.potentiallyAffected(
                                    MonteCarloPlanDocument.read(inputs.request().planB()),
                                    inputs.request().start().getYear(), inputs.request().endingYear())));
        } catch (Exception invalid) {
            return new AnalysisFingerprintService.Assessment(null, null, "Inputs need attention.");
        }
    }

    public AnalysisFingerprintService.Assessment assess(MonteCarloSnapshot snapshot) {
        return assess(new Inputs(snapshot.kind(), snapshot.settings(), snapshot.request(), snapshot.provenance().suppliedWorldFingerprint()),
                snapshot.pairedExpenseExecution() != null ? Contract.PAIRED_EXPENSE_V4
                        : snapshot.expenseExecution() == null ? Contract.TEMPORARY_NUMERIC_V2 : Contract.EXPENSE_MODEL_V3);
    }

    public AnalysisFingerprintService.Assessment storedV1(MonteCarloSnapshot snapshot) {
        return assess(new Inputs(snapshot.kind(), snapshot.settings(), snapshot.request(), snapshot.provenance().suppliedWorldFingerprint()), Contract.EXACT_V1);
    }

    public AnalysisFingerprintService.Assessment storedFingerprint(MonteCarloSnapshot snapshot) {
        return !snapshot.hasExpenseExecution() ? storedV1(snapshot)
                : assess(new Inputs(snapshot.kind(), snapshot.settings(), snapshot.request(), snapshot.provenance().suppliedWorldFingerprint()),
                        snapshot.pairedExpenseExecution() != null ? Contract.PAIRED_EXPENSE_V4 : Contract.EXPENSE_MODEL_V3);
    }

    /** Verify the stored contract, then assess legacy numeric V2, standalone V3 or paired V4. Never migrate disk data. */
    public AnalysisCompatibilityPolicy.Assessment compatibility(AnalysisSnapshot saved, Inputs current) {
        var policy = new AnalysisCompatibilityPolicy();
        if (current == null) return new AnalysisCompatibilityPolicy.Assessment(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION, "Correct the pending analyzer inputs.");
        if (!(saved.payload() instanceof MonteCarloSnapshot frozen) || saved.fingerprintVersion() != frozen.fingerprintVersion()
                || saved.kind() != frozen.kind() || current.kind() != saved.kind())
            return policy.unavailable("Unsupported Monte Carlo fingerprint contract or analysis kind.");
        try { frozen.requireInterpretable(); }
        catch (IllegalArgumentException unsupported) { return policy.unavailable("Historical expense execution provenance is unavailable; original result preserved."); }
        var original = storedFingerprint(frozen);
        if (!original.valid() || !saved.inputFingerprint().equals(original.fingerprint())
                || !saved.contextFingerprint().equals(original.contextFingerprint()))
            return policy.unavailable("The original Monte Carlo input fingerprint cannot be verified.");
        var provenance = policy.assess(saved, original, AnalysisVersions.algorithms(saved.kind(), frozen), AnalysisVersions.references(saved.kind()));
        if (provenance.state() != AnalysisCompatibilityPolicy.State.CURRENT) return provenance;
        var next = assess(current);
        if (!next.valid()) return new AnalysisCompatibilityPolicy.Assessment(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION, "Correct the pending analyzer inputs.");
        var historical = assess(frozen);
        return new AnalysisCompatibilityPolicy.Assessment(
                historical.fingerprint().equals(next.fingerprint()) && historical.contextFingerprint().equals(next.contextFingerprint())
                        ? AnalysisCompatibilityPolicy.State.CURRENT : AnalysisCompatibilityPolicy.State.OUTDATED,
                historical.fingerprint().equals(next.fingerprint()) && historical.contextFingerprint().equals(next.contextFingerprint())
                        ? !frozen.hasExpenseExecution() ? "Saved results match current effective inputs (temporary numeric compatibility v2)."
                                : "Saved results match current effective inputs (stochastic expense contract v" + frozen.fingerprintVersion() + ")."
                        : "These results were generated using previous plan or analysis assumptions and may not reflect the current retirement plan.");
    }

    private JsonNode plan(String document, boolean sampled, boolean alternative, boolean paired, ObjectNode context) throws java.io.IOException {
        var node = (ObjectNode) json.readTree(document);
        var allowed = Set.of("planId", "household", "accountPortfolio", "planningAssumptions",
                "rothConversionRequest", "baseline", "nonInvestableAssets", "analyzerConfigurations", "projectionState");
        node.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw new IllegalArgumentException("Unclassified plan field."); });
        node.remove(List.of("planId", "baseline", "analyzerConfigurations", "projectionState"));
        removeNames(node);
        var assumptions = (ObjectNode) node.get("planningAssumptions");
        if (sampled || alternative) move(assumptions, "projectionLengthYears", context);
        if (sampled) {
            var death = (ObjectNode) assumptions.get("deathScenarioAssumptions");
            var frozenDeath = context.putObject("configuredDeath");
            move(death, "deathScenario", frozenDeath);
            move(death, "deathYear", frozenDeath);
            if (!paired) move(death, "survivorClaimingAge", frozenDeath);
            // The post-death expense factor remains an effective financial input.
        } else removeCategories(node);
        return node;
    }
    private static void move(ObjectNode source, String key, ObjectNode context) {
        var value = source.remove(key);
        if (value != null) context.set(key, value);
    }
    private static void removeCategories(JsonNode node) {
        if (node.isObject()) ((ObjectNode) node).remove("mortalityCategory");
        node.forEach(MonteCarloFingerprintService::removeCategories);
    }
    private static void removeNames(JsonNode node) {
        if (node.isObject()) ((ObjectNode) node).remove(List.of("firstName", "lastName", "name", "description"));
        node.forEach(MonteCarloFingerprintService::removeNames);
    }
}
