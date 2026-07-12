package com.daviddunn.retirementplanner.model;

import com.daviddunn.retirementplanner.model.Household;
import com.daviddunn.retirementplanner.model.Person;
import com.daviddunn.retirementplanner.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.financial.RothIRA;
import com.daviddunn.retirementplanner.util.Money;

import java.time.LocalDate;
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