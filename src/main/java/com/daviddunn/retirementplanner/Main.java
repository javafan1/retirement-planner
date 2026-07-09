package com.daviddunn.retirementplanner;

import com.daviddunn.retirementplanner.model.Person;

import java.time.LocalDate;
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static void main(String[] args) {

        Person david = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        System.out.println("Retirement Planner");
        System.out.println("------------------");
        System.out.println("Name: " + david.getFullName());

        System.out.println("Birth Date: " +
                david.getBirthDate());

        System.out.println("Age: " + david.getAge());
    }
}
