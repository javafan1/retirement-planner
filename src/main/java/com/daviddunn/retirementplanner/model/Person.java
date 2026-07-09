package com.daviddunn.retirementplanner.model;

import java.time.LocalDate;

public class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;

    public Person(String firstName, String lastName, LocalDate birthDate) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public int getAge() {
        return LocalDate.now().getYear() - birthDate.getYear();
    }
}
