package com.daviddunn.retirementplanner.app;

import com.daviddunn.retirementplanner.model.*;
import com.daviddunn.retirementplanner.account.*;
import com.daviddunn.retirementplanner.util.Money;

import java.time.LocalDate;

public class RetirementPlannerApplication {

    public void run() {

        Person david =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963,6,4));

        Person lisa =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965,2,28));

        Household household =
                new Household(david,lisa);


        printHousehold(household);

        TraditionalIRA ira =
                new TraditionalIRA(
                        david,
                        "Fidelity Traditional IRA",
                        Money.of("2587000"));

        RothIRA roth =
                new RothIRA(
                        david,
                        "Fidelity Roth IRA",
                        Money.of("400000"));

        david.addAccount(ira);
        david.addAccount(roth);

        System.out.println();
        System.out.println("Net Worth");
        System.out.println("----------");
        System.out.println(david.getNetWorth());

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