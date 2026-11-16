package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NorthCarolinaAnnualScheduleTest {
    private RetirementPlan plan(DeathScenario death) {
        var household = new Household(new Person("Primary", "Example", LocalDate.of(1966, 6, 1)),
                new Person("Spouse", "Example", LocalDate.of(1966, 7, 1)));
        var recipient = death == DeathScenario.SPOUSE_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        // The steady-state worksheet uses annual 100000 to far below cent precision.
        // Survivor fixtures use exactly 7000/month, avoiding a recurring-decimal input at a half-cent tax boundary.
        household.peopleByOwner().get(recipient).addIncomeSource(new Pension("Private pension", recipient,
                LocalDate.of(2026, 1, 1), null, death == DeathScenario.BOTH_SURVIVE
                        ? new BigDecimal("100000").divide(new BigDecimal("12"), 40, RoundingMode.HALF_UP)
                        : new BigDecimal("7000"),
                BigDecimal.ZERO));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(AccountFactory.create(AccountType.CHECKING, "Cash", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
        var defaults = new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 9, LocalDate.of(2026, 1, 1));
        var assumptions = new PlanningAssumptions(defaults.getEconomicAssumptions(), defaults.getTaxAssumptions(),
                defaults.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death,
                        death == DeathScenario.BOTH_SURVIVE ? null : 2027, 67, BigDecimal.ONE), 9,
                defaults.getProjectionStartDate(), StateTaxAssumptions.simplified(StateTaxJurisdiction.NC));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    @Test
    void calendarRatesAndIndependentLifetimeTaxSurviveNormalProjection() {
        var plan = plan(DeathScenario.BOTH_SURVIVE);
        var projection = new ProjectionEngine().project(plan);
        var expected = List.of("2972.55", "2600.05", "2600.05", "2600.05", "2413.80", "2413.80", "2413.80", "2227.55", "2227.55");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(new BigDecimal(expected.get(i)), projection.getYearAt(i).getStateIncomeTax());
            assertEquals(0, projection.getYearAt(i).getTaxFundingWithdrawal().signum());
        }
        var state = projection.getYears().stream().map(ProjectionYear::getStateIncomeTax).reduce(BigDecimal.ZERO, BigDecimal::add);
        var federal = projection.getYears().stream().map(ProjectionYear::getFederalIncomeTax).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("22469.20"), state);
        assertEquals(federal.add(state), new ProjectionMetricsCalculator().calculate(plan, projection).totalTaxes());
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void deathYearUsesJointDeductionAndLaterYearUsesSingle(DeathScenario death) {
        var projection = new ProjectionEngine().project(plan(death));
        // Independent unchanged 84000 pension: 2027 joint base 58500; 2028 Single base 71250.
        assertEquals(new BigDecimal("2041.65"), projection.getYearAt(1).getStateIncomeTax());
        assertEquals(new BigDecimal("2486.63"), projection.getYearAt(2).getStateIncomeTax());
    }
}
