package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Household {

    private final Person primaryPerson;
    private final Person spouse;

    private final List<Expense> expenses = new ArrayList<>();

    @JsonCreator
    public Household(
            @JsonProperty("primaryPerson") Person primaryPerson,
            @JsonProperty("spouse") Person spouse) {

        this.primaryPerson = Objects.requireNonNull(primaryPerson);
        this.spouse = Objects.requireNonNull(spouse);
    }

    @JsonIgnore
    public String getHouseholdName() {

        return primaryPerson.getLastName() + " Household";
    }

    public Person getPrimaryPerson() {
        return primaryPerson;
    }

    public Person getSpouse() {
        return spouse;
    }

    public void addExpense(Expense expense) {
        expenses.add(Objects.requireNonNull(expense));
    }

    public void removeExpense(Expense expense) {

        expenses.remove(
                Objects.requireNonNull(expense));
    }

    public void replaceExpense(
            Expense oldExpense,
            Expense newExpense) {

        Objects.requireNonNull(oldExpense);
        Objects.requireNonNull(newExpense);

        int index =
                expenses.indexOf(oldExpense);

        if (index >= 0) {
            expenses.set(
                    index,
                    newExpense);
        }
    }


    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

//    public BigDecimal getTotalAssets() {
//
//        return primaryPerson.getTotalAssets()
//                .add(spouse.getTotalAssets());
//    }

//    @JsonIgnore
//    public BigDecimal getTotalAssets() {
//
//        BigDecimal total = BigDecimal.ZERO;
//
//        for (Account account : getAllAccounts()) {
//            total = total.add(account.getCurrentBalance());
//        }
//
//        return total;
//    }
    @JsonIgnore
    public BigDecimal getTotalLiabilities() {

        return primaryPerson.getTotalLiabilities()
                .add(spouse.getTotalLiabilities());
    }

//    @JsonIgnore
//    public BigDecimal getNetWorth() {
//
//        return getTotalAssets()
//                .subtract(getTotalLiabilities());
//    }
//    @JsonIgnore
//    public BigDecimal getGuaranteedIncome() {
//
//        return primaryPerson.getGuaranteedIncome()
//                .add(spouse.getGuaranteedIncome());
//    }

    @JsonIgnore
    public BigDecimal getGuaranteedIncome(LocalDate projectionDate) {

        return primaryPerson.getGuaranteedIncome(projectionDate)
                .add(spouse.getGuaranteedIncome(projectionDate));
    }

}