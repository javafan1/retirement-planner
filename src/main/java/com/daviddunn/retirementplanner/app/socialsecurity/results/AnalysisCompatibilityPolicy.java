package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.util.*;

public final class AnalysisCompatibilityPolicy {
    public enum State { NO_SAVED_RESULTS, CURRENT, OUTDATED, INCOMPATIBLE_OR_UNAVAILABLE, INPUTS_NEED_ATTENTION }
    public record Assessment(State state, String explanation) { }
    public Assessment assess(AnalysisSnapshot saved, AnalysisFingerprintService.Assessment inputs,
            Map<String, String> algorithms, Map<String, String> references) {
        if (saved == null) return new Assessment(State.NO_SAVED_RESULTS, "No saved results.");
        if (saved.schemaVersion() != AnalysisSnapshot.SCHEMA || saved.fingerprintVersion() != AnalysisFingerprintService.VERSION)
            return unavailable("Unsupported snapshot format.");
        for (var version : saved.algorithms().entrySet()) {
            String readable = version.getKey().equals("analysis-kind") ? saved.kind().name() + "-v1" : "v1";
            if (!Set.of("analysis-kind", "monthly-social-security", "projection", "weighted", "algorithm").contains(version.getKey())
                    || !version.getValue().equals(readable)) return unavailable("The saved financial algorithm semantics are not supported by this application.");
        }
        if (!inputs.valid() || inputs.contextFingerprint() == null)
            return new Assessment(State.INPUTS_NEED_ATTENTION, inputs.problem() == null ? "Input context must be assessed." : inputs.problem());
        if (!saved.algorithms().equals(algorithms) || !saved.references().equals(references)
                || !saved.inputFingerprint().equals(inputs.fingerprint())
                || !saved.contextFingerprint().equals(inputs.contextFingerprint())) {
            return new Assessment(State.OUTDATED, "These results were generated using previous plan or analysis assumptions and may not reflect the current retirement plan.");
        }
        return new Assessment(State.CURRENT, "Saved results match current effective inputs.");
    }
    public Assessment unavailable(String reason) { return new Assessment(State.INCOMPATIBLE_OR_UNAVAILABLE, reason); }
    /** Copy assessment under the original frozen analysis settings; UI must reassess its next-run controls later. */
    public Assessment assessCopy(AnalysisSnapshot s, com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) {
        try {
            if ((s.shape() == AnalysisKind.Shape.COUPLE) != plan.getHousehold().hasSpouse())
                return new Assessment(State.OUTDATED, "Historical copy belongs to the previous household shape.");
            AnalysisFingerprintService.Inputs inputs;
            if (s.payload().individual() != null) {
                var i = s.payload().individual(); var r = i.mortality().request();
                inputs = new AnalysisFingerprintService.Inputs(r.mortalityAdjustment().factor(), null,
                        r.mortalityBaseDate(), i.valuationDate(), i.discountRate(), null, null, List.of(), List.of(), null);
            } else {
                var m = s.payload().couple().metadata(); var longevity = m.longevityAssumptions();
                inputs = new AnalysisFingerprintService.Inputs(longevity.primaryAdjustment().factor(), longevity.spouseAdjustment().factor(),
                        longevity.mortalityBaseDate(), m.valuationDate(), m.realDiscountRate(), null, null,
                        List.of(), s.context().baseline().fingerprintSections(), null);
            }
            return assess(s, new AnalysisFingerprintService().assess(s.kind(), plan, inputs, null),
                    AnalysisVersions.algorithms(s.kind()), AnalysisVersions.references(s.kind()));
        } catch (RuntimeException invalid) {
            return new Assessment(State.OUTDATED, "Historical copy; effective inputs could not be matched to the new plan.");
        }
    }
}
