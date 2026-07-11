package com.daviddunn.retirementplanner.income;

import com.daviddunn.retirementplanner.model.Person;
import com.daviddunn.retirementplanner.util.Money;

import java.math.BigDecimal;

public class Pension extends IncomeSource {

    private final BigDecimal monthlyBenefit;

    public Pension(Person owner,
                   String name,
                   BigDecimal monthlyBenefit) {

        super(owner, name);

        this.monthlyBenefit = monthlyBenefit;
    }

    @Override
    public BigDecimal getAnnualIncome() {

        return monthlyBenefit.multiply(
                Money.of("12"));
    }
}