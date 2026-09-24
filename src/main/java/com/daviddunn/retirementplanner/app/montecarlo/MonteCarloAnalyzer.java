package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.projection.SocialSecurityProjectionIncomeProvider;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.PersonMortalityCategories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.IntFunction;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult.Reference;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult.RunOutcome;

/**
 * Sequential, headless full-engine execution. One isolated plan and one engine per analysis.
 * Projections are reduced immediately; unexpected errors abort without publishing partial results.
 */
public final class MonteCarloAnalyzer {

    private final ProjectionEngine engine;
    private final ProjectionMetricsCalculator metrics = new ProjectionMetricsCalculator();

    public MonteCarloAnalyzer() {
        this(new ProjectionEngine());
    }

    public MonteCarloAnalyzer(ProjectionEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    public MonteCarloMortalityAnalysisResult analyzeMortality(
            RetirementPlan source,
            MonteCarloMortalityRequest request) {
        return analyzeMortality(source, request, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    public MonteCarloMortalityAnalysisResult analyzeMortality(
            RetirementPlan source,
            MonteCarloMortalityRequest request,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        return analyzeMortality(source, request, null,
                MonteCarloScenarioGenerator.MODEL_VERSION,
                "HOUSEHOLD_MORTALITY_V" + MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1, progress, cancellation);
    }

    /** Explicit indexed worlds support direct-engine oracles without changing fixed-mode APIs. */
    public MonteCarloMortalityAnalysisResult analyzeMortality(
            RetirementPlan source,
            MonteCarloMortalityRequest request,
            IntFunction<MonteCarloWorld> worlds,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        return analyzeMortality(source, request, Objects.requireNonNull(worlds),
                "CALLER_SUPPLIED_WORLDS", "CALLER_SUPPLIED_WORLDS", progress, cancellation);
    }

    private MonteCarloMortalityAnalysisResult analyzeMortality(
            RetirementPlan source,
            MonteCarloMortalityRequest request,
            IntFunction<MonteCarloWorld> suppliedWorlds,
            String returnModel,
            String mortalityModel,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(cancellation);
        cancellation.throwIfCancellationRequested();
        var plan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(source));
        validateMortalityPreparation(plan, request);
        IntFunction<MonteCarloWorld> worlds = suppliedWorlds != null
                ? suppliedWorlds : new MonteCarloWorldGenerator(request)::generate;
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        var settings = request.settings();
        var estate = new EstateAtSecondDeathCalculator();
        var outcomes = new ArrayList<MonteCarloMortalityAnalysisResult.WorldOutcome>(settings.simulationCount());
        cancellation.throwIfCancellationRequested();
        report(progress, 0, settings.simulationCount());
        int progressInterval = Math.max(1, (settings.simulationCount() + 99) / 100);
        for (int index = 0; index < settings.simulationCount(); index++) {
            cancellation.throwIfCancellationRequested();
            try {
                var world = Objects.requireNonNull(worlds.apply(index), "World is required.");
                if (world.scenarioIndex() != index) {
                    throw new IllegalArgumentException("World scenario index does not match requested index.");
                }
                var lifetime = world.lifetimeScenario();
                int secondDeathYear = Math.max(lifetime.primaryDeathYear().orElseThrow().getValue(),
                        lifetime.spouseDeathYear().orElseThrow().getValue());
                var deathDate = LocalDate.of(secondDeathYear, 1, 1);
                estate.validateCoverageStart(plan, deathDate);
                if (deathDate.equals(start)) {
                    var opening = estate.calculateOpening(plan, deathDate);
                    var nonInvestable = new NonInvestableAssetProjectionService()
                            .project(plan.getNonInvestableAssets(), start.getYear(), start.getYear())
                            .getFirst().getTotalValue();
                    outcomes.add(new MonteCarloMortalityAnalysisResult.WorldOutcome(index, lifetime, Map.of(),
                            Optional.of(new MonteCarloMortalityAnalysisResult.TerminalOutcome(
                                    opening.balanceDate(), opening.nominalInvestableAssets(),
                                    opening.nominalInvestableAssets().add(nonInvestable),
                                    opening.nominalAfterTaxEstate(), BigDecimal.ZERO)), Optional.empty()));
                } else {
                    int last = secondDeathYear - 1;
                    var context = ProjectionEvaluationContext.withLifetimeScenario(lifetime).withExactEndingYear(last);
                    var execution = engine.projectWithOutcome(plan, context, world.economicPath());
                    if (execution instanceof ProjectionExecutionResult.InsufficientFunds failed) {
                        outcomes.add(new MonteCarloMortalityAnalysisResult.WorldOutcome(index, lifetime,
                                annual(failed.completedYears()), Optional.empty(), Optional.of(failed.fundingFailure())));
                    } else {
                        var projection = ((ProjectionExecutionResult.Completed) execution).projection();
                        validateYears(projection, start.getYear(), last);
                        var totals = metrics.calculate(plan, projection);
                        outcomes.add(new MonteCarloMortalityAnalysisResult.WorldOutcome(index, lifetime,
                                annual(projection.getYears()),
                                Optional.of(new MonteCarloMortalityAnalysisResult.TerminalOutcome(
                                        deathDate.minusDays(1), totals.endingInvestableAssets(), totals.endingNetWorth(),
                                        totals.afterTaxEstate(), totals.totalTaxes())), Optional.empty()));
                    }
                }
            } catch (AnalysisCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new MonteCarloExecutionException(MonteCarloExecutionException.Phase.SIMULATION,
                        OptionalInt.of(index), settings.seed(), returnModel, exception);
            }
            cancellation.throwIfCancellationRequested();
            if ((index + 1) % progressInterval == 0 || index + 1 == settings.simulationCount()) {
                report(progress, index + 1, settings.simulationCount());
            }
        }
        cancellation.throwIfCancellationRequested();
        var result = new MonteCarloMortalityAnalysisResult(request, returnModel, mortalityModel, outcomes);
        cancellation.throwIfCancellationRequested();
        return result;
    }

    private static void validateMortalityPreparation(RetirementPlan plan, MonteCarloMortalityRequest request) {
        if (!new SocialSecurityProjectionIncomeProvider().supportsAdvancedPath(plan)) {
            throw new IllegalArgumentException("Mortality execution requires the advanced two-person Social Security "
                    + "path: modern-cohort people with one correctly owned Social Security source each.");
        }
        // The lifetime provider applies this shared persisted policy to either surviving person.
        // Do not use extractCurrentStrategy: its absent-policy representation substitutes age 60.
        if (plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge() == null) {
            throw new IllegalArgumentException("Mortality execution requires a persisted survivor claiming age "
                    + "for either first-death direction; no default survivor election is supplied.");
        }
        var household = plan.getHousehold();
        var categories = PersonMortalityCategories.from(household);
        var assumptions = request.longevityAssumptions();
        if (!request.primaryBirthDate().equals(household.getPrimaryPerson().getBirthDate())
                || !request.spouseBirthDate().equals(household.getSpouse().getBirthDate())
                || categories.primary() != assumptions.primaryCategory()
                || categories.spouse() != assumptions.spouseCategory()
                || !plan.getPlanningAssumptions().getProjectionStartDate().equals(assumptions.mortalityBaseDate())) {
            throw new IllegalArgumentException("Mortality request does not match the plan's people or projection start.");
        }
    }

    public MonteCarloAnalysisResult analyze(RetirementPlan plan, MonteCarloSettings settings) {
        return analyze(plan, settings, null);
    }

    /**
     * Supplied reference must be the caller's valid normal projection for this exact plan revision.
     * Horizon is checked here; provenance cannot be inferred from a bare Projection.
     */
    public MonteCarloAnalysisResult analyze(
            RetirementPlan plan,
            MonteCarloSettings settings,
            Projection reference) {
        return analyze(plan, settings, reference, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    public MonteCarloAnalysisResult analyze(
            RetirementPlan plan,
            MonteCarloSettings settings,
            Projection reference,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        return analyzeWithReferenceOutcome(plan, settings,
                reference == null ? null : new ProjectionExecutionResult.Completed(reference.getYears()),
                progress, cancellation);
    }

    /**
     * Reuses an authoritative reference for the same frozen plan, including an underfunded prefix.
     */
    public MonteCarloAnalysisResult analyzeWithReferenceOutcome(
            RetirementPlan plan,
            MonteCarloSettings settings,
            ProjectionExecutionResult reference,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(settings);
        int first = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int last = Math.addExact(first, plan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        var generator = new MonteCarloScenarioGenerator();
        return analyze(
                plan, settings, reference,
                index -> generator.generate(first, last, settings, index),
                MonteCarloScenarioGenerator.MODEL_VERSION, progress, cancellation);
    }

    /**
     * Explicit paths enable common-random-number comparisons without plan-dependent draws.
     */
    public MonteCarloAnalysisResult analyze(
            RetirementPlan source,
            MonteCarloSettings settings,
            Projection reference,
            IntFunction<ProjectionEconomicPath> paths) {
        return analyze(source, settings, reference, paths,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    public MonteCarloAnalysisResult analyze(
            RetirementPlan source,
            MonteCarloSettings settings,
            Projection reference,
            IntFunction<ProjectionEconomicPath> paths,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        return analyze(source, settings,
                reference == null ? null : new ProjectionExecutionResult.Completed(reference.getYears()),
                paths, "CALLER_SUPPLIED_PATHS", progress, cancellation);
    }

    private MonteCarloAnalysisResult analyze(
            RetirementPlan source,
            MonteCarloSettings settings,
            ProjectionExecutionResult reference,
            IntFunction<ProjectionEconomicPath> paths,
            String returnModel,
            AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(paths);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(cancellation);
        cancellation.throwIfCancellationRequested();
        var plan = new RetirementPlanScenarioCopyService().copy(Objects.requireNonNull(source));
        int first = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int last = Math.addExact(first, plan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        if (reference instanceof ProjectionExecutionResult.Completed completed) {
            validateYears(completed.projection(), first, last);
        }
        Reference deterministic = deterministicReference(plan, reference, settings, returnModel, first, last);
        cancellation.throwIfCancellationRequested();
        report(progress, 0, settings.simulationCount());
        int progressInterval = Math.max(1, (settings.simulationCount() + 99) / 100);
        Map<Integer, List<BigDecimal>> samples = new TreeMap<>();
        for (int year = first; year <= last; year++) {
            samples.put(year, new ArrayList<>(settings.simulationCount()));
        }
        List<RunOutcome> outcomes = new ArrayList<>(settings.simulationCount());
        List<BigDecimal> assets = new ArrayList<>();
        List<BigDecimal> worth = new ArrayList<>();
        List<BigDecimal> estate = new ArrayList<>();
        List<BigDecimal> taxes = new ArrayList<>();
        for (int index = 0; index < settings.simulationCount(); index++) {
            cancellation.throwIfCancellationRequested();
            try {
                var execution = engine.projectWithOutcome(
                        plan, ProjectionEvaluationContext.empty(), paths.apply(index));
                if (execution instanceof ProjectionExecutionResult.InsufficientFunds failed) {
                    addAnnualSamples(samples, failed.completedYears());
                    outcomes.add(new RunOutcome(
                            index, Optional.empty(), Optional.empty(), Optional.of(failed.fundingFailure())));
                } else {
                    var projection = ((ProjectionExecutionResult.Completed) execution).projection();
                    validateYears(projection, first, last);
                    var totals = metrics.calculate(plan, projection);
                    Optional<Integer> nonpositive = projection.getYears().stream()
                            .filter(year -> year.getEndingInvestableAssets().signum() <= 0)
                            .map(ProjectionYear::getCalendarYear)
                            .findFirst();
                    addAnnualSamples(samples, projection.getYears());
                    assets.add(totals.endingInvestableAssets());
                    worth.add(totals.endingNetWorth());
                    estate.add(totals.afterTaxEstate());
                    taxes.add(totals.totalTaxes());
                    outcomes.add(new RunOutcome(index, Optional.of(totals), nonpositive, Optional.empty()));
                }
            } catch (AnalysisCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new MonteCarloExecutionException(
                        MonteCarloExecutionException.Phase.SIMULATION,
                        OptionalInt.of(index), settings.seed(), returnModel, exception);
            }
            cancellation.throwIfCancellationRequested();
            if ((index + 1) % progressInterval == 0 || index + 1 == settings.simulationCount()) {
                report(progress, index + 1, settings.simulationCount());
            }
        }
        cancellation.throwIfCancellationRequested();
        Map<Integer, Optional<MonteCarloPercentiles>> annual = new TreeMap<>();
        samples.forEach((year, values) -> annual.put(year, MonteCarloPercentiles.of(values)));
        var result = new MonteCarloAnalysisResult(
                settings, returnModel, outcomes, annual,
                MonteCarloPercentiles.of(assets), MonteCarloPercentiles.of(worth),
                MonteCarloPercentiles.of(estate), MonteCarloPercentiles.of(taxes), deterministic);
        cancellation.throwIfCancellationRequested();
        return result;
    }

    private static void report(AnalysisProgressListener progress, int completed, int total) {
        progress.onProgress(new AnalysisProgress(AnalysisPhase.MONTE_CARLO_SIMULATIONS, completed, total));
    }

    private Reference deterministicReference(
            RetirementPlan plan,
            ProjectionExecutionResult reference,
            MonteCarloSettings settings,
            String returnModel,
            int first,
            int last) {
        try {
            var execution = reference == null
                    ? engine.projectWithOutcome(plan)
                    : reference;
            if (execution instanceof ProjectionExecutionResult.Completed completed) {
                var projection = completed.projection();
                validateYears(projection, first, last);
                return new Reference(
                        annual(projection.getYears()), Optional.of(metrics.calculate(plan, projection)), Optional.empty());
            }
            var failed = (ProjectionExecutionResult.InsufficientFunds) execution;
            return new Reference(
                    annual(failed.completedYears()), Optional.empty(), Optional.of(failed.fundingFailure()));
        } catch (AnalysisCancelledException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MonteCarloExecutionException(
                    MonteCarloExecutionException.Phase.DETERMINISTIC_REFERENCE,
                    OptionalInt.empty(), settings.seed(), returnModel, exception);
        }
    }

    private static void addAnnualSamples(
            Map<Integer, List<BigDecimal>> samples,
            List<ProjectionYear> years) {
        for (var year : years) {
            samples.get(year.getCalendarYear()).add(year.getEndingInvestableAssets());
        }
    }

    private static Map<Integer, BigDecimal> annual(List<ProjectionYear> years) {
        Map<Integer, BigDecimal> values = new TreeMap<>();
        for (var year : years) {
            values.put(year.getCalendarYear(), year.getEndingInvestableAssets());
        }
        return values;
    }

    private static void validateYears(Projection projection, int first, int last) {
        if (projection.size() != last - first + 1) {
            throw new IllegalArgumentException("Projection horizon does not match request.");
        }
        for (int index = 0; index < projection.size(); index++) {
            if (projection.getYearAt(index).getCalendarYear() != first + index) {
                throw new IllegalArgumentException("Projection years do not match request.");
            }
        }
    }
}
