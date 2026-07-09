package com.daviddunn.retirementplanner.app;

import com.daviddunn.retirementplanner.model.Household;
import com.daviddunn.retirementplanner.model.Person;

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