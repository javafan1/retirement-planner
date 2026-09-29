package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

final class MonteCarloComparisonFixtures {
    static RetirementPlan plan() {
        var p = MonteCarloMortalityUiFixtures.plan();
        p.setBaseline(ProjectionBaselineFactory.create(p, "Original spending and income"));
        p.getHousehold().addExpense(new Expense("Additional spending", new BigDecimal("6000")));
        p.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Later pension", AccountOwnership.PRIMARY,
                LocalDate.of(2040, 1, 1), null, new BigDecimal("2000"), BigDecimal.ZERO));
        return p;
    }

    static MonteCarloStrategyComparisonRun run() {
        var p = MonteCarloUiFixtures.plan("10000", 3);
        p.setBaseline(ProjectionBaselineFactory.create(p, "Original"));
        var prepared = MonteCarloStrategyComparisonRunService.capture(p, new MonteCarloSettings(3, 417, BigDecimal.ZERO, BigDecimal.ZERO),
                MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67");
        return new MonteCarloStrategyComparisonRunService().run(prepared, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    static MonteCarloPairedMetricSummary metric() {
        return new MonteCarloPairedMetricSummary(4,
                MonteCarloPercentiles.of(List.of(new BigDecimal("-200"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("500"))),
                Optional.of(new BigDecimal("100")), 2, 1, 1);
    }

    static Map<Integer, MonteCarloPairedAnnualResult> annual() {
        return Map.of(2040, new MonteCarloPairedAnnualResult(2040, 2000, 9, 1991, 2, 1, 2, metric()),
                2041, new MonteCarloPairedAnnualResult(2041, 2000, 9, 1991, 2, 1, 2, metric()),
                2042, new MonteCarloPairedAnnualResult(2042, 2000, 9, 1991, 2, 1, 2, metric()));
    }
}
