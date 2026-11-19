package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchCalculator.CandidateObserver;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluationFailure;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyResult;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.CANONICAL;

/** Each deterministic occurrence is recorded before ranking or full-detail retention. */
public record ArkansasDeterministicEvidence(List<Candidate> candidates) {
    public record Unavailable(int year, ArkansasProjectionUnavailableException.Reason reason, ArkansasTaxIssue.Kind kind,
            List<AccountOwnership> reviewRecipients) {
        public Unavailable {
            Objects.requireNonNull(reason);
            reviewRecipients = List.copyOf(reviewRecipients);
            var recipients = java.util.Set.copyOf(reviewRecipients);
            new ArkansasTaxIssue(kind, recipients);
            if (!reviewRecipients.equals(recipients.stream().sorted().toList())) {
                throw new IllegalArgumentException("Noncanonical Arkansas review recipients.");
            }
        }

        static Unavailable from(ArkansasProjectionUnavailableException issue) {
            return new Unavailable(issue.year(), issue.reason(), issue.issue().kind(),
                    issue.issue().reviewRecipients().stream().sorted().toList());
        }
    }

    public record Candidate(int order, WeightedSnapshots.Strategy strategy, Integer primaryDeathOverride,
            int firstYear, int completedYears, String annualDigest, String metricsDigest,
            ArkansasAnalysisEvidenceV2.Shortage shortage, Unavailable unavailable, WeightedSnapshots.Failure failure) {
        public Candidate {
            Objects.requireNonNull(strategy).restore();
            if (order < 0 || completedYears < 0 || (metricsDigest != null) == (failure != null)
                    || (metricsDigest != null && (shortage != null || unavailable != null || completedYears == 0))
                    || (shortage != null && (unavailable != null || shortage.year() != firstYear + (long) completedYears))) {
                throw new IllegalArgumentException("Invalid Arkansas deterministic outcome.");
            }
            if (metricsDigest != null) ArkansasAnalysisEvidenceV2.requireDigest(metricsDigest);
            if (metricsDigest != null || shortage != null) ArkansasAnalysisEvidenceV2.requireDigest(annualDigest);
            else if (annualDigest != null || completedYears != 0) throw new IllegalArgumentException("Unavailable candidate has annual results.");
        }

        void validate(int occurrence, WeightedSnapshots.Strategy election, ProjectionMetrics metrics,
                WeightedSnapshots.Failure expectedFailure, RetainedProjectionSnapshot detail) {
            if (order != occurrence || !strategy.equals(election) || !Objects.equals(failure, expectedFailure)
                    || !Objects.equals(metricsDigest, metrics == null ? null : CANONICAL.executionDigest(metrics))) {
                throw new IllegalArgumentException("Deterministic results differ from their observed candidate.");
            }
            if (detail != null && (detail.arkansas() == null || detail.years().size() != completedYears
                    || detail.years().getFirst().year() != firstYear || !annualDigest.equals(canonicalAnnual(detail)))) {
                throw new IllegalArgumentException("Deterministic candidate detail differs from its observed annual results.");
            }
        }
    }

    public ArkansasDeterministicEvidence {
        if (candidates.size() < 2 || candidates.size() > ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS)
            throw ArkansasAnalysisEvidenceV2.capacity();
        candidates = List.copyOf(candidates);
        var baseline = candidates.getFirst();
        if (baseline.failure() != null) throw new IllegalArgumentException("Observed deterministic baseline must have completed.");
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            if (candidate.order() != index || candidate.firstYear() != baseline.firstYear()
                    || !Objects.equals(candidate.primaryDeathOverride(), baseline.primaryDeathOverride())) {
                throw new IllegalArgumentException("Invalid deterministic request occurrence or death override.");
            }
        }
    }

    void validatePayload(AnalysisKind kind, AnalysisPayload payload) {
        switch (kind) {
            case COUPLE_EXHAUSTIVE -> {
                var data = (ExhaustiveSnapshot) payload;
                validate(data.baseline(), data.entries(), null);
            }
            case INDIVIDUAL_DETERMINISTIC -> {
                var data = (IndividualDeterministicSnapshot) payload;
                validate(data.baseline(), data.entries(), data.deathYear());
            }
            default -> throw new IllegalArgumentException("Unsupported Arkansas deterministic evidence family.");
        }
    }

    private void validate(RetainedProjectionSnapshot baseline, List<ExhaustiveSnapshot.Entry> entries, Integer override) {
        if (candidates.size() != entries.size() + 1 || !Objects.equals(override, candidates.getFirst().primaryDeathOverride())) {
            throw new IllegalArgumentException("Deterministic candidate coverage differs from the original request.");
        }
        candidates.getFirst().validate(0, baseline.strategy(), baseline.metrics(), null, baseline);
        for (var entry : entries) {
            candidates.get(entry.order()).validate(entry.order(), entry.strategy(), entry.metrics(), entry.failure(), entry.detail());
        }
    }

    void validateSource(CompleteProjectionSnapshot source) {
        var baseline = candidates.getFirst();
        if (baseline.firstYear() != source.annual().years().getFirst().year()
                || baseline.completedYears() != source.annual().years().size()
                || baseline.primaryDeathOverride() == null && !baseline.annualDigest().equals(canonicalAnnual(source.annual()))) {
            throw new IllegalArgumentException("Deterministic baseline differs from its original projection or request override.");
        }
    }

    private static String canonicalAnnual(RetainedProjectionSnapshot annual) {
        return CANONICAL.executionDigest(new RetainedProjectionSnapshot(annual.schemaVersion(), annual.accounts(),
                annual.years(), null, null, null, List.of(), annual.arkansas()));
    }

    static final class Observer implements CandidateObserver {
        private final CompleteProjectionSnapshot source;
        private final AnalysisCancellationToken cancellation;
        private final Integer death;
        private final List<Candidate> observed = new ArrayList<>();
        private WeightedSnapshots.Strategy strategy;
        private ProjectionExecutionResult execution;

        Observer(CompleteProjectionSnapshot source, Integer death, AnalysisCancellationToken cancellation) {
            this.source = source;
            this.death = death;
            this.cancellation = cancellation;
        }

        @Override public void starting(int occurrence, SocialSecurityHouseholdClaimingStrategy election) {
            cancellation.throwIfCancellationRequested();
            if (strategy != null || occurrence != observed.size() || occurrence == ArkansasAnalysisEvidenceV2.MAX_EXECUTIONS)
                throw new IllegalArgumentException("Invalid or oversized Arkansas deterministic occurrence sequence.");
            strategy = WeightedSnapshots.Strategy.from(election);
        }

        void projected(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionExecutionResult results) {
            cancellation.throwIfCancellationRequested();
            if (strategy == null || execution != null
                    || !source.inputDigest().equals(CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(plan)))
                    || !Objects.equals(context.householdLifetimeScenario().flatMap(life -> life.primaryDeathYear())
                                .map(java.time.Year::getValue).orElse(null), death)
                    || !Objects.equals(context.socialSecurityStrategy().map(WeightedSnapshots.Strategy::from).orElse(null),
                                observed.isEmpty() ? null : strategy)) {
                throw new IllegalArgumentException("Deterministic execution differs from its frozen candidate.");
            }
            execution = results;
        }

        @Override public void completed(IntegratedSocialSecurityStrategyResult result) {
            cancellation.throwIfCancellationRequested();
            if (!(execution instanceof ProjectionExecutionResult.Completed complete)
                    || !complete.completedYears().equals(result.projection().getYears())
                    || !strategy.equals(WeightedSnapshots.Strategy.from(result.evaluatedStrategy()))) {
                throw new IllegalArgumentException("Deterministic metric reduction was not produced by its observed calculation.");
            }
            record(CANONICAL.executionDigest(result.metrics()), null, null, null);
        }

        @Override public void failed(RuntimeException exception, IntegratedSocialSecurityStrategyEvaluationFailure failure) {
            cancellation.throwIfCancellationRequested();
            if (execution instanceof ProjectionExecutionResult.Completed) throw exception;
            var unavailable = exception instanceof ArkansasProjectionUnavailableException issue
                    ? Unavailable.from(issue) : null;
            var shortage = execution instanceof ProjectionExecutionResult.InsufficientFunds failed
                    ? ArkansasAnalysisEvidenceV2.Shortage.from(failed.fundingFailure()) : null;
            record(null, shortage, unavailable, new WeightedSnapshots.Failure(failure.message(), failure.category()));
        }

        private void record(String metrics, ArkansasAnalysisEvidenceV2.Shortage shortage,
                Unavailable unavailable, WeightedSnapshots.Failure failure) {
            var annual = new Projection();
            if (execution != null) for (var year : execution.completedYears()) {
                cancellation.throwIfCancellationRequested();
                annual.addYear(year);
            }
            String digest = execution == null ? null : CANONICAL.executionDigest(annual.isEmpty()
                    ? List.of() : RetainedProjectionSnapshot.captureArkansas(annual));
            observed.add(new Candidate(observed.size(), strategy, death, source.annual().years().getFirst().year(),
                    annual.size(), digest, metrics, shortage, unavailable, failure));
            execution = null;
            strategy = null;
        }

        ArkansasDeterministicEvidence finish() {
            cancellation.throwIfCancellationRequested();
            if (strategy != null) throw new IllegalArgumentException("Incomplete deterministic evidence.");
            return new ArkansasDeterministicEvidence(observed);
        }
    }
}
