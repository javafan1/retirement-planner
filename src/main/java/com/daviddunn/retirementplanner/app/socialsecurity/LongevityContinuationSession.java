package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyCalculator.ScheduleKey;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityScenarioContinuationPlanner.*;

/** One frozen strategy run. No account graph or full projection escapes a carrier attempt. */
final class LongevityContinuationSession {
    private final RetirementPlan plan;
    private final LongevityWeightedIntegratedStrategyRequest request;
    private final Consumer<LongevityContinuationWork> observer;
    private ProjectionEngine engine;
    private final RetirementPlanScenarioCopyService copier = new RetirementPlanScenarioCopyService();
    private final HouseholdLifetimeScenarioMapper mapper = new HouseholdLifetimeScenarioMapper();
    private final EstateAtSecondDeathCalculator estate = new EstateAtSecondDeathCalculator();
    private final SocialSecurityProjectionIncomeProvider socialSecurity = new SocialSecurityProjectionIncomeProvider();
    private final Map<Path, Group> groups;
    private final Set<Path> attempted = new HashSet<>();
    private final Map<Path, Map<Integer, EstateAtSecondDeathSnapshot>> snapshots = new HashMap<>();
    private final int firstYear;
    private final Map<Path, ScheduleKey> carrierKeys = new HashMap<>();
    private final long positive;
    private final boolean structuredFunding;
    private long carriers, successes, failures, fallback, starts, completed, scenarios, outcomes, reused, rows;

    LongevityContinuationSession(RetirementPlan plan, LongevityWeightedIntegratedStrategyRequest request,
            int positive, Consumer<LongevityContinuationWork> observer) {
        this(plan, request, positive, observer, null, false);
    }

    LongevityContinuationSession(RetirementPlan plan, LongevityWeightedIntegratedStrategyRequest request,
            int positive, Consumer<LongevityContinuationWork> observer, ProjectionEngine engine) {
        this(plan, request, positive, observer, engine, false);
    }

    LongevityContinuationSession(RetirementPlan plan, LongevityWeightedIntegratedStrategyRequest request,
            int positive, Consumer<LongevityContinuationWork> observer, ProjectionEngine engine, boolean structuredFunding) {
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireOptimizationSupported(plan);
        this.engine = engine;
        this.plan = plan;
        this.request = request;
        this.observer = observer;
        this.positive = positive;
        this.structuredFunding = structuredFunding;
        firstYear = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        groups = new LongevityScenarioContinuationPlanner().plan(request.longevityScenarios().scenarios(),
                plan.getPlanningAssumptions().getProjectionStartDate(), request.cancellationToken());
        report();
    }

    record SnapshotSource(EstateAtSecondDeathSnapshot snapshot, HouseholdLifetimeScenario execution) { }

    EstateAtSecondDeathSnapshot snapshot(SocialSecurityJointMortalityScenario member) {
        return snapshotWithSource(member).snapshot();
    }

    SnapshotSource snapshotWithSource(SocialSecurityJointMortalityScenario member) {
        request.cancellationToken().throwIfCancellationRequested();
        int death = secondDeathYear(member);
        var deathDate = LocalDate.of(death, 1, 1);
        estate.validateCoverageStart(plan, deathDate);
        if (deathDate.equals(plan.getPlanningAssumptions().getProjectionStartDate())) {
            return new SnapshotSource(estate.calculateOpening(plan, deathDate), null);
        }
        Path path = path(member);
        // Preserve the member's own finite-horizon request validation, including election omission.
        // A validation failure is replayed through the independent engine for the original exception contract.
        boolean covered;
        try {
            var memberKey = socialSecurity.validatedKeyForEquivalence(plan, firstYear, death - 1, context(member));
            var carrierKey = carrierKeys.computeIfAbsent(path, ignored -> {
                var carrier = request.longevityScenarios().scenarios().get(groups.get(path).carrierIndex());
                return socialSecurity.validatedKeyForEquivalence(plan, firstYear,
                        secondDeathYear(carrier) - 1, context(carrier));
            });
            covered = LongevityEquivalenceCoverage.covers(memberKey, carrierKey);
        } catch (AnalysisCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException unproven) {
            return independent(member);
        }
        if (!covered) return independent(member);
        if (attempted.add(path)) attempt(path, groups.get(path));
        request.cancellationToken().throwIfCancellationRequested();
        var available = snapshots.get(path);
        if (available == null) return independent(member);
        var result = available.get(death);
        if (result == null) return independent(member);
        reused++;
        report();
        var carrier = request.longevityScenarios().scenarios().get(groups.get(path).carrierIndex());
        return new SnapshotSource(result, mapper.map(carrier));
    }

    private void attempt(Path path, Group group) {
        request.cancellationToken().throwIfCancellationRequested();
        carriers++;
        report();
        Map<Integer, EstateAtSecondDeathSnapshot> extracted = new HashMap<>();
        try {
            var carrier = request.longevityScenarios().scenarios().get(group.carrierIndex());
            var isolated = copier.copy(plan);
            var projection = project(isolated, carrier);
            for (int index : group.members()) {
                request.cancellationToken().throwIfCancellationRequested();
                int year = secondDeathYear(request.longevityScenarios().scenarios().get(index));
                extracted.computeIfAbsent(year, ignored -> estate.calculate(isolated, projection, LocalDate.of(year, 1, 1)));
            }
        } catch (AnalysisCancelledException cancelled) {
            throw cancelled;
        } catch (com.daviddunn.retirementplanner.app.socialsecurity.results.ArkansasAnalysisEvidenceV2.BudgetExceeded exceeded) {
            throw exceeded;
        } catch (RuntimeException unavailable) {
            // Discard every speculative prefix. Members will run independently in original order.
            failures++;
            report();
            return;
        }
        successes++;
        snapshots.put(path, Map.copyOf(extracted));
        report();
    }

    private SnapshotSource independent(SocialSecurityJointMortalityScenario member) {
        request.cancellationToken().throwIfCancellationRequested();
        fallback++;
        report();
        var isolated = copier.copy(plan);
        var projection = project(isolated, member);
        var result = estate.calculate(isolated, projection, LocalDate.of(secondDeathYear(member), 1, 1));
        request.cancellationToken().throwIfCancellationRequested();
        return new SnapshotSource(result, mapper.map(member));
    }

    private Projection project(RetirementPlan isolated, SocialSecurityJointMortalityScenario member) {
        request.cancellationToken().throwIfCancellationRequested();
        if (engine == null) engine = new ProjectionEngine();
        starts++;
        report();
        var context = context(member);
        var projection = structuredFunding ? IntegratedSocialSecurityStrategyEvaluator.projectFinancial(engine, isolated, context)
                : engine.project(isolated, context);
        completed++;
        rows += projection.size();
        report();
        request.cancellationToken().throwIfCancellationRequested();
        return projection;
    }

    private ProjectionEvaluationContext context(SocialSecurityJointMortalityScenario member) {
        return ProjectionEvaluationContext.withSocialSecurityStrategy(request.strategy(), mapper.map(member))
                .withExactEndingYear(secondDeathYear(member) - 1);
    }

    void scenarioStarted() { scenarios++; report(); }
    void scenarioCompleted() { outcomes++; report(); }
    LongevityContinuationWork work() {
        return new LongevityContinuationWork(positive, carriers, successes, failures, 0, fallback,
                starts, completed, scenarios, outcomes, reused, rows);
    }
    private void report() { observer.accept(work()); }
}
