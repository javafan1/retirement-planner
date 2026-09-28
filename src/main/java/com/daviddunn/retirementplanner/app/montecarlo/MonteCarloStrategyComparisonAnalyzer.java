package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult.TerminalOutcome;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.PersonMortalityCategories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.function.IntFunction;

/** Sequential common-random-number execution. No random generator is reachable from evaluation. */
public final class MonteCarloStrategyComparisonAnalyzer {
    private final ProjectionEngine engine;

    public MonteCarloStrategyComparisonAnalyzer() {
        this(new ProjectionEngine());
    }

    public MonteCarloStrategyComparisonAnalyzer(ProjectionEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    public MonteCarloStrategyComparisonResult analyzeComparison(MonteCarloStrategyComparisonRequest request) {
        return analyzeComparison(request, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    public MonteCarloStrategyComparisonResult analyzeComparison(MonteCarloStrategyComparisonRequest request,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        return run(request, null, progress, cancellation);
    }

    /** Caller owns reproducibility of supplied worlds. Each index is requested exactly once. */
    public MonteCarloStrategyComparisonResult analyzeComparison(MonteCarloStrategyComparisonRequest request,
            IntFunction<MonteCarloComparisonWorld> worlds,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        return run(request, Objects.requireNonNull(worlds), progress, cancellation);
    }

    private MonteCarloStrategyComparisonResult run(MonteCarloStrategyComparisonRequest request,
            IntFunction<MonteCarloComparisonWorld> supplied,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        var a = request.strategyA().copyForRun();
        var b = request.strategyB().copyForRun();
        validate(a, request.assumptions());
        validate(b, request.assumptions());
        validateSamePeople(a, b);
        var worlds = supplied == null ? request.worldSource() : supplied;
        int count = request.assumptions().settings().simulationCount();
        var pairs = new ArrayList<MonteCarloPairedOutcome>(count);
        report(progress, 0, count);
        int interval = Math.max(1, (count + 99) / 100);
        for (int index = 0; index < count; index++) {
            cancellation.throwIfCancellationRequested();
            MonteCarloComparisonWorld world;
            try {
                world = Objects.requireNonNull(worlds.apply(index), "World is required.");
                validateWorld(world, index, request.assumptions());
            } catch (AnalysisCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new ExecutionException(index, Stage.WORLD, request, exception);
            }
            cancellation.throwIfCancellationRequested();
            var outcomeA = evaluateSide(a, world, request, Stage.STRATEGY_A);
            cancellation.throwIfCancellationRequested();
            var outcomeB = evaluateSide(b, world, request, Stage.STRATEGY_B);
            cancellation.throwIfCancellationRequested();
            pairs.add(new MonteCarloPairedOutcome(index, world.lifetimeScenario(), outcomeA, outcomeB));
            if ((index + 1) % interval == 0 || index + 1 == count) {
                report(progress, index + 1, count);
            }
        }
        cancellation.throwIfCancellationRequested();
        return new MonteCarloStrategyComparisonResult(request, supplied == null
                ? MonteCarloStrategyComparisonResult.WorldSource.GENERATED
                : MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED, pairs);
    }

    private MonteCarloStrategyOutcome evaluateSide(RetirementPlan plan, MonteCarloComparisonWorld world,
            MonteCarloStrategyComparisonRequest request, Stage stage) {
        try {
            return evaluate(plan, world, request.assumptions());
        } catch (AnalysisCancelledException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExecutionException(world.scenarioIndex(), stage, request, exception);
        }
    }

    /** Reduces engine output immediately. Neither the source plan nor world is changed. */
    private MonteCarloStrategyOutcome evaluate(RetirementPlan plan, MonteCarloComparisonWorld world,
            MonteCarloStrategyComparisonRequest.Assumptions assumptions) {
        int last = endingYear(world, assumptions);
        var context = ProjectionEvaluationContext.empty().withExactEndingYear(last);
        if (assumptions instanceof MonteCarloStrategyComparisonRequest.Longevity) {
            var deathDate = LocalDate.of(last + 1, 1, 1);
            var estate = new EstateAtSecondDeathCalculator();
            estate.validateCoverageStart(plan, deathDate);
            if (deathDate.equals(assumptions.start())) {
                var opening = estate.calculateOpening(plan, deathDate);
                var nonInvestable = new NonInvestableAssetProjectionService().project(
                        plan.getNonInvestableAssets(), deathDate.getYear(), deathDate.getYear()).getFirst().getTotalValue();
                return new MonteCarloStrategyOutcome(Map.of(), Optional.of(new TerminalOutcome(
                        opening.balanceDate(), opening.nominalInvestableAssets(),
                        opening.nominalInvestableAssets().add(nonInvestable), opening.nominalAfterTaxEstate(),
                        BigDecimal.ZERO)), Optional.empty());
            }
            context = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario())
                    .withExactEndingYear(last).withSurvivorClaimingAge(
                            plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        }
        if (world.inflationPath().isPresent()) {
            context = context.withInflationPath(world.inflationPath().orElseThrow());
        }
        var execution = engine.projectWithOutcome(plan, context, world.economicPath());
        var annual = new TreeMap<Integer, BigDecimal>();
        int expected = assumptions.start().getYear();
        for (var year : execution.completedYears()) {
            if (year.getCalendarYear() != expected++) {
                throw new IllegalArgumentException("Projection years must be a contiguous completed prefix.");
            }
            annual.put(year.getCalendarYear(), year.getEndingInvestableAssets());
        }
        if (execution instanceof ProjectionExecutionResult.InsufficientFunds failed) {
            if (failed.fundingFailure().calendarYear() != expected || expected > last) {
                throw new IllegalArgumentException("Funding failure must follow the completed prefix within the horizon.");
            }
            return new MonteCarloStrategyOutcome(annual, Optional.empty(), Optional.of(failed.fundingFailure()));
        }
        if (expected != last + 1) {
            throw new IllegalArgumentException("Completed projection does not match the comparison horizon.");
        }
        var projection = ((ProjectionExecutionResult.Completed) execution).projection();
        var metrics = new ProjectionMetricsCalculator().calculate(plan, projection);
        return new MonteCarloStrategyOutcome(annual, Optional.of(new TerminalOutcome(
                LocalDate.of(last, 12, 31), metrics.endingInvestableAssets(), metrics.endingNetWorth(),
                metrics.afterTaxEstate(), metrics.totalTaxes())), Optional.empty());
    }

    private static int endingYear(MonteCarloComparisonWorld world,
            MonteCarloStrategyComparisonRequest.Assumptions assumptions) {
        if (assumptions instanceof MonteCarloStrategyComparisonRequest.Fixed fixed) {
            return fixed.endingYear();
        }
        return Math.max(world.lifetimeScenario().primaryDeathYear().orElseThrow().getValue(),
                world.lifetimeScenario().spouseDeathYear().orElseThrow().getValue()) - 1;
    }

    private static void validateWorld(MonteCarloComparisonWorld world, int index,
            MonteCarloStrategyComparisonRequest.Assumptions assumptions) {
        if (world.scenarioIndex() != index) {
            throw new IllegalArgumentException("World scenario index does not match requested index.");
        }
        if (assumptions instanceof MonteCarloStrategyComparisonRequest.Fixed fixed
                && !world.lifetimeScenario().equals(fixed.lifetimeScenario())) {
            throw new IllegalArgumentException("Fixed world death timing must match the comparison request.");
        }
        int last = endingYear(world, assumptions);
        if (assumptions instanceof MonteCarloStrategyComparisonRequest.Longevity
                && LocalDate.of(last + 1, 1, 1).isBefore(assumptions.start())) {
            throw new IllegalArgumentException("Second death precedes available opening balances.");
        }
        world.economicPath().requireCoverage(assumptions.start().getYear(), last);
        world.inflationPath().ifPresent(path -> path.requireCoverage(assumptions.start().getYear(), last));
    }

    private static void validate(RetirementPlan plan, MonteCarloStrategyComparisonRequest.Assumptions assumptions) {
        if (!plan.getPlanningAssumptions().getProjectionStartDate().equals(assumptions.start())) {
            throw new IllegalArgumentException("Each strategy must match the comparison start date.");
        }
        var death = plan.getPlanningAssumptions().getDeathScenarioAssumptions();
        if (assumptions instanceof MonteCarloStrategyComparisonRequest.Fixed fixed) {
            var primary = death.getDeathScenario() == DeathScenario.PRIMARY_DIES
                    ? Optional.of(Year.of(death.getDeathYear())) : Optional.<Year>empty();
            var spouse = death.getDeathScenario() == DeathScenario.SPOUSE_DIES
                    ? Optional.of(Year.of(death.getDeathYear())) : Optional.<Year>empty();
            if (!new HouseholdLifetimeScenario(primary, spouse).equals(fixed.lifetimeScenario())) {
                throw new IllegalArgumentException("Configured death timing must match the fixed comparison request.");
            }
        } else {
            var mortality = ((MonteCarloStrategyComparisonRequest.Longevity) assumptions).mortality();
            if (!new SocialSecurityProjectionIncomeProvider().supportsAdvancedPath(plan)) {
                throw new IllegalArgumentException("Longevity comparison requires the advanced two-person Social Security path.");
            }
            var household = plan.getHousehold();
            var categories = PersonMortalityCategories.from(household);
            if (!household.getPrimaryPerson().getBirthDate().equals(mortality.primaryBirthDate())
                    || !household.getSpouse().getBirthDate().equals(mortality.spouseBirthDate())
                    || categories.primary() != mortality.longevityAssumptions().primaryCategory()
                    || categories.spouse() != mortality.longevityAssumptions().spouseCategory()) {
                throw new IllegalArgumentException("Strategy demographics must match mortality generation inputs.");
            }
            if (death.getSurvivorClaimingAge() == null) {
                throw new IllegalArgumentException("Each longevity strategy requires its own survivor claiming age.");
            }
            com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy
                    .validateClaimingAge(death.getSurvivorClaimingAge());
        }
    }

    private static void validateSamePeople(RetirementPlan a, RetirementPlan b) {
        var ah = a.getHousehold();
        var bh = b.getHousehold();
        if (!samePerson(ah.getPrimaryPerson(), bh.getPrimaryPerson())
                || !samePerson(ah.getSpouse(), bh.getSpouse())) {
            throw new IllegalArgumentException("Strategies must describe the same household demographics.");
        }
    }

    private static boolean samePerson(Person a, Person b) {
        return a == null || b == null ? a == b
                : Objects.equals(a.getBirthDate(), b.getBirthDate()) && a.getMortalityCategory() == b.getMortalityCategory();
    }

    private static void report(AnalysisProgressListener progress, int completed, int total) {
        progress.onProgress(new AnalysisProgress(AnalysisPhase.MONTE_CARLO_SIMULATIONS, completed, total));
    }

    public enum Stage { WORLD, STRATEGY_A, STRATEGY_B }

    /** Software/configuration errors are never financial observations. Scenario indexes are zero-based. */
    public static final class ExecutionException extends RuntimeException {
        private final int scenarioIndex;
        private final Stage stage;

        private ExecutionException(int index, Stage stage, MonteCarloStrategyComparisonRequest request,
                                   RuntimeException cause) {
            super("Paired Monte Carlo scenario " + index + ", " + stage + ", seed "
                    + request.assumptions().settings().seed() + " failed", cause);
            scenarioIndex = index;
            this.stage = stage;
        }

        public int scenarioIndex() { return scenarioIndex; }
        public Stage stage() { return stage; }
    }
}
