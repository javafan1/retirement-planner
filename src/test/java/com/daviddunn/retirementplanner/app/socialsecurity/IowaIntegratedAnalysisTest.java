package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.state.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IowaIntegratedAnalysisTest {
    static RetirementPlan plan() {
        var plan = Stage4TestPlans.plan();
        for (var person : plan.getHousehold().peopleByOwner().values()) {
            for (var source : List.copyOf(person.getIncomeSources())) {
                if (source instanceof Pension) person.removeIncomeSource(source);
            }
        }
        for (var account : List.copyOf(plan.getAccountPortfolio().getAccounts())) {
            if (account.getType() == AccountType.BROKERAGE) plan.getAccountPortfolio().replaceAccount(account,
                    AccountFactory.create(AccountType.CHECKING, account.getName(), account.getOwnership(), account.getCurrentBalance()));
        }
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.IA)));
        return plan;
    }

    @Test
    void currentAlternativeGridAndWeightedStrategiesUseCommonBoundary() {
        var plan = plan();
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var current = evaluator.evaluate(plan, Stage4TestPlans.strategy(plan));
        assertTrue(current.projection().getYears().stream().allMatch(year -> year.getStateTaxJurisdiction() == StateTaxJurisdiction.IA
                && year.getStateIncomeTax().signum() == 0));
        var grid = new IntegratedRetirementClaimingGridCalculator().calculate(plan);
        assertEquals(81, grid.cells().size());
        assertTrue(grid.cells().stream().allMatch(cell -> cell.integratedResult().isPresent()));
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(plan,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "1")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                        LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(0, BigDecimal.ONE.compareTo(weighted.totalEvaluatedProbability()));
    }

    @Test
    void unknownPensionBaselineCannotProduceAValidRankingOrEstate() {
        var plan = plan();
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Unknown pension", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO));
        var failure = assertThrows(StateTaxUnavailableException.class, () -> new IntegratedSocialSecurityStrategyEvaluator()
                .evaluate(plan, Stage4TestPlans.strategy(plan)));
        assertTrue(failure.result().estimatedIncomeTax().isEmpty());
        assertThrows(RuntimeException.class, () -> new IntegratedRetirementClaimingGridCalculator().calculate(plan));
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(plan,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "1")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weightedFailure = assertThrows(IllegalStateException.class, () -> new LongevityWeightedIntegratedStrategyEvaluator()
                .evaluate(new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                        LocalDate.of(2030, 1, 1), BigDecimal.ZERO)));
        assertInstanceOf(StateTaxUnavailableException.class, weightedFailure.getCause());
        assertTrue(com.daviddunn.retirementplanner.ui.ProjectionFailureMessages.stateTaxUnavailableMessage(weightedFailure)
                .orElseThrow().contains("Iowa"));
    }

    @Test
    void unsupportedClaimingCandidatesRetainExplanationAndNoFinancialMetrics() {
        var plan = plan();
        for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("25000")));
        for (var account : List.copyOf(plan.getAccountPortfolio().getAccounts())) {
            if (account.getType() == AccountType.CHECKING) plan.getAccountPortfolio().replaceAccount(account,
                    new BrokerageAccount(account.getName(), account.getOwnership(), account.getCurrentBalance()));
        }
        var grid = new IntegratedRetirementClaimingGridCalculator().calculate(plan);
        assertTrue(grid.cells().stream().anyMatch(cell -> cell.successful()));
        var unavailable = grid.cells().stream().filter(cell -> cell.failure().isPresent()).toList();
        assertFalse(unavailable.isEmpty());
        assertTrue(unavailable.stream().allMatch(cell -> cell.integratedResult().isEmpty()
                && cell.differencesFromCurrentPlan().isEmpty()
                && cell.failure().orElseThrow().message().contains("State income tax unavailable")));
    }

    @Test
    void singlePersonCurrentAndAlternativeStrategiesPreserveSupportedTaxBoundary() {
        var plan = com.daviddunn.retirementplanner.domain.projection.PennsylvaniaProductionProjectionTest.plan(false, StateTaxJurisdiction.IA);
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var current = evaluator.evaluate(plan, evaluator.extractCurrentStrategy(plan));
        var alternative = evaluator.evaluate(plan,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy.primaryOnly(
                        70, plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(70)));
        for (var result : List.of(current, alternative)) {
            assertTrue(result.projection().getYears().stream().allMatch(year -> year.getStateTaxJurisdiction() == StateTaxJurisdiction.IA
                    && year.stateTaxCalculation().orElseThrow().calculationStatus() == StateTaxCalculationStatus.CALCULATED));
        }
    }
}
