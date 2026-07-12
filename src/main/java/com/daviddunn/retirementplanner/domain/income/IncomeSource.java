package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;

public abstract class IncomeSource {

    private final Person owner;
    private final String name;

    protected IncomeSource(Person owner,
                           String name) {

        this.owner = owner;
        this.name = name;
    }

    public Person getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public abstract BigDecimal getAnnualIncome();
}