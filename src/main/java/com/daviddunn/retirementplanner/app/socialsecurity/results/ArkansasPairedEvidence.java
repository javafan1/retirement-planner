package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPlanDocument;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyCandidate;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonAnalyzer.Stage;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyOutcome;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ArkansasValidationTax;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.CANONICAL;
import static com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.requireDigest;

/** Ordered source and world observations. Trial index is the zero-based position in each side's observations. */
public record ArkansasPairedEvidence(Side current, Side baseline) {
    public static final int MAX_WORLDS = 12_000;
    public enum Role { CURRENT_A, BASELINE_B }
    public enum Status { COMPLETED, INSUFFICIENT_FUNDS, OPENING_ASSETS }

    public record Source(Role role, UUID acceptedId, StateTaxJurisdiction jurisdiction, String projectionProvenance,
            String effectiveInput, Integer survivorOverride) {
        public Source {
            Objects.requireNonNull(role);
            Objects.requireNonNull(acceptedId);
            if (!Objects.requireNonNull(jurisdiction).productionAdmitted()
                    || Objects.requireNonNull(projectionProvenance).isBlank()) {
                throw new IllegalArgumentException("Invalid paired projection source.");
            }
            requireDigest(effectiveInput);
            if (survivorOverride != null) {
                com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy.validateClaimingAge(survivorOverride);
            }
        }

        static Source capture(Role role, CompleteProjectionSnapshot projection, MonteCarloStrategyCandidate candidate, boolean longevity) {
            Integer override = longevity && projection.people().spouse() != null ? candidate.survivorClaimingAge() : null;
            var source = new Source(role, projection.id(), candidate.jurisdiction(), projection.provenance(),
                    candidate.projectionInputDigest(), override);
            source.validate(projection);
            return source;
        }

        void validate(CompleteProjectionSnapshot original) {
            if (!acceptedId.equals(original.id()) || !projectionProvenance.equals(original.provenance())) {
                throw new IllegalArgumentException("Paired side does not identify the original projection.");
            }
            var frozen = original.restoreInputs();
            if (frozen.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction() != jurisdiction
                    || original.arkansasHistory() != (jurisdiction == StateTaxJurisdiction.AR)) {
                throw new IllegalArgumentException("Paired side has incompatible state-tax evidence.");
            }
            String effective = survivorOverride == null ? original.inputDigest() : overlay(frozen, survivorOverride);
            if (!effective.equals(effectiveInput)) {
                throw new IllegalArgumentException("Paired side differs from its accepted inputs beyond the authorized survivor election.");
            }
        }

        private static String overlay(RetirementPlanSnapshot source, int age) {
            if (!source.getHousehold().hasSpouse()) throw new IllegalArgumentException("A survivor election requires a couple.");
            var original = source.getPlanningAssumptions();
            var death = original.getDeathScenarioAssumptions();
            var effective = new PlanningAssumptions(original.getEconomicAssumptions(), original.getTaxAssumptions(),
                    original.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death.getDeathScenario(), death.getDeathYear(), age),
                    original.getProjectionLengthYears(), original.getProjectionStartDate(), original.getStateTaxAssumptions());
            var snapshot = new RetirementPlanSnapshot(source.getHousehold(), source.getAccountPortfolio(), effective,
                    source.getRothConversionRequest(), source.getNonInvestableAssets(), source.getConditionalBenefits());
            if (source.getFinancialCalculationMode() != null) snapshot.setFinancialCalculationMode(source.getFinancialCalculationMode());
            return CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(snapshot));
        }

        private void validateDocument(String document, boolean survivorElection) {
            var plan = MonteCarloPlanDocument.read(document);
            var age = plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge();
            if (plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction() != jurisdiction
                    || !CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan)).equals(effectiveInput)
                    || !Objects.equals(survivorOverride, survivorElection ? age : null)) {
                throw new IllegalArgumentException("Paired request does not identify the effective side.");
            }
        }
    }

    public record Trial(Status status, String annualDigest, String outcomeDigest) {
        public Trial {
            Objects.requireNonNull(status);
            if (status == Status.OPENING_ASSETS && annualDigest != null) {
                throw new IllegalArgumentException("Opening assets have no annual execution.");
            }
            if (status != Status.OPENING_ASSETS) requireDigest(annualDigest);
            requireDigest(outcomeDigest);
        }
    }

    public record Side(Source source, List<Trial> trials) {
        public Side {
            Objects.requireNonNull(source);
            if (trials.isEmpty() || trials.size() > MAX_WORLDS) throw ArkansasAnalysisEvidenceV2.capacity();
            trials = List.copyOf(trials);
        }
    }

    public ArkansasPairedEvidence {
        if (current.source().role() != Role.CURRENT_A || baseline.source().role() != Role.BASELINE_B
                || current.trials().size() != baseline.trials().size()
                || current.source().jurisdiction() != StateTaxJurisdiction.AR && baseline.source().jurisdiction() != StateTaxJurisdiction.AR) {
            throw new IllegalArgumentException("Invalid ordered Arkansas paired dependencies.");
        }
    }

    void validatePayload(AnalysisKind kind, AnalysisPayload payload) {
        if (!kind.isMonteCarlo() || !(payload instanceof MonteCarloSnapshot mc) || mc.paired() == null
                || mc.paired().outcomes().size() != current.trials().size()) {
            throw new IllegalArgumentException("Paired results do not match their observed trials.");
        }
        boolean survivorElection = mc.mode() == MonteCarloSnapshot.Mode.PAIRED_LONGEVITY && mc.hasSpouse();
        current.source().validateDocument(mc.request().planA(), survivorElection);
        baseline.source().validateDocument(mc.request().planB(), survivorElection);
        for (var pair : mc.paired().outcomes()) {
            validateTrial(current.trials().get(pair.index()), pair.a(), null);
            validateTrial(baseline.trials().get(pair.index()), pair.b(), pair.a().history());
        }
    }

    private static void validateTrial(Trial evidence, MonteCarloSnapshot.Outcome result, MonteCarloSnapshot.History prefix) {
        if (!CANONICAL.executionDigest(result.withoutPrefix(prefix)).equals(evidence.outcomeDigest())
                || (result.failure() != null) != (evidence.status() == Status.INSUFFICIENT_FUNDS)
                || (result.terminal() != null && result.terminal().balanceDate().getDayOfYear() == 1)
                        != (evidence.status() == Status.OPENING_ASSETS)) {
            throw new IllegalArgumentException("Paired outcome differs from its observed financial reduction.");
        }
    }

    static final class Observer {
        private final Source source;
        private final int firstYear;
        private final List<Trial> trials = new ArrayList<>();
        private int complete;
        private int shortages;
        private long years;
        private boolean targeted;

        Observer(Source source, int firstYear) {
            this.source = source;
            this.firstYear = firstYear;
        }

        void observe(Stage side, int index, ProjectionExecutionResult execution, MonteCarloStrategyOutcome outcome,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            if ((side == Stage.STRATEGY_A) != (source.role() == Role.CURRENT_A) || index != trials.size()) {
                throw new IllegalArgumentException("Paired observation arrived for another side or trial.");
            }
            boolean arkansas = source.jurisdiction() == StateTaxJurisdiction.AR;
            var compact = MonteCarloSnapshot.Outcome.observed(firstYear, outcome);
            if (execution == null) {
                trials.add(new Trial(Status.OPENING_ASSETS, null, CANONICAL.executionDigest(compact)));
                return;
            }
            var annual = new Projection();
            for (var year : execution.completedYears()) {
                cancellation.throwIfCancellationRequested();
                if (arkansas) {
                    var tax = year.arkansasValidationTax().orElseThrow();
                    if (!tax.versions().equals(ArkansasValidationTax.reviewedVersions())) {
                        throw new IllegalArgumentException("Unexpected Arkansas paired tax policies.");
                    }
                    targeted |= tax.targetMetrics().isPresent();
                } else if (year.arkansasValidationTax().isPresent() || year.stateTaxCalculation().filter(
                        state -> state.jurisdiction() == source.jurisdiction() && state.estimatedIncomeTax().isPresent()).isEmpty()) {
                    throw new IllegalArgumentException("Paired side did not calculate its admitted state tax.");
                }
                annual.addYear(year);
                years++;
            }
            var status = execution.status() == ProjectionExecutionResult.Status.COMPLETED ? Status.COMPLETED : Status.INSUFFICIENT_FUNDS;
            if (status == Status.COMPLETED) complete++;
            else shortages++;
            String annualDigest = CANONICAL.executionDigest(annual.isEmpty() ? List.of()
                    : arkansas ? RetainedProjectionSnapshot.captureArkansas(annual) : RetainedProjectionSnapshot.capture(annual));
            trials.add(new Trial(status, annualDigest, CANONICAL.executionDigest(compact)));
        }

        Side finish() { return new Side(source, trials); }

        ArkansasAnalysisProvenance.Dependency dependency(CompleteProjectionSnapshot original) {
            return new ArkansasAnalysisProvenance.Dependency(original.id(), original.inputDigest(),
                    ArkansasAnalysisProvenance.annualDigest(original), complete, shortages, years, targeted);
        }
    }
}
