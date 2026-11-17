package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalyzer;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityRequest;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;

import java.util.List;

/**
 * Worker-only adapter: one normal reference and one sequential analysis of a captured plan.
 */
public final class MonteCarloRunService {
    public MonteCarloRun runMortality(RetirementPlan frozenPlan, MonteCarloMortalityRequest request,
                                     AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireAnalyzerSupported(frozenPlan);
        long started = System.nanoTime();
        cancellation.throwIfCancellationRequested();
        com.daviddunn.retirementplanner.app.montecarlo.ExpenseVolatilityAdmission.requireStandaloneSupported(frozenPlan);
        var result = new MonteCarloAnalyzer().analyzeMortality(frozenPlan, request, progress, cancellation);
        cancellation.throwIfCancellationRequested();
        var people = BreakEvenPlanSummary.from(frozenPlan.getHousehold());
        return new MonteCarloRun(new MonteCarloRun.Mortality(result),
                MonteCarloFanModel.from(result, people), people,
                result.firstReportingYear(), result.lastReportingYear().orElse(result.firstReportingYear() - 1),
                false, System.nanoTime() - started).withPlanDetails(planDetails(frozenPlan) + expenseDetails(frozenPlan, result.expenseExecution()));
    }

    public MonteCarloRun run(RetirementPlan frozenPlan, MonteCarloSettings settings, Projection cachedReference,
                             AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireAnalyzerSupported(frozenPlan);
        long started = System.nanoTime();
        cancellation.throwIfCancellationRequested();
        ProjectionEngine engine = new ProjectionEngine();
        com.daviddunn.retirementplanner.app.montecarlo.ExpenseVolatilityAdmission.requireStandaloneSupported(frozenPlan);
        ProjectionExecutionResult reference = cachedReference == null
                ? engine.projectWithOutcome(frozenPlan)
                : new ProjectionExecutionResult.Completed(cachedReference.getYears());
        cancellation.throwIfCancellationRequested();
        var people = BreakEvenPlanSummary.from(frozenPlan.getHousehold());
        int first = frozenPlan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int last = Math.addExact(first, frozenPlan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        var actual = ProjectionChartModel.from(reference.completedYears(), List.of(), people,
                frozenPlan.getAccountPortfolio().getAccounts());
        var context = new ProjectionChartModel(actual.years(), ProjectionChartModel.claims(people, first, last),
                actual.rothPeriods(), actual.rmdPeriods());
        var result = new MonteCarloAnalyzer(engine).analyzeWithReferenceOutcome(
                frozenPlan, settings, reference, progress, cancellation);
        cancellation.throwIfCancellationRequested();
        return new MonteCarloRun(result, MonteCarloFanModel.from(result, context), people, first, last,
                reference instanceof ProjectionExecutionResult.InsufficientFunds, System.nanoTime() - started)
                .withPlanDetails(planDetails(frozenPlan) + expenseDetails(frozenPlan, result.expenseExecution()));
    }

    private static String expenseDetails(RetirementPlan original, com.daviddunn.retirementplanner.app.montecarlo.ExpenseExecution capability) {
        return capability == null ? "" : "\n" + String.join("\n", capability.disclosure(original));
    }

    private static String planDetails(RetirementPlan plan) {
        var p = plan.getPlanningAssumptions();
        var reduction = p.getEconomicAssumptions().getSocialSecurityBenefitReductionPercent();
        String reductionText = reduction.signum() > 0
                ? "\nSocial Security benefit reduction: " + reduction.stripTrailingZeros().toPlainString()
                        + "% beginning " + p.getEconomicAssumptions().getSocialSecurityBenefitReductionEffectiveYear()
                : "";
        String iowa = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.IA
                ? "\nIowa planning estimate: " + String.join(" ", new com.daviddunn.retirementplanner.domain.tax.state.IowaProductionTaxCalculator()
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String arizona = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AZ
                ? "\nArizona planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.ArizonaProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String northCarolina = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.NC
                ? "\nNorth Carolina planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.NorthCarolinaProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String kentucky = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.KY
                ? "\nKentucky planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.KentuckyProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String georgia = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.GA
                ? "\nGeorgia planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.GeorgiaProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String oklahoma = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.OK
                ? "\nOklahoma planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.OklahomaProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String mississippi = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MS
                ? "\nMississippi planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.MississippiProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        String virginia = p.getStateTaxAssumptions().jurisdiction() == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.VA
                ? "\nVirginia planning estimate: " + String.join(" ", com.daviddunn.retirementplanner.domain.tax.state.VirginiaProductionTaxCalculator
                        .planningLimitations(p.getProjectionStartDate().getYear(), p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1))
                : "";
        return "Projection start: " + p.getProjectionStartDate()
                + "\nConfigured planning horizon: " + (p.getProjectionStartDate().getYear() + p.getProjectionLengthYears() - 1)
                + "\nPlan general inflation: " + percent(p.getGeneralInflationRate())
                + "\nHealthcare inflation (deterministic): " + percent(p.getHealthcareInflationRate())
                + "\nConfigured deterministic reference return: " + percent(p.getExpectedAnnualInvestmentReturn())
                + reductionText
                + "\nState of residence: " + p.getStateTaxAssumptions().jurisdiction()
                + "; simplified state estimate; full-year resident" + iowa + arizona + northCarolina + kentucky + georgia + virginia + oklahoma + mississippi;
    }

    private static String percent(java.math.BigDecimal value) {
        return value.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
