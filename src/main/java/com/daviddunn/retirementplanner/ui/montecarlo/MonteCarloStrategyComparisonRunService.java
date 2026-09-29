package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;

/** Capture on FX, execute on worker. Reconstructs the baseline using BaselineProjectionService's fields. */
public final class MonteCarloStrategyComparisonRunService {
    public static final String MISSING_BASELINE = "Save a baseline before running Monte Carlo Strategy Comparison.";

    public record Prepared(MonteCarloStrategyComparisonRequest request, MonteCarloMode mode, String details) { }

    public static boolean available(RetirementPlan plan) {
        return plan != null && plan.getBaseline() != null;
    }

    public static Prepared capture(RetirementPlan current, MonteCarloSettings settings, MonteCarloMode mode,
            String primaryAdjustment, String spouseAdjustment, String survivorA, String survivorB) {
        if (!available(current)) {
            throw new IllegalArgumentException(MISSING_BASELINE);
        }
        var baseline = current.getBaseline();
        var snapshot = baseline.getSnapshot();
        var copier = new RetirementPlanScenarioCopyService();
        var a = copier.copy(current);
        var b = copier.copy(new RetirementPlan(snapshot.getHousehold(), snapshot.getAccountPortfolio(),
                snapshot.getPlanningAssumptions(), snapshot.getRothConversionRequest(), snapshot.getNonInvestableAssets()));
        if (!ProjectionReadiness.isReady(a) || !ProjectionReadiness.isReady(b)) {
            throw new IllegalArgumentException("Both strategies require birth dates and a projection start date.");
        }
        var start = a.getPlanningAssumptions().getProjectionStartDate();
        if (!start.equals(b.getPlanningAssumptions().getProjectionStartDate())) {
            throw new IllegalArgumentException("Current Plan and Saved Baseline must have the same projection start date.");
        }
        for (var people : java.util.List.of(
                java.util.List.of(a.getHousehold().getPrimaryPerson(), b.getHousehold().getPrimaryPerson()),
                java.util.List.of(a.getHousehold().getSpouse(), b.getHousehold().getSpouse()))) {
            if (!Objects.equals(people.get(0).getBirthDate(), people.get(1).getBirthDate())
                    || people.get(0).getMortalityCategory() != people.get(1).getMortalityCategory()) {
                throw new IllegalArgumentException("Both strategies must describe the same birth dates and mortality categories.");
            }
        }
        MonteCarloStrategyComparisonRequest.Assumptions assumptions;
        String lifetime;
        if (mode == MonteCarloMode.LONGEVITY_ADJUSTED) {
            int ageA = MonteCarloMortalityPresentation.survivorClaimingAge(survivorA);
            int ageB = MonteCarloMortalityPresentation.survivorClaimingAge(survivorB);
            applySurvivorAge(a, ageA);
            applySurvivorAge(b, ageB);
            var session = MonteCarloMortalityPresentation.settings(a, primaryAdjustment, spouseAdjustment);
            var mortality = new MonteCarloMortalityRequest(a, settings, session, ageA);
            assumptions = new MonteCarloStrategyComparisonRequest.Longevity(mortality);
            var longevity = mortality.longevityAssumptions();
            lifetime = "Mortality table: " + longevity.tableMetadata().displayName() + " / "
                    + longevity.tableMetadata().sourceVersion() + "\nCategories: " + longevity.primaryCategory()
                    + " / " + longevity.spouseCategory() + "; conditioning date: " + start
                    + "\nLongevity factors: " + primaryAdjustment + " / " + spouseAdjustment
                    + "; survivor SS claiming age: Current Plan " + ageA + ", Saved Baseline " + ageB;
        } else {
            var deaths = lifetime(a);
            if (!deaths.equals(lifetime(b))) {
                throw new IllegalArgumentException("Fixed comparison requires matching configured death scenarios. Use Longevity-Adjusted mode for shared sampled lifetimes.");
            }
            int last = start.getYear() + a.getPlanningAssumptions().getProjectionLengthYears() - 1;
            assumptions = new MonteCarloStrategyComparisonRequest.Fixed(settings, start, last, deaths);
            lifetime = "Common fixed horizon: " + start + " through " + last + " (Current Plan horizon)."
                    + "\nSaved Baseline configured length: " + b.getPlanningAssumptions().getProjectionLengthYears()
                    + " years. Mortality table, conditioning and longevity factors: not applied in Fixed Lifespan mode."
                    + "\nSurvivor SS claiming age: Current Plan " + survivorLabel(a) + ", Saved Baseline " + survivorLabel(b);
        }
        var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("Current Plan", a),
                new MonteCarloStrategyCandidate("Saved Baseline", b), assumptions);
        String details = "Strategy A: Current Plan\nStrategy B: Saved Baseline — " + baseline.getDescription()
                + " (saved " + baseline.getSavedAt() + ")\nMode: " + mode + "\nSimulations: " + settings.simulationCount()
                + "; seed: " + settings.seed() + "\nReturn model: independent lognormal gross returns"
                + "; expected return: " + percent(settings.expectedReturn()) + "; volatility: " + percent(settings.returnVolatility())
                + "\nGeneral inflation: " + settings.inflation().map(i -> "Stochastic; mean " + percent(i.expectedInflationRate())
                + "; volatility " + percent(i.inflationVolatility()) + "; floor " + percent(i.minimumInflationRate()))
                .orElse("Deterministic; Current Plan " + percent(a.getPlanningAssumptions().getGeneralInflationRate())
                        + ", Saved Baseline " + percent(b.getPlanningAssumptions().getGeneralInflationRate()))
                + "\nHealthcare inflation: Current Plan " + percent(a.getPlanningAssumptions().getHealthcareInflationRate())
                + ", Saved Baseline " + percent(b.getPlanningAssumptions().getHealthcareInflationRate())
                + "\n" + lifetime + "\nSettings are session-only. Plan and baseline elections are unchanged."
                + "\nAfter-Tax Estate excludes non-investable assets. Lifetime Modeled Income Taxes includes federal and Michigan income taxes.";
        return new Prepared(request, mode, details);
    }

    private static String percent(java.math.BigDecimal value) {
        return value.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    private static String survivorLabel(RetirementPlan plan) {
        var age = plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge();
        return age == null ? "not configured" : age.toString();
    }

    private static HouseholdLifetimeScenario lifetime(RetirementPlan plan) {
        var d = plan.getPlanningAssumptions().getDeathScenarioAssumptions();
        return new HouseholdLifetimeScenario(d.getDeathScenario() == DeathScenario.PRIMARY_DIES
                ? Optional.of(Year.of(d.getDeathYear())) : Optional.empty(),
                d.getDeathScenario() == DeathScenario.SPOUSE_DIES ? Optional.of(Year.of(d.getDeathYear())) : Optional.empty());
    }

    private static void applySurvivorAge(RetirementPlan plan, int age) {
        var p = plan.getPlanningAssumptions();
        var d = p.getDeathScenarioAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(p.getEconomicAssumptions(), p.getTaxAssumptions(),
                p.getWithdrawalAssumptions(), new DeathScenarioAssumptions(d.getDeathScenario(), d.getDeathYear(), age),
                p.getProjectionLengthYears(), p.getProjectionStartDate()));
    }

    public MonteCarloStrategyComparisonRun run(Prepared prepared, AnalysisProgressListener progress,
            AnalysisCancellationToken cancellation) {
        long start = System.nanoTime();
        var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(prepared.request(), progress, cancellation);
        cancellation.throwIfCancellationRequested();
        return new MonteCarloStrategyComparisonRun(result, prepared.mode(), prepared.details(), System.nanoTime() - start);
    }
}
