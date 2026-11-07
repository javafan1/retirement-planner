package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPlanDocument;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded original-input evidence. V3 composes conditional receipts with insured-contingent expenses.
 * No scenario paths, random draws or live domain references. V1/V2 retain their benefit-only meaning. */
public record ConditionalBenefitAnalysisExecution(int version, String model, AnalysisKind family,
        String scenarioProtocol, List<Candidate> candidates) {
    public static final int VERSION = 1;
    public static final String MODEL = "ANALYZER_ANNUAL_CONDITIONAL_BENEFITS_V1";
    public static final String KEY = "conditional-benefits";
    public static final String ALGORITHM = "analyzer-annual-v1";
    public static final int LIFE_PRINCIPAL_VERSION = 2;
    public static final String LIFE_PRINCIPAL_MODEL = "ANALYZER_CONDITIONAL_LIFE_PRINCIPAL_V2";
    public static final String LIFE_PRINCIPAL_ALGORITHM = "analyzer-life-principal-v2";
    public static final int PREMIUM_VERSION = 3;
    public static final String PREMIUM_MODEL = "ANALYZER_CONTRACTUAL_PREMIUMS_V3";
    public static final String PREMIUM_ALGORITHM = "analyzer-contractual-premiums-v3";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    public enum Role { ANALYZED_PLAN, CURRENT, BASELINE }

    public record Candidate(Role role, String originalInputs, String inputDigest, String definitionDigest,
            boolean enabled) {
        public Candidate {
            Objects.requireNonNull(role);
            var plan = MonteCarloPlanDocument.read(originalInputs);
            if (!digest(originalInputs).equals(inputDigest)
                    || !definitionsDigest(plan).equals(definitionDigest)
                    || enabled != com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(plan))
                throw new IllegalArgumentException("Conditional-benefit candidate input binding is invalid.");
            plan.validateHouseholdReferences();
        }

        static Candidate capture(Role role, String inputs) {
            var plan = MonteCarloPlanDocument.read(inputs);
            return new Candidate(role, inputs, digest(inputs), definitionsDigest(plan),
                    com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(plan));
        }
        @Override public String toString() {
            return "Candidate[role=" + role + ", enabled=" + enabled + ", inputDigest=" + inputDigest
                    + ", definitionDigest=" + definitionDigest + "]";
        }
    }

    public ConditionalBenefitAnalysisExecution {
        Objects.requireNonNull(family);
        if (!(version == VERSION && MODEL.equals(model)
                || version == LIFE_PRINCIPAL_VERSION && LIFE_PRINCIPAL_MODEL.equals(model)
                || version == PREMIUM_VERSION && PREMIUM_MODEL.equals(model))
                || !protocol(family).equals(scenarioProtocol))
            throw new IllegalArgumentException("Unsupported conditional-benefit analyzer capability.");
        candidates = List.copyOf(candidates);
        boolean paired = family.name().contains("PAIRED");
        if (candidates.size() != (paired ? 2 : 1)
                || candidates.getFirst().role() != (paired ? Role.CURRENT : Role.ANALYZED_PLAN)
                || paired && candidates.get(1).role() != Role.BASELINE
                || candidates.stream().noneMatch(Candidate::enabled))
            throw new IllegalArgumentException("Conditional-benefit candidate roles or enabled state are invalid.");
        // Enforce the one/two-candidate bound before decoding their original documents again.
        boolean principal = candidates.stream().anyMatch(c -> ConditionalBenefits.hasExcludedPrincipal(
                MonteCarloPlanDocument.read(c.originalInputs()).getConditionalBenefits()));
        boolean premiums = candidates.stream().anyMatch(c -> com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(
                MonteCarloPlanDocument.read(c.originalInputs())));
        if (premiums != (version == PREMIUM_VERSION) || !premiums && principal != (version == LIFE_PRINCIPAL_VERSION))
            throw new IllegalArgumentException("Conditional tax character does not match its execution capability.");
        for (var candidate : candidates) {
            var plan = MonteCarloPlanDocument.read(candidate.originalInputs());
            if (plan.getHousehold().hasSpouse() != (family.shape() == AnalysisKind.Shape.COUPLE))
                throw new IllegalArgumentException("Conditional-benefit candidate household does not match its family.");
        }
    }

    public static ConditionalBenefitAnalysisExecution capture(AnalysisKind kind, RetirementPlan current) {
        return capture(kind, MonteCarloPlanDocument.capture(current), null);
    }

    public static ConditionalBenefitAnalysisExecution capture(AnalysisKind kind, String current, String baseline) {
        boolean principal = ConditionalBenefits.hasExcludedPrincipal(MonteCarloPlanDocument.read(current).getConditionalBenefits())
                || baseline != null && ConditionalBenefits.hasExcludedPrincipal(MonteCarloPlanDocument.read(baseline).getConditionalBenefits());
        boolean premiums = com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(MonteCarloPlanDocument.read(current))
                || baseline != null && com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(MonteCarloPlanDocument.read(baseline));
        return new ConditionalBenefitAnalysisExecution(premiums ? PREMIUM_VERSION : principal ? LIFE_PRINCIPAL_VERSION : VERSION,
                premiums ? PREMIUM_MODEL : principal ? LIFE_PRINCIPAL_MODEL : MODEL, kind, protocol(kind), baseline == null
                ? List.of(Candidate.capture(Role.ANALYZED_PLAN, current))
                : List.of(Candidate.capture(Role.CURRENT, current), Candidate.capture(Role.BASELINE, baseline)));
    }

    public boolean includesPremiums() { return version == PREMIUM_VERSION; }
    public boolean excludesLifePrincipal() { return version == LIFE_PRINCIPAL_VERSION || includesPremiums() && candidates.stream().anyMatch(c ->
            ConditionalBenefits.hasExcludedPrincipal(MonteCarloPlanDocument.read(c.originalInputs()).getConditionalBenefits())); }
    public String algorithm() { return includesPremiums() ? PREMIUM_ALGORITHM : excludesLifePrincipal() ? LIFE_PRINCIPAL_ALGORITHM : ALGORITHM; }

    public void validate(AnalysisKind kind, String current, String baseline) {
        if (!equals(capture(kind, current, baseline)))
            throw new IllegalArgumentException("Conditional-benefit capability differs from the original analyzer request.");
    }

    public static String protocol(AnalysisKind kind) {
        if (kind == AnalysisKind.COUPLE_SOCIAL_SECURITY || kind == AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY)
            throw new IllegalArgumentException("Social Security-only results cannot claim conditional-benefit execution.");
        if (kind.isMonteCarlo()) return kind.name().endsWith("LONGEVITY")
                ? "GENERATED_SHARED_LIFETIME_JAN1_PRIOR_DEC31_V1" : "GENERATED_CONFIGURED_DEATHS_V1";
        return kind == AnalysisKind.COUPLE_WEIGHTED || kind == AnalysisKind.INDIVIDUAL_WEIGHTED
                ? "WEIGHTED_LIFETIME_JAN1_PRIOR_DEC31_FULL_EVALUATION_V1" : "INTEGRATED_DETERMINISTIC_V1";
    }

    /** Original assumptions only. This does not evaluate benefits or infer which events occurred. */
    public List<String> disclosure() {
        var lines = new ArrayList<String>();
        lines.add("Conditional benefit execution: " + model + "; " + scenarioProtocol + ".");
        for (var candidate : candidates) {
            String side = candidate.role() == Role.ANALYZED_PLAN ? "Analyzed plan"
                    : candidate.role() == Role.CURRENT ? "Current" : "Baseline";
            var plan = MonteCarloPlanDocument.read(candidate.originalInputs());
            lines.addAll(com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.lines(plan, side));
        }
        return List.copyOf(lines);
    }

    private static String digest(String document) {
        return AnalysisSnapshotCodec.sha256(Objects.requireNonNull(document).getBytes(StandardCharsets.UTF_8));
    }

    private static String definitionsDigest(RetirementPlan plan) {
        try { return AnalysisSnapshotCodec.sha256(JSON.writeValueAsBytes(
                com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(plan)
                        ? List.of(plan.getConditionalBenefits(), plan.getHousehold().getExpenses().stream().filter(
                                com.daviddunn.retirementplanner.domain.financial.Expense::isContractualInsurancePremium).toList())
                        : plan.getConditionalBenefits())); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot bind original conditional benefits.", failure); }
    }
}
