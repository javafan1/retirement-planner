package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.util.Money;

import java.time.LocalDate;

public final class DemoDataFactory {

    private DemoDataFactory() {
    }

    public static RetirementPlan createRetirementPlan() {

        Person david = createDavid();
        Person lisa = createLisa();

        addDavidAccounts(david);
        addLisaAccounts(lisa);

        addDavidIncome(david);
        addLisaIncome(lisa);

        Household household = new Household(david, lisa);

        PlanningAssumptions assumptions =
                createPlanningAssumptions();

        AccountPortfolio accountPortfolio = new AccountPortfolio();

        return new RetirementPlan(
                household,
                accountPortfolio,
                assumptions);
    }

    private static PlanningAssumptions createPlanningAssumptions() {

        return new PlanningAssumptions(
                Money.of("0.025"),   // 2.5% inflation
                Money.of("0.070"));  // 7.0% investment return
    }

    private static Person createDavid() {

        return new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));
    }

    private static Person createLisa() {

        return new Person(
                "Lisa",
                "Dunn",
                LocalDate.of(1965, 2, 28));
    }

    private static void addDavidAccounts(Person david) {

        david.addAccount(
                new TraditionalIRA("Traditional IRA",
                        Money.of("2587000")));

        david.addAccount(
                new RothIRA(
                        "Roth IRA",
                        Money.of("400000")));
    }

    private static void addLisaAccounts(Person lisa) {

        lisa.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        Money.of("2165700")));
    }

    private static void addDavidIncome(Person david) {

        david.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        LocalDate.of(2026, 7, 1),
                        Money.of("3800"),
                        false));
    }

    //"Primary Pension",

    private static void addLisaIncome(Person lisa) {

        // Placeholder for future Social Security and pension.
    }
}