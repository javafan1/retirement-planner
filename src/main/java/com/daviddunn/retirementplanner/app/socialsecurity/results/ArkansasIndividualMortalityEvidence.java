package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathSnapshot;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.CANONICAL;

/** The exact positive-probability scenario sequence for each of the nine claiming ages. */
public record ArkansasIndividualMortalityEvidence(int firstYear, List<Scenario> scenarios) {
    public record Scenario(int occurrence, WeightedSnapshots.Strategy strategy, int deathYear,
            String annualDigest, EstateAtSecondDeathSnapshot opening, String outcomeDigest) {
        public Scenario {
            if (occurrence < 0 || Objects.requireNonNull(strategy).restore().hasSpouse()
                    || (annualDigest == null) == (opening == null)) {
                throw new IllegalArgumentException("Invalid Arkansas individual lifetime execution.");
            }
            if (annualDigest != null) ArkansasAnalysisEvidenceV2.requireDigest(annualDigest);
            ArkansasAnalysisEvidenceV2.requireDigest(outcomeDigest);
        }
    }

    public ArkansasIndividualMortalityEvidence {
        if (scenarios.isEmpty() || scenarios.size() > ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS)
            throw ArkansasAnalysisEvidenceV2.capacity();
        scenarios = List.copyOf(scenarios);
        for (int index = 0; index < scenarios.size(); index++) {
            var scenario = scenarios.get(index);
            if (scenario.occurrence() != index || scenario.deathYear() < firstYear
                    || (scenario.deathYear() == firstYear) != (scenario.opening() != null)) {
                throw new IllegalArgumentException("Invalid individual lifetime coverage.");
            }
        }
    }

    void validatePayload(AnalysisKind kind, AnalysisPayload payload) {
        if (kind != AnalysisKind.INDIVIDUAL_WEIGHTED || !(payload instanceof WeightedSnapshots.Payload weighted)
                || weighted.individual() == null || weighted.individual().mode() != SinglePersonMortalityAnalysis.Mode.INTEGRATED) {
            throw new IllegalArgumentException("Arkansas lifetime provenance requires an individual integrated result.");
        }
        int index = 0;
        int calculations = 0;
        for (var entry : weighted.individual().entries()) {
            var nominal = BigDecimal.ZERO;
            var assets = BigDecimal.ZERO;
            var pv = BigDecimal.ZERO;
            for (var outcome : entry.outcomes()) {
                if (index >= scenarios.size()) throw new IllegalArgumentException("Missing individual lifetime observation.");
                var observation = scenarios.get(index++);
                if (!observation.strategy().equals(entry.strategy()) || observation.deathYear() != outcome.deathYear()
                        || !observation.outcomeDigest().equals(CANONICAL.executionDigest(outcome))) {
                    throw new IllegalArgumentException("Individual lifetime outcome differs from the observed scenario.");
                }
                if (observation.opening() == null) calculations++;
                else if (!observation.opening().nominalAfterTaxEstate().equals(outcome.nominalEstate())
                        || !observation.opening().nominalInvestableAssets().equals(outcome.investableAssets())) {
                    throw new IllegalArgumentException("Opening assets differ from the individual lifetime outcome.");
                }
                nominal = nominal.add(outcome.nominalEstate().multiply(outcome.probability()));
                assets = assets.add(outcome.investableAssets().multiply(outcome.probability()));
                pv = pv.add(outcome.presentValue().multiply(outcome.probability()));
            }
            if (!nominal.equals(entry.nominal()) || !assets.equals(entry.assets()) || !pv.equals(entry.pv())) {
                throw new IllegalArgumentException("Individual lifetime outcomes do not support the retained expected values.");
            }
        }
        if (index != scenarios.size() || calculations != weighted.individual().projectionCount()) {
            throw new IllegalArgumentException("Invalid Arkansas lifetime execution count.");
        }
    }

    void validateSource(CompleteProjectionSnapshot source) {
        var input = source.restoreInputs();
        var start = input.getPlanningAssumptions().getProjectionStartDate();
        if (start.getYear() != firstYear || input.getHousehold().hasSpouse())
            throw new IllegalArgumentException("Individual lifetime source differs from the original projection.");
        if (scenarios.stream().anyMatch(scenario -> scenario.opening() != null)) {
            var opening = openingAssets(input);
            if (scenarios.stream().anyMatch(scenario -> scenario.opening() != null && !opening.equals(scenario.opening()))) {
                throw new IllegalArgumentException("Lifetime opening evidence differs from the frozen source assets.");
            }
        }
    }

    static EstateAtSecondDeathSnapshot openingAssets(com.daviddunn.retirementplanner.domain.baseline.RetirementPlanSnapshot input) {
        var plan = new RetirementPlan(input.getHousehold(), input.getAccountPortfolio(), input.getPlanningAssumptions(),
                input.getRothConversionRequest(), input.getNonInvestableAssets(), null, input.getConditionalBenefits());
        if (input.getFinancialCalculationMode() != null) plan.setFinancialCalculationMode(input.getFinancialCalculationMode());
        return new EstateAtSecondDeathCalculator().calculateOpening(plan, input.getPlanningAssumptions().getProjectionStartDate());
    }

    static final class Observer implements SinglePersonMortalityAnalysis.FinancialObservation {
        private final CompleteProjectionSnapshot source;
        private final AnalysisCancellationToken cancellation;
        private final int first;
        private final List<Scenario> scenarios = new ArrayList<>();
        private ProjectionExecutionResult.Completed observed;
        private ProjectionEvaluationContext context;

        Observer(CompleteProjectionSnapshot source, AnalysisCancellationToken cancellation) {
            this.source = source;
            this.cancellation = cancellation;
            first = source.annual().years().getFirst().year();
        }

        void projected(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionExecutionResult execution) {
            cancellation.throwIfCancellationRequested();
            if (observed != null || !source.inputDigest().equals(CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan)))) {
                throw new IllegalArgumentException("Individual lifetime calculation used different frozen inputs.");
            }
            if (execution instanceof ProjectionExecutionResult.Completed complete) {
                observed = complete;
                this.context = context;
            }
        }

        @Override public void accept(SocialSecurityHouseholdClaimingStrategy strategy, Projection projection,
                EstateAtSecondDeathSnapshot snapshot, SinglePersonMortalityAnalysis.Outcome outcome) {
            cancellation.throwIfCancellationRequested();
            if (scenarios.size() == ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS) throw ArkansasAnalysisEvidenceV2.capacity();
            int death = outcome.deathYear();
            String digest = null;
            if (projection != null) {
                if (observed == null || !observed.completedYears().equals(projection.getYears())
                        || !context.socialSecurityStrategy().filter(strategy::equals).isPresent()
                        || context.householdLifetimeScenario().orElseThrow().primaryDeathYear().orElseThrow().getValue() != death
                        || context.horizonPolicy() != ProjectionEvaluationContext.HorizonPolicy.EXACT_REQUESTED
                        || projection.getStartYear() != first || projection.getEndYear() != death - 1) {
                    throw new IllegalArgumentException("Individual lifetime result differs from the observed financial scenario.");
                }
                digest = CANONICAL.executionDigest(RetainedProjectionSnapshot.captureArkansas(projection));
            } else if (observed != null || !snapshot.balanceDate().equals(LocalDate.of(death, 1, 1))) {
                throw new IllegalArgumentException("Unexpected annual execution for opening-death assets.");
            }
            scenarios.add(new Scenario(scenarios.size(), WeightedSnapshots.Strategy.from(strategy), death, digest,
                    projection == null ? snapshot : null, CANONICAL.executionDigest(outcome)));
            observed = null;
            context = null;
        }

        ArkansasIndividualMortalityEvidence finish() {
            cancellation.throwIfCancellationRequested();
            if (observed != null) throw new IllegalArgumentException("Unbound individual lifetime execution.");
            return new ArkansasIndividualMortalityEvidence(first, scenarios);
        }
    }
}
