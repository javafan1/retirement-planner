package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionChartReconciliationTest {
    @Test void everyAccountTypeAndPermittedOwnerUsesAuthoritativeClassification() {
        for (var type : AccountType.values()) for (var owner : AccountOwnership.values()) {
            if (!type.allowsOwnership(owner)) continue;
            Account account = new Account(type.name(), owner, BigDecimal.ZERO, type) {
                @Override public AccountType getType() { return type; }
            };
            var year = ProjectionYearBuilder.aProjectionYear().withEndingInvestableAssets(100)
                    .withEndingAccountSnapshots(List.of(new ProjectedAccountSnapshot(account, new BigDecimal("100")))).build();
            var point = ProjectionChartFixtures.model(List.of(year), List.of(), null).years().getFirst();
            assertTrue(point.compositionComplete(), type + " / " + owner);
            assertEquals(new BigDecimal("100"), point.composition().get(type.getProjectionAssetType()));
            assertEquals(0, point.compositionResidual().signum());
        }
    }

    @Test void reconciliationUsesRawResidualAndDoesNotAbsorbMissingBalances() {
        for (String amount : List.of("100", "99.99407004880506", "99.99", "100.01", "99.989999", "100.010001", "99", "90")) {
            Account account = new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Test", AccountOwnership.JOINT, BigDecimal.ZERO);
            var year = ProjectionYearBuilder.aProjectionYear().withEndingInvestableAssets(100)
                    .withEndingAccountSnapshots(List.of(new ProjectedAccountSnapshot(account, new BigDecimal(amount)))).build();
            var point = ProjectionChartFixtures.model(List.of(year), List.of(), null).years().getFirst();
            assertEquals(new BigDecimal("100").subtract(new BigDecimal(amount)).abs().compareTo(new BigDecimal("0.01")) <= 0, point.compositionComplete());
            assertEquals(new BigDecimal("100").subtract(new BigDecimal(amount)), point.compositionResidual());
        }
    }

    @Test void missingZeroBalanceDuplicateAndUnexpectedAccountsFailEvenWhenTotalsMatch() {
        var first = new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Same name", AccountOwnership.PRIMARY, BigDecimal.ZERO);
        var second = new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Same name", AccountOwnership.SPOUSE, BigDecimal.ZERO);
        var one = new ProjectedAccountSnapshot(first, BigDecimal.ZERO);
        var two = new ProjectedAccountSnapshot(second, BigDecimal.ZERO);
        for (var snapshots : List.of(List.of(one), List.of(one, one), List.of(two))) {
            var year = ProjectionYearBuilder.aProjectionYear().withEndingAccountSnapshots(snapshots).build();
            assertFalse(ProjectionChartModel.from(List.of(year), List.of(), null, List.of(first, second))
                    .years().getFirst().compositionComplete());
        }
        var complete = ProjectionYearBuilder.aProjectionYear().withEndingAccountSnapshots(List.of(one, two)).build();
        assertTrue(ProjectionChartModel.from(List.of(complete), List.of(), null, List.of(first, second))
                .years().getFirst().compositionComplete());
        assertFalse(ProjectionChartModel.from(List.of(complete), List.of(), null, List.of(first))
                .years().getFirst().compositionComplete());
        assertFalse(ProjectionChartModel.from(List.of(complete), List.of(), null, List.of(first, first, second))
                .years().getFirst().compositionComplete());
    }

    @Test void validProjectionWithFractionalCashFlowDisplaysAllThirtyYears() {
        var plan = roundingPlan();
        var projection = new ProjectionEngine().project(plan);
        var model = ProjectionChartModel.from(projection.getYears(), List.of(), null, plan.getAccountPortfolio().getAccounts());
        assertEquals(30, model.years().size());
        var affected = model.years().stream().filter(p -> p.year() == 2030).findFirst().orElseThrow();
        assertTrue(affected.compositionComplete());
        assertEquals(0, new BigDecimal("0.00592995119494").compareTo(affected.compositionResidual()));
        assertEquals(0, new BigDecimal("2211334.91").compareTo(affected.values().get(ProjectionChartMetric.INVESTABLE_ASSETS)));
        // Characterize existing results, including first, conversion, first RMD and final years.
        for (var point : model.years()) {
            var source = projection.getYears().stream().filter(y -> y.getCalendarYear() == point.year()).findFirst().orElseThrow();
            var sum = source.getEndingAccountSnapshots().stream().map(ProjectedAccountSnapshot::getEndingBalance)
                    .reduce(source.getEndingRetainedNonQualifiedAssets(), BigDecimal::add);
            assertEquals(0, source.getEndingInvestableAssets().subtract(sum).compareTo(point.compositionResidual()));
        }
        assertTrue(model.years().getFirst().compositionComplete());
        assertTrue(model.years().stream().allMatch(ProjectionChartModel.Point::compositionComplete));
        assertEquals(2038, model.rmdPeriods().getFirst().firstYear());
    }

    static com.daviddunn.retirementplanner.domain.model.RetirementPlan roundingPlan() {
        var plan = com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory.createEmptyPlan();
   var owner=com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
   plan.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1963,6,4));
   plan.getHousehold().getSpouse().setBirthDate(java.time.LocalDate.of(1965,2,28));
   plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(new BigDecimal("0.04"),new BigDecimal("0.02"),30,java.time.LocalDate.of(2027,1,1)));
   plan.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension("Synthetic pension",owner,java.time.LocalDate.of(2027,1,1),null,new BigDecimal("1000.01"),new BigDecimal("0.025")));
   plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Synthetic spending",new BigDecimal("60000.01")));
   plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Brokerage",owner,new BigDecimal("500000")));
   plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",owner,new BigDecimal("1500000")));
   plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Roth",owner,new BigDecimal("100000")));

        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2027, new BigDecimal("75000"),
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        return plan;
    }
}