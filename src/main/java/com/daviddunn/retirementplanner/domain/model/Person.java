package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    @JsonIgnore
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @JsonIgnore
    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public void addAccount(Account account) {
        accounts.add(account);
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    @JsonIgnore
    public int getAccountCount() {
        return accounts.size();
    }

    @JsonIgnore
    public BigDecimal getNetWorth() {

        return getTotalAssets()
                .subtract(getTotalLiabilities());
    }

    private List<IncomeSource> incomeSources =
            new ArrayList<>();

    public void addIncomeSource(IncomeSource incomeSource) {
        incomeSources.add(
                Objects.requireNonNull(incomeSource));
    }

    public void removeIncomeSource(IncomeSource incomeSource) {
        incomeSources.remove(
                Objects.requireNonNull(incomeSource));
    }

    public void replaceIncomeSource(
            IncomeSource oldIncomeSource,
            IncomeSource newIncomeSource) {

        Objects.requireNonNull(oldIncomeSource);
        Objects.requireNonNull(newIncomeSource);

        int index =
                incomeSources.indexOf(oldIncomeSource);

        if (index >= 0) {
            incomeSources.set(
                    index,
                    newIncomeSource);
        }
    }

    public List<IncomeSource> getIncomeSources() {
        return Collections.unmodifiableList(incomeSources);
    }

    @JsonIgnore
    public BigDecimal getGuaranteedIncome(LocalDate projectionDate) {

        BigDecimal total = BigDecimal.ZERO;

        for (IncomeSource income : incomeSources) {
            total = total.add(
                    income.getAnnualIncome(projectionDate));
        }

        return total;
    }
//    @JsonIgnore
//    public BigDecimal getGuaranteedIncome() {
//
//        BigDecimal total = BigDecimal.ZERO;
//
//        for (IncomeSource income : incomeSources) {
//            total = total.add(income.getAnnualIncome());
//        }
//
//        return total;
//    }

    @JsonIgnore
    public BigDecimal getTotalAssets() {

        BigDecimal total = BigDecimal.ZERO;

        for (Account account : accounts) {
            total = total.add(account.getCurrentBalance());
        }

        return total;
    }

    @JsonIgnore
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

        this.incomeSources =
                incomeSources == null
                        ? new ArrayList<>()
                        : new ArrayList<>(incomeSources);
    }
}
