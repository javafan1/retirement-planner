package com.daviddunn.retirementplanner.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import com.daviddunn.retirementplanner.financial.Account;
import com.daviddunn.retirementplanner.income.IncomeSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private final List<Account> accounts = new ArrayList<>();

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
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public void addAccount(Account account) {
        accounts.add(account);
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    public int getAccountCount() {
        return accounts.size();
    }

    public BigDecimal getNetWorth() {

        BigDecimal total = BigDecimal.ZERO;

        for (Account account : accounts) {
            total = total.add(account.getBalance());
        }

        return total;
    }

    private final List<IncomeSource> incomeSources =
            new ArrayList<>();

    public void addIncomeSource(IncomeSource incomeSource) {
        incomeSources.add(incomeSource);
    }

    public List<IncomeSource> getIncomeSources() {
        return Collections.unmodifiableList(incomeSources);
    }

    public BigDecimal getGuaranteedIncome() {

        BigDecimal total = BigDecimal.ZERO;

        for (IncomeSource income : incomeSources) {
            total = total.add(income.getAnnualIncome());
        }

        return total;
    }

}
