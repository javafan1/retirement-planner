

package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DemoDataFactory {

    private DemoDataFactory() {
    }

    public static RetirementPlan createRetirementPlan() {

        Person david = createDavid();
        Person lisa = createLisa();

        addDavidIncome(david);
        addLisaIncome(lisa);

        Household household = new Household(david, lisa);

        AccountPortfolio portfolio = createPortfolio();

        PlanningAssumptions assumptions =
                createPlanningAssumptions();

        return new RetirementPlan(
                household,
                portfolio,
                assumptions);
    }

    private static PlanningAssumptions createPlanningAssumptions() {

        return new PlanningAssumptions(
                Money.of("0.025"),
                Money.of("0.070"),
                40);
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

    private static void addDavidIncome(Person david) {

        david.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 7, 1),
                        null,
                        Money.of("3800"),
                        BigDecimal.ZERO));
    }

    private static void addLisaIncome(Person lisa) {

        lisa.addIncomeSource(
                new Pension(
                        "Lisa Pension",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2027, 2, 28),
                        null,
                        Money.of("956"),
                        BigDecimal.ZERO));
    }

    private static AccountPortfolio createPortfolio() {

        AccountPortfolio portfolio = new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        Money.of("2587000")));

        portfolio.addAccount(
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        Money.of("400000")));

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.SPOUSE,
                        Money.of("2165700")));

        return portfolio;
    }
}
/*
package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
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

        AccountPortfolio portfolio = createPortfolio(david, lisa);

        PlanningAssumptions assumptions =
                createPlanningAssumptions();

        return new RetirementPlan(
                household,
                portfolio,
                assumptions);
    }

    private static PlanningAssumptions createPlanningAssumptions() {

        return new PlanningAssumptions(
                Money.of("0.025"),
                Money.of("0.070"),
                40);
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
                new TraditionalIRA(
                        "Traditional IRA",
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
                        PersonRole.PRIMARY,
                        LocalDate.of(2026, 7, 1),
                        null,
                        Money.of("3800"),
                        false));
    }

    private static void addLisaIncome(Person lisa) {

        lisa.addIncomeSource(
                new Pension(
                        "Lisa Pension",
                        PersonRole.SPOUSE,
                        LocalDate.of(2027, 2, 28),
                        null,
                        Money.of("956"),
                        false));
    }

    private static AccountPortfolio createPortfolio(
            Person david,
            Person lisa) {

        AccountPortfolio portfolio = new AccountPortfolio();

        david.getAccounts()
                .forEach(portfolio::addAccount);

        lisa.getAccounts()
                .forEach(portfolio::addAccount);

        return portfolio;
    }
}

 */