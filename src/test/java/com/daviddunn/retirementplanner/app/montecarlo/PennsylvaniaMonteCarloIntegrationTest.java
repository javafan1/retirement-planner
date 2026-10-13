package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PennsylvaniaMonteCarloIntegrationTest {
    public static RetirementPlan plan(StateTaxJurisdiction state) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        for (var person : plan.getHousehold().peopleByOwner().values()) {
            for (var income : List.copyOf(person.getIncomeSources())) {
                if (income instanceof Pension) person.removeIncomeSource(income);
            }
        }
        for (var account : List.copyOf(plan.getAccountPortfolio().getAccounts())) {
            if (account.getType() == AccountType.BROKERAGE) {
                plan.getAccountPortfolio().replaceAccount(account, AccountFactory.create(AccountType.CHECKING,
                        account.getName(), account.getOwnership(), new BigDecimal("20000000")));
            }
        }
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
        return plan;
    }

    public static MonteCarloMortalityRequest mortalityRequest(RetirementPlan plan, MonteCarloSettings settings) {
        return MonteCarloWorldGeneratorTest.request(plan, settings);
    }

    @Test
    void fixedAndLongevitySeededWorldsRepeatAndAlwaysUseCommonCalculatedPaTax() {
        var plan = plan(StateTaxJurisdiction.PA);
        var checked = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan scenario,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                var result = super.projectWithOutcome(scenario, context, path);
                for (var year : result.completedYears()) {
                    assertEquals(StateTaxJurisdiction.PA, year.getStateTaxJurisdiction());
                    assertEquals(0, year.getStateIncomeTax().signum());
                    assertEquals(StateTaxCalculationStatus.CALCULATED, year.stateTaxCalculation().orElseThrow().calculationStatus());
                    checked.incrementAndGet();
                }
                return result;
            }
        };
        var analyzer = new MonteCarloAnalyzer(engine);
        var settings = MonteCarloSettings.forPlan(plan, 25, 417, new BigDecimal("0.12"));
        var fixed = analyzer.analyze(plan, settings);
        var request = mortalityRequest(plan, settings);
        var longevity = analyzer.analyzeMortality(plan, request);
        assertEquals(25, fixed.completedCount());
        assertEquals(25, longevity.completedCount());
        assertEquals(fixed.outcomes(), analyzer.analyze(plan, settings).outcomes());
        assertEquals(longevity.outcomes(), analyzer.analyzeMortality(plan, request).outcomes());
        assertTrue(checked.get() > 1000);
    }

    @Test
    void singlePersonFixedWorldsAlsoCalculateWithoutInventingSpouse() {
        var plan = PennsylvaniaProductionProjectionTest.plan(false, StateTaxJurisdiction.PA);
        var settings = MonteCarloSettings.forPlan(plan, 20, 417, new BigDecimal("0.12"));
        var analyzer = new MonteCarloAnalyzer();
        var result = analyzer.analyze(plan, settings);
        assertEquals(20, result.completedCount());
        assertEquals(result.outcomes(), analyzer.analyze(plan, settings).outcomes());
        assertFalse(plan.getHousehold().hasSpouse());
    }

    @Test
    void unsupportedReferenceAbortsBothModesWithoutReturningStatistics() {
        var plan = plan(StateTaxJurisdiction.PA);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Unknown plan", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO));
        var settings = MonteCarloSettings.forPlan(plan, 25, 417, new BigDecimal("0.12"));
        for (boolean longevity : List.of(false, true)) {
            var failure = assertThrows(MonteCarloExecutionException.class, () -> {
                if (longevity) new MonteCarloAnalyzer().analyzeMortality(plan, mortalityRequest(plan, settings));
                else new MonteCarloAnalyzer().analyze(plan, settings);
            });
            var unavailable = failure.stateTaxUnavailable().orElseThrow();
            assertEquals(StateTaxJurisdiction.PA, unavailable.result().jurisdiction());
            assertTrue(unavailable.result().estimatedIncomeTax().isEmpty());
            assertTrue(unavailable.result().unavailableReasons().contains(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION));
        }
    }

    @Test
    void unsupportedExtendedWorldAbortsSimulationRatherThanEnteringPercentiles() {
        var plan = plan(StateTaxJurisdiction.PA);
        MonteCarloMortalityExecutionTest.configure(plan, LocalDate.of(2027, 1, 1), 1, 67);
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.PA)));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Future unknown plan", AccountOwnership.PRIMARY,
                LocalDate.of(2029, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO));
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        var world = MonteCarloMortalityExecutionTest.world(0, 2035, 2036, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var failure = assertThrows(MonteCarloExecutionException.class, () -> new MonteCarloAnalyzer().analyzeMortality(
                plan, mortalityRequest(plan, settings), index -> world,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none()));
        assertEquals(MonteCarloExecutionException.Phase.SIMULATION, failure.phase());
        assertEquals(0, failure.scenarioIndex().orElseThrow());
        assertEquals(2029, failure.stateTaxUnavailable().orElseThrow().taxYear());
    }

    @Test
    void deceasedOwnerFundingWithoutSuccessorFactsIsUnavailableNotAStatisticalOutcome() {
        var plan = plan(StateTaxJurisdiction.PA);
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            if (account.getType() == AccountType.CHECKING) account.setCurrentBalance(new BigDecimal("1000000"));
        }
        var settings = MonteCarloSettings.forPlan(plan, 500, 417, new BigDecimal("0.12"));
        var failure = assertThrows(MonteCarloExecutionException.class, () -> new MonteCarloAnalyzer()
                .analyzeMortality(plan, mortalityRequest(plan, settings)));
        assertEquals(MonteCarloExecutionException.Phase.SIMULATION, failure.phase());
        assertEquals(29, failure.scenarioIndex().orElseThrow());
        assertEquals(2061, failure.stateTaxUnavailable().orElseThrow().taxYear());
        assertTrue(failure.stateTaxUnavailable().orElseThrow().result().unavailableReasons()
                .contains(StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION));
    }
}
