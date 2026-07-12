package com.daviddunn.retirementplanner.data;

import com.daviddunn.retirementplanner.financial.RothIRA;
import com.daviddunn.retirementplanner.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.income.Pension;
import com.daviddunn.retirementplanner.model.Household;
import com.daviddunn.retirementplanner.model.Person;
import com.daviddunn.retirementplanner.util.Money;

import java.time.LocalDate;

public final class SampleDataFactory {

    private SampleDataFactory() {
    }

    public static Household createHousehold() {

        Person david = createDavid();
        Person lisa = createLisa();

        createDavidAccounts(david);
        createLisaAccounts(lisa);

        createDavidIncome(david);
        createLisaIncome(lisa);

        return new Household(david, lisa);
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

    private static void createDavidAccounts(Person david) {

        david.addAccount(
                new TraditionalIRA(
                        david,
                        "Fidelity Traditional IRA",
                        Money.of("2587000")));

        david.addAccount(
                new RothIRA(
                        david,
                        "Fidelity Roth IRA",
                        Money.of("400000")));
    }

    private static void createLisaAccounts(Person lisa) {

        lisa.addAccount(
                new TraditionalIRA(
                        lisa,
                        "Fidelity Traditional IRA",
                        Money.of("2165700")));
    }

    private static void createDavidIncome(Person david) {

        david.addIncomeSource(
                new Pension(
                        david,
                        "Primary Pension",
                        Money.of("3800")));
    }

    private static void createLisaIncome(Person lisa) {

        // Lisa doesn't have a pension yet.
        // We'll add Social Security later.
    }
}