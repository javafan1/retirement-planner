package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.util.*;

public final class AnalysisCompatibilityPolicy {
    public enum State { NO_SAVED_RESULTS, CURRENT, OUTDATED, INCOMPATIBLE_OR_UNAVAILABLE, INPUTS_NEED_ATTENTION }
    public record Assessment(State state, String explanation) { }
    public Assessment assess(AnalysisSnapshot saved, AnalysisFingerprintService.Assessment inputs,
            Map<String, String> algorithms, Map<String, String> references) {
        if (saved == null) return new Assessment(State.NO_SAVED_RESULTS, "No saved results.");
        boolean enabledExpenses = saved.payload() instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.expenseExecution() != null;
        boolean pairedExpenses = saved.payload() instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && mc.pairedExpenseExecution() != null;
        if (saved.schemaVersion() != AnalysisSnapshot.SCHEMA
                || saved.fingerprintVersion() != (saved.payload() instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                    ? mc.fingerprintVersion() : AnalysisFingerprintService.VERSION))
            return unavailable("Unsupported snapshot format.");
        for (var version : saved.algorithms().entrySet()) {
            if (version.getKey().equals(ConditionalBenefitAnalysisExecution.KEY)) {
                var capability = saved.benefitExecution() != null ? saved.benefitExecution()
                        : saved.payload() instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc ? mc.benefitExecution() : null;
                if (capability == null || !version.getValue().equals(capability.algorithm()))
                    return unavailable("Unsupported conditional-benefit analyzer capability.");
                continue;
            }
            if (version.getKey().equals(com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY)) {
                if (!AnalysisVersions.usesOrdinaryPensionSelection(saved.kind())
                        || !version.getValue().equals(com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.CORRECTED))
                    return unavailable("Unsupported pension-selection algorithm semantics.");
                continue;
            }
            if (pairedExpenses && version.getKey().equals("paired-recurring-expenses")) {
                if (!version.getValue().equals(com.daviddunn.retirementplanner.app.montecarlo.PairedExpenseExecution.ALGORITHM))
                    return unavailable("Unsupported paired stochastic expense model.");
                continue;
            }
            if (enabledExpenses && version.getKey().equals("recurring-expenses")) {
                if (!version.getValue().equals(com.daviddunn.retirementplanner.app.montecarlo.ExpenseExecution.ALGORITHM))
                    return unavailable("Unsupported stochastic expense model.");
                continue;
            }
            String readable = version.getKey().equals("analysis-kind") ? saved.kind().name() + "-v1" : "v1";
            if (!(Set.of("analysis-kind", "monthly-social-security", "projection", "weighted", "algorithm").contains(version.getKey())
                    || saved.kind().isMonteCarlo() && Set.of("monte-carlo", "random-streams", "statistics").contains(version.getKey()))
                    || !version.getValue().equals(readable)) return unavailable("The saved financial algorithm semantics are not supported by this application.");
        }
        if (!inputs.valid() || inputs.contextFingerprint() == null)
            return new Assessment(State.INPUTS_NEED_ATTENTION, inputs.problem() == null ? "Input context must be assessed." : inputs.problem());
        var originalAlgorithms = new TreeMap<>(saved.algorithms());
        var expectedAlgorithms = new TreeMap<>(algorithms);
        originalAlgorithms.remove(com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY);
        expectedAlgorithms.remove(com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY);
        originalAlgorithms.remove(ConditionalBenefitAnalysisExecution.KEY);
        expectedAlgorithms.remove(ConditionalBenefitAnalysisExecution.KEY);
        if (!originalAlgorithms.equals(expectedAlgorithms) || !saved.references().equals(references)
                || !saved.inputFingerprint().equals(inputs.fingerprint())
                || !saved.contextFingerprint().equals(inputs.contextFingerprint())) {
            return new Assessment(State.OUTDATED, "These results were generated using previous plan or analysis assumptions and may not reflect the current retirement plan.");
        }
        if (!saved.kind().isMonteCarlo() && saved.kind() != AnalysisKind.COUPLE_SOCIAL_SECURITY
                && saved.kind() != AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY
                && Boolean.TRUE.equals(inputs.conditionalBenefitsConfigured()) && saved.benefitExecution() == null)
            return new Assessment(State.OUTDATED, "Historical result does not establish conditional-benefit analyzer execution. Original results remain available.");
        if (AnalysisVersions.usesOrdinaryPensionSelection(saved.kind())
                && !saved.algorithms().containsKey(com.daviddunn.retirementplanner.app.projection.PensionSelectionSemantics.KEY)
                && !Boolean.FALSE.equals(inputs.legacyPensionAffected())) {
            return new Assessment(State.OUTDATED,
                    "Historical pension-tax calculation semantics: corrected death-selected pension income is not established. Original results remain available.");
        }
        return new Assessment(State.CURRENT, "Saved results match current effective inputs.");
    }
    public Assessment unavailable(String reason) { return new Assessment(State.INCOMPATIBLE_OR_UNAVAILABLE, reason); }
    /** Copy assessment under the original frozen analysis settings; UI must reassess its next-run controls later. */
    public Assessment assessCopy(AnalysisSnapshot s, com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) {
        if (s.kind().isMonteCarlo()) return new Assessment(State.OUTDATED, "Historical Monte Carlo copy; current effective inputs have not been assessed.");
        try {
            if ((s.shape() == AnalysisKind.Shape.COUPLE) != plan.getHousehold().hasSpouse())
                return new Assessment(State.OUTDATED, "Historical copy belongs to the previous household shape.");
            AnalysisFingerprintService.Inputs inputs;
            if (s.payload() instanceof ExhaustiveSnapshot) {
                inputs = AnalysisFingerprintService.Inputs.none();
            } else if (s.payload() instanceof CoupleSocialSecuritySnapshot ss) {
                var r = ss.request(); var longevity = ss.frozen().longevity();
                inputs = new AnalysisFingerprintService.Inputs(longevity.primaryAdjustment().factor(), longevity.spouseAdjustment().factor(),
                        r.conditioning(), r.valuation(), r.discount(), null, null, List.of(), List.of(), null);
            } else if (s.payload().individual() != null) {
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
