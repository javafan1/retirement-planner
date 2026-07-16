package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
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

    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

//    public BigDecimal getTotalAssets() {
//
//        return primaryPerson.getTotalAssets()
//                .add(spouse.getTotalAssets());
//    }

    public BigDecimal getTotalAssets() {

        BigDecimal total = BigDecimal.ZERO;

        for (Account account : getAllAccounts()) {
            total = total.add(account.getCurrentBalance());
        }

        return total;
    }
    public BigDecimal getTotalLiabilities() {

        return primaryPerson.getTotalLiabilities()
                .add(spouse.getTotalLiabilities());
    }

    public BigDecimal getNetWorth() {

        return getTotalAssets()
                .subtract(getTotalLiabilities());
    }

    public BigDecimal getGuaranteedIncome() {

        return primaryPerson.getGuaranteedIncome()
                .add(spouse.getGuaranteedIncome());
    }

    public BigDecimal getTotalAnnualExpenses() {

        BigDecimal total = BigDecimal.ZERO;

        for (Expense expense : expenses) {
            total = total.add(expense.getAnnualAmount());
        }

        return total;
    }

    public List<Account> getAllAccounts() {

        List<Account> accounts = new ArrayList<>();

        accounts.addAll(primaryPerson.getAccounts());
        accounts.addAll(spouse.getAccounts());

        return Collections.unmodifiableList(accounts);
    }

    public int getAccountCount() {
        return getAllAccounts().size();
    }
}