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

class KentuckyAnnualScheduleTest {
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
                defaults.getProjectionStartDate(), StateTaxAssumptions.simplified(StateTaxJurisdiction.KY));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    @Test
    void calendarRatesAndIndependentLifetimeTaxSurviveNormalProjection() {
        var plan = plan(DeathScenario.BOTH_SURVIVE);
        var projection = new ProjectionEngine().project(plan);
        var expected = List.of("2293.55", "2289.70", "2289.70", "2289.70", "2289.70", "2209.70", "2209.70", "2209.70", "2209.70");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(new BigDecimal(expected.get(i)), projection.getYearAt(i).getStateIncomeTax());
            assertEquals(0, projection.getYearAt(i).getTaxFundingWithdrawal().signum());
        }
        var state = projection.getYears().stream().map(ProjectionYear::getStateIncomeTax).reduce(BigDecimal.ZERO, BigDecimal::add);
        var federal = projection.getYears().stream().map(ProjectionYear::getFederalIncomeTax).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("20291.15"), state);
        assertEquals(federal.add(state), new ProjectionMetricsCalculator().calculate(plan, projection).totalTaxes());
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void deathYearUsesJointDeductionAndLaterYearUsesSingle(DeathScenario death) {
        var projection = new ProjectionEngine().project(plan(death));
        // Recipient has one 31110 retirement pool and one 3470 deduction in both joint and Single years.
        assertEquals(new BigDecimal("1729.70"), projection.getYearAt(1).getStateIncomeTax());
        assertEquals(new BigDecimal("1729.70"), projection.getYearAt(2).getStateIncomeTax());
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void survivorPensionUsesRecipientRatherThanOriginalOwnersExclusion(DeathScenario death) {
        var plan = plan(death);
        var original = death == DeathScenario.PRIMARY_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        var survivor = original == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        plan.getHousehold().members().forEach(person -> List.copyOf(person.getIncomeSources()).forEach(person::removeIncomeSource));
        plan.getHousehold().peopleByOwner().get(original).addIncomeSource(new Pension("Ordinary survivor pension", original,
                LocalDate.of(2026, 1, 1), null, new BigDecimal("7000"), BigDecimal.ZERO, new BigDecimal("7000")));
        var projection = new ProjectionEngine().project(plan);
        assertEquals(new BigDecimal("1733.55"), projection.getYearAt(0).getStateIncomeTax());
        for (int index : List.of(1, 2)) {
            var year = projection.getYearAt(index);
            assertEquals(new BigDecimal("1729.70"), year.getStateIncomeTax());
            var source = year.getIncomeSources().stream().filter(value -> value.kind()
                    == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.PENSION).findFirst().orElseThrow();
            assertEquals(survivor, source.owner().orElseThrow());
            assertEquals(original, source.pension().orElseThrow().originalOwner());
        }
    }
}
