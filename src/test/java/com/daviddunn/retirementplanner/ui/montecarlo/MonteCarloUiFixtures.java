package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;

import java.math.BigDecimal;
import java.time.LocalDate;

final class MonteCarloUiFixtures {
    static RetirementPlan plan(String balance, int years) {
        var plan = RetirementPlanFactory.createEmptyPlan();
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1980, 1, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1982, 1, 1));
        plan.setPlanningAssumptions(new PlanningAssumptions(
                BigDecimal.ZERO, BigDecimal.ZERO, years, LocalDate.of(2027, 1, 1)));
        plan.getAccountPortfolio().addAccount(new BrokerageAccount(
                "Cash", AccountOwnership.JOINT, new BigDecimal(balance)));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("100")));
        return plan;
    }

    static MonteCarloRun run(String balance) {
        var plan = plan(balance, 3);
        return new MonteCarloRunService().run(plan,
                MonteCarloSettings.forPlan(plan, 3, 417, BigDecimal.ZERO), null,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }
}
