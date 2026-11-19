package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.io.*;
import java.util.*;

public final class AnalysisVersions {
    private AnalysisVersions() { }
    /** Adds provenance only for newly calculated NC full-plan results. Never rewrites saved references. */
    public static Map<String, String> withNorthCarolinaRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.NC)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.northcarolina.NorthCarolinaTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.northcarolina.NorthCarolinaTaxRules.VERSION);
        return Map.copyOf(result);
    }
    /** Kentucky-only provenance for new full-plan results; existing state references remain unchanged. */
    public static Map<String, String> withKentuckyRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.KY)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.kentucky.KentuckyTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.kentucky.KentuckyTaxRules.VERSION);
        return Map.copyOf(result);
    }
    public static Map<String, String> withGeorgiaRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.GA)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.georgia.GeorgiaTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.georgia.GeorgiaTaxRules.VERSION);
        return Map.copyOf(result);
    }
    public static Map<String, String> withOklahomaRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.OK)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.oklahoma.OklahomaTaxRules.VERSION);
        return Map.copyOf(result);
    }
    public static Map<String, String> withAlabamaRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AL)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.alabama.AlabamaTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.alabama.AlabamaTaxRules.VERSION);
        return Map.copyOf(result);
    }
    public static Map<String, String> withMississippiRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MS)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.mississippi.MississippiTaxRules.VERSION);
        return Map.copyOf(result);
    }
    public static Map<String, String> withVirginiaRules(AnalysisKind kind, Map<String, String> references,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan... frozenPlans) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                || Arrays.stream(frozenPlans).filter(Objects::nonNull).noneMatch(plan ->
                        plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.VA)) {
            return references;
        }
        var result = new TreeMap<>(references.isEmpty() ? references(kind) : references);
        result.put(com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaTaxRules.REFERENCE_KEY,
                com.daviddunn.retirementplanner.domain.tax.state.virginia.VirginiaTaxRules.VERSION);
        return Map.copyOf(result);
    }

    public static boolean usesOrdinaryPensionSelection(AnalysisKind kind) {
        return kind == AnalysisKind.COUPLE_QUICK || kind == AnalysisKind.COUPLE_EXHAUSTIVE
                || kind.isMonteCarlo() && kind.name().endsWith("FIXED");
    }
    /** Only fresh calculation completions use this map; never apply it to a restored artifact. */
    public static Map<String, String> calculatedAlgorithms(AnalysisKind kind, AnalysisPayload payload) {
        var result = new TreeMap<>(algorithms(kind, payload));
        if (usesOrdinaryPensionSelection(kind)) result.put(
                com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY,
                com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.CORRECTED);
        return Map.copyOf(result);
    }
    public static boolean supportedMonteCarloAlgorithms(AnalysisKind kind, AnalysisPayload payload, Map<String, String> versions) {
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc && mc.benefitExecution() != null)
            return versions.equals(calculatedAlgorithms(kind, payload));
        return versions.equals(algorithms(kind, payload)) || versions.equals(calculatedAlgorithms(kind, payload));
    }
    /** Payload-specific capabilities leave every legacy kind's version map unchanged. */
    public static Map<String, String> algorithms(AnalysisKind kind, AnalysisPayload payload) {
        var result = new TreeMap<>(algorithms(kind));
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.expenseExecution() != null)
            result.put("recurring-expenses", com.daviddunn.retirementplanner.app.montecarlo.ExpenseExecution.ALGORITHM);
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.pairedExpenseExecution() != null)
            result.put("paired-recurring-expenses", com.daviddunn.retirementplanner.app.montecarlo.PairedExpenseExecution.ALGORITHM);
        if (payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc && mc.benefitExecution() != null)
            result.put(ConditionalBenefitAnalysisExecution.KEY, mc.benefitExecution().algorithm());
        return Map.copyOf(result);
    }
    public static Map<String, String> algorithms(AnalysisKind kind) {
        var versions = new TreeMap<String, String>();
        versions.put("monthly-social-security", "v1");
        versions.put("analysis-kind", kind.name() + "-v1");
        if (kind.isMonteCarlo()) {
            versions.put("monte-carlo", "v1");
            versions.put("random-streams", "v1");
            versions.put("statistics", "v1");
        }
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
            versions.put("projection", com.daviddunn.retirementplanner.domain.projection.ProjectionEngine.ALGORITHM_VERSION);
        return Map.copyOf(versions);
    }
    /** Classpath reads belong on the I/O worker, not JavaFX. No remote reference data. */
    public static Map<String, String> references(AnalysisKind kind) {
        List<String> resources = new ArrayList<>();
        if (kind.isMonteCarlo() ? kind.name().endsWith("LONGEVITY")
                : kind != AnalysisKind.COUPLE_EXHAUSTIVE && kind != AnalysisKind.INDIVIDUAL_DETERMINISTIC)
            resources.add("mortality/ssa-period-life-table-2022.csv");
        if (kind != AnalysisKind.COUPLE_SOCIAL_SECURITY && kind != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY) {
            resources.add("rules/government-rules-2026.json");
            resources.add("tax/state/state-tax-2026.json");
            resources.add("tax/state/iowa-tax-2026.json");
        }
        Map<String, String> result = new TreeMap<>();
        for (String name : resources) {
            try (var stream = AnalysisVersions.class.getClassLoader().getResourceAsStream(name)) {
                if (stream == null) throw new IllegalStateException("Missing shipped analysis reference: " + name);
                result.put(name, AnalysisSnapshotCodec.sha256(stream.readAllBytes()));
            } catch (IOException failure) { throw new IllegalStateException("Cannot fingerprint shipped analysis references.", failure); }
        }
        return Map.copyOf(result);
    }
}
