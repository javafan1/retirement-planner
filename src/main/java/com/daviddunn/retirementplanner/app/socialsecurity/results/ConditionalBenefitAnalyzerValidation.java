package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.projection.ConditionalBenefitExecutionSemantics;
import java.util.*;

/** Exact capability/family/input combinations. Historical validation performs no financial execution. */
public final class ConditionalBenefitAnalyzerValidation {
    private ConditionalBenefitAnalyzerValidation() { }

    public static void validate(AnalysisKind kind, AnalysisPayload payload, SnapshotContext context,
            ConditionalBenefitAnalysisExecution capability, Map<String, String> algorithms,
            String inputFingerprint, String contextFingerprint) {
        if (kind.isMonteCarlo()) {
            if (capability != null) throw new IllegalArgumentException("Monte Carlo capability belongs to its versioned payload.");
            boolean enabled = payload instanceof MonteCarloSnapshot mc && mc.benefitExecution() != null;
            if (algorithms.containsKey(ConditionalBenefitAnalysisExecution.KEY) != enabled)
                throw new IllegalArgumentException("Monte Carlo conditional-benefit tag/capability mismatch.");
            return;
        }
        var retained = retained(payload);
        if (capability == null) {
            if (algorithms.containsKey(ConditionalBenefitAnalysisExecution.KEY)
                    || retained.stream().flatMap(p -> p.years().stream()).flatMap(y -> y.sources().stream())
                    .anyMatch(s -> s.conditionalBenefit() != null))
                throw new IllegalArgumentException("Conditional analyzer evidence lacks its execution capability.");
            return;
        }
        if (kind != capability.family()) throw new IllegalArgumentException("Conditional analyzer family mismatch.");
        var expected = new TreeMap<>(AnalysisVersions.calculatedAlgorithms(kind, payload));
        expected.put(ConditionalBenefitAnalysisExecution.KEY, ConditionalBenefitAnalysisExecution.ALGORITHM);
        if (!expected.equals(algorithms)) throw new IllegalArgumentException("Unsupported conditional analyzer algorithm combination.");
        var plan = MonteCarloPlanDocument.read(capability.candidates().getFirst().originalInputs());
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireAnalyzerSupported(plan);
        var fingerprints = new AnalysisFingerprintService();
        var inputs = inputs(payload, context);
        if (!fingerprints.fingerprint(kind, plan, inputs).equals(inputFingerprint)
                || !fingerprints.effectiveContextFingerprint(kind, plan).equals(contextFingerprint))
            throw new IllegalArgumentException("Conditional analyzer original-input fingerprint mismatch.");
        for (var projection : retained) ConditionalBenefitExecutionSemantics.validateRetainedEvidence(plan, projection);
    }

    public static AnalysisFingerprintService.Inputs inputs(AnalysisPayload payload, SnapshotContext context) {
        if (payload instanceof ExhaustiveSnapshot) return AnalysisFingerprintService.Inputs.none();
        if (payload instanceof IndividualDeterministicSnapshot i)
            return new AnalysisFingerprintService.Inputs(null, null, null, null, null, null, null, List.of(), List.of(), i.deathYear());
        if (payload instanceof QuickComparisonSnapshot q)
            return new AnalysisFingerprintService.Inputs(null, null, null, null, null, q.requested(), q.prerequisiteFingerprint(),
                    q.candidates().stream().map(c -> new AnalysisFingerprintService.Candidate(c.strategy(), c.rank(), c.expected())).toList(), List.of(), null);
        if (payload.individual() != null) {
            var i = payload.individual(); var r = i.mortality().request();
            return new AnalysisFingerprintService.Inputs(r.mortalityAdjustment().factor(), null, r.mortalityBaseDate(),
                    i.valuationDate(), i.discountRate(), null, null, List.of(), List.of(), null);
        }
        if (payload.couple() != null) {
            var m = payload.couple().metadata(); var a = m.longevityAssumptions();
            return new AnalysisFingerprintService.Inputs(a.primaryAdjustment().factor(), a.spouseAdjustment().factor(),
                    a.mortalityBaseDate(), m.valuationDate(), m.realDiscountRate(), null, null,
                    List.of(), Objects.requireNonNull(context.baseline()).fingerprintSections(), null);
        }
        throw new IllegalArgumentException("Unsupported conditional analyzer payload.");
    }

    private static List<RetainedProjectionSnapshot> retained(AnalysisPayload payload) {
        var result = new ArrayList<RetainedProjectionSnapshot>();
        if (payload instanceof QuickComparisonSnapshot q) {
            result.add(q.baseline()); q.entries().forEach(e -> { if (e.detail() != null) result.add(e.detail()); });
        } else if (payload instanceof ExhaustiveSnapshot e) {
            result.add(e.baseline()); e.entries().forEach(v -> { if (v.detail() != null) result.add(v.detail()); });
        } else if (payload instanceof IndividualDeterministicSnapshot i) {
            result.add(i.baseline()); i.entries().forEach(v -> { if (v.detail() != null) result.add(v.detail()); });
        }
        return result;
    }
}
