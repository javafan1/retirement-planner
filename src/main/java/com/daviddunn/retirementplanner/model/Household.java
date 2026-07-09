package com.daviddunn.retirementplanner.model;

public class Household {

    private Person primaryPerson;
    private Person spouse;

    public Household(Person primaryPerson, Person spouse) {
        this.primaryPerson = primaryPerson;
        this.spouse = spouse;
    }

    public Person getPrimaryPerson() {
        return primaryPerson;
    }

    public Person getSpouse() {
        return spouse;
    }
}