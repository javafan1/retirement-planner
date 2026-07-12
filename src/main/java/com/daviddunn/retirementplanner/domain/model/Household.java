package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;

public class Household {

    private final Person primaryPerson;
    private final Person spouse;

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

    public BigDecimal getNetWorth() {
        return primaryPerson.getNetWorth()
                .add(spouse.getNetWorth());
    }

    public int getAccountCount() {
        return primaryPerson.getAccountCount()
                + spouse.getAccountCount();
    }

    public BigDecimal getGuaranteedIncome() {

        return primaryPerson.getGuaranteedIncome()
                .add(spouse.getGuaranteedIncome());
    }

    public BigDecimal getTotalAssets() {

        return primaryPerson.getTotalAssets()
                .add(spouse.getTotalAssets());
    }

    public BigDecimal getTotalLiabilities() {

        return primaryPerson.getTotalLiabilities()
                .add(spouse.getTotalLiabilities());
    }

}