package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Person {

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private List<Account> accounts = new ArrayList<>();

    public Person() {
    }

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

        return getTotalAssets()
                .subtract(getTotalLiabilities());
    }

    private List<IncomeSource> incomeSources =
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

    public BigDecimal getTotalAssets() {

        BigDecimal total = BigDecimal.ZERO;

        for (Account account : accounts) {
            total = total.add(account.getCurrentBalance());
        }

        return total;
    }

    public BigDecimal getTotalLiabilities() {
        return BigDecimal.ZERO;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public void setIncomeSources(List<IncomeSource> incomeSources) {
        this.incomeSources = incomeSources;
    }
}
