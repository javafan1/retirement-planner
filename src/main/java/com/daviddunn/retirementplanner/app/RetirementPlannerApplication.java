package com.daviddunn.retirementplanner.app;

import com.daviddunn.retirementplanner.data.DemoDataFactory;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import com.daviddunn.retirementplanner.util.CurrencyFormatter;

public class RetirementPlannerApplication {

    public void run() {

        Household household =
                DemoDataFactory.createHousehold();

        printHeader();

        printPerson(household.getPrimaryPerson());

        printPerson(household.getSpouse());

        printHouseholdSummary(household);
    }

//    private Household createHousehold() {
//
//        Person david = new Person(
//                "David",
//                "Dunn",
//                LocalDate.of(1963, 6, 4));
//
//        Person lisa = new Person(
//                "Lisa",
//                "Dunn",
//                LocalDate.of(1965, 2, 28));
//
//        TraditionalIRA davidTraditionalIRA =
//                new TraditionalIRA(
//                        david,
//                        "Fidelity Traditional IRA",
//                        Money.of("2587000.00"));
//
//        RothIRA davidRothIRA =
//                new RothIRA(
//                        david,
//                        "Fidelity Roth IRA",
//                        Money.of("400000.00"));
//
//        TraditionalIRA lisaTraditionalIRA =
//                new TraditionalIRA(
//                        lisa,
//                        "Fidelity Traditional IRA",
//                        Money.of("2165700.00"));
//
//        david.addAccount(davidTraditionalIRA);
//        david.addAccount(davidRothIRA);
//
//        lisa.addAccount(lisaTraditionalIRA);
//
//        return new Household(david, lisa);
//    }

//    public void run() {
//
//        Person david =
//                new Person(
//                        "David",
//                        "Dunn",
//                        LocalDate.of(1963,6,4));
//
//        Person lisa =
//                new Person(
//                        "Lisa",
//                        "Dunn",
//                        LocalDate.of(1965,2,28));
//
//        Household household =
//                new Household(david,lisa);
//
//
//        printHousehold(household);
//
//        TraditionalIRA ira =
//                new TraditionalIRA(
//                        david,
//                        "Fidelity Traditional IRA",
//                        Money.of("2587000"));
//
//        RothIRA roth =
//                new RothIRA(
//                        david,
//                        "Fidelity Roth IRA",
//                        Money.of("400000"));
//
//        david.addAccount(ira);
//        david.addAccount(roth);
//
//        System.out.println();
//        System.out.println("Net Worth");
//        System.out.println("----------");
//        System.out.println(david.getNetWorth());
//
//        System.out.println();
//        System.out.println("Accounts");
//        System.out.println("--------");
//
//        for (Account account : david.getAccounts()) {
//
//            System.out.printf(
//                    "%-30s %15s%n",
//                    account.getAccountName(),
//                    CurrencyFormatter.format(account.getBalance()));
//        }
//
//        System.out.println();
//        System.out.println("Net Worth");
//        System.out.println("---------");
//
//        System.out.println(
//                CurrencyFormatter.format(
//                        david.getNetWorth()));
//    }

    private void printHeader() {

        System.out.println();
        System.out.println("Retirement Planner");
        System.out.println("==================");
        System.out.println();
    }

    private void printPerson(Person person) {

        System.out.println(person.getFullName());

        System.out.println("-------------------------");

        System.out.println("Age: " + person.getAge());

        System.out.println();

        System.out.println("Accounts");

        for (Account account : person.getAccounts()) {

            System.out.printf(
                    "%-30s %15s%n",
                    account.getAccountName(),
                    CurrencyFormatter.format(account.getCurrentBalance()));
        }

        System.out.println();

        System.out.println("Net Worth");

        System.out.println(
                CurrencyFormatter.format(
                        person.getNetWorth()));

        System.out.println();
    }

    private void printHouseholdSummary(Household household) {

        System.out.println("==============================");

        System.out.println("Household Net Worth");

        System.out.println(
                CurrencyFormatter.format(
                        household.getNetWorth()));

        System.out.println();

        System.out.println("Accounts: "
                + household.getAccountCount());
    }

    private void printHousehold(Household household) {

        System.out.println();
        System.out.println("Retirement Planner");
        System.out.println("==================");
        System.out.println();

        System.out.println("Primary");
        System.out.println("-------");
        System.out.println("Name : " +
                household.getPrimaryPerson().getFullName());

        System.out.println("Age  : " +
                household.getPrimaryPerson().getAge());

        System.out.println();

        System.out.println("Spouse");
        System.out.println("------");
        System.out.println("Name : " +
                household.getSpouse().getFullName());

        System.out.println("Age  : " +
                household.getSpouse().getAge());




    }
}