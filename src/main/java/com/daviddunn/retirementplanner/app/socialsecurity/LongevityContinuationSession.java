package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityScenarioContinuationPlanner.*;

/** One frozen strategy run. No account graph or full projection escapes a carrier attempt. */
final class LongevityContinuationSession {
    private final RetirementPlan plan;
    private final LongevityWeightedIntegratedStrategyRequest request;
    private final Consumer<LongevityContinuationWork> observer;
    private final ProjectionEngine engine;
    private final RetirementPlanScenarioCopyService copier = new RetirementPlanScenarioCopyService();
    private final HouseholdLifetimeScenarioMapper mapper = new HouseholdLifetimeScenarioMapper();
    private final EstateAtSecondDeathCalculator estate = new EstateAtSecondDeathCalculator();
    private final SocialSecurityProjectionIncomeProvider socialSecurity = new SocialSecurityProjectionIncomeProvider();
    private final Map<Path, Group> groups;
    private final Set<Path> attempted = new HashSet<>();
    private final Map<Path, Map<Integer, EstateAtSecondDeathSnapshot>> snapshots = new HashMap<>();
    private final int firstYear;
    private final int configuredLast;
    private final long positive;
    private long carriers, successes, failures, early, fallback, starts, completed, scenarios, outcomes, reused, rows;

    LongevityContinuationSession(RetirementPlan plan, LongevityWeightedIntegratedStrategyRequest request,
            int positive, Consumer<LongevityContinuationWork> observer) {
        this(plan, request, positive, observer, new ProjectionEngine());
    }

    LongevityContinuationSession(RetirementPlan plan, LongevityWeightedIntegratedStrategyRequest request,
            int positive, Consumer<LongevityContinuationWork> observer, ProjectionEngine engine) {
        this.engine = Objects.requireNonNull(engine);
        this.plan = plan;
        this.request = request;
        this.observer = observer;
        this.positive = positive;
        firstYear = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        configuredLast = firstYear + plan.getPlanningAssumptions().getProjectionLengthYears() - 1;
        groups = new LongevityScenarioContinuationPlanner().plan(request.longevityScenarios().scenarios(),
                configuredLast, request.cancellationToken());
        report();
    }

    EstateAtSecondDeathSnapshot snapshot(SocialSecurityJointMortalityScenario member) {
        request.cancellationToken().throwIfCancellationRequested();
        int death = secondDeathYear(member);
        if (death <= configuredLast) {
            return independent(member, true);
        }
        // Preserve the member's own finite-horizon request validation, including election omission.
        // A validation failure is replayed through the independent engine for the original exception contract.
        try {
            socialSecurity.validateForContinuation(plan, firstYear, death - 1, context(member));
        } catch (AnalysisCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException unproven) {
            return independent(member, false);
        }
        Path path = path(member);
        if (attempted.add(path)) attempt(path, groups.get(path));
        request.cancellationToken().throwIfCancellationRequested();
        var available = snapshots.get(path);
        if (available == null) return independent(member, false);
        var result = available.get(death);
        if (result == null) return independent(member, false);
        reused++;
        report();
        return result;
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

    private EstateAtSecondDeathSnapshot independent(SocialSecurityJointMortalityScenario member, boolean earlyHorizon) {
        request.cancellationToken().throwIfCancellationRequested();
        if (earlyHorizon) early++; else fallback++;
        report();
        var isolated = copier.copy(plan);
        var projection = project(isolated, member);
        var result = estate.calculate(isolated, projection, LocalDate.of(secondDeathYear(member), 1, 1));
        request.cancellationToken().throwIfCancellationRequested();
        return result;
    }

    private Projection project(RetirementPlan isolated, SocialSecurityJointMortalityScenario member) {
        request.cancellationToken().throwIfCancellationRequested();
        starts++;
        report();
        var projection = engine.project(isolated, context(member));
        completed++;
        rows += projection.size();
        report();
        request.cancellationToken().throwIfCancellationRequested();
        return projection;
    }

    private ProjectionEvaluationContext context(SocialSecurityJointMortalityScenario member) {
        return ProjectionEvaluationContext.withSocialSecurityStrategy(request.strategy(), mapper.map(member))
                .withEndingYear(secondDeathYear(member) - 1);
    }

    void scenarioStarted() { scenarios++; report(); }
    void scenarioCompleted() { outcomes++; report(); }
    LongevityContinuationWork work() {
        return new LongevityContinuationWork(positive, carriers, successes, failures, early, fallback,
                starts, completed, scenarios, outcomes, reused, rows);
    }
    private void report() { observer.accept(work()); }
}
