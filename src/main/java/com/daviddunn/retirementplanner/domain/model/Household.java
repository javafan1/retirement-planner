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
import java.util.Optional;

public class Household {

    private final Person primaryPerson;
    private final Person spouse;

    private final List<Expense> expenses = new ArrayList<>();

    @JsonCreator
    public Household(
            @JsonProperty("primaryPerson") Person primaryPerson,
            @JsonProperty("spouse") Person spouse) {

        this.primaryPerson = Objects.requireNonNull(primaryPerson, "Primary person is required.");
        if (primaryPerson == spouse) {
            throw new IllegalArgumentException("Primary and spouse must be distinct people.");
        }
        this.spouse = spouse;
        validatePersonReferences();
    }

    public Household(Person primaryPerson) {
        this(primaryPerson, null);
    }

    @JsonIgnore
    public String getHouseholdName() {

        return primaryPerson.getLastName() + " Household";
    }

    public Person getPrimaryPerson() {
        return primaryPerson;
    }

    /** Persistence-only nullable property; domain callers use spouse() or requireSpouse(). */
    @JsonProperty("spouse")
    private Person serializedSpouse() {
        return spouse;
    }

    @JsonIgnore
    public Optional<Person> spouse() {
        return Optional.ofNullable(spouse);
    }

    @JsonIgnore
    public boolean hasSpouse() {
        return spouse != null;
    }

    @JsonIgnore
    public List<Person> members() {
        return hasSpouse() ? List.of(primaryPerson, spouse) : List.of(primaryPerson);
    }

    /** Compatibility boundary for consumers not yet adapted to optional membership. */
    @JsonIgnore
    public Person getSpouse() {
        return requireSpouse("required-spouse consumer (Household.getSpouse)");
    }

    public Person requireSpouse(String component) {
        Objects.requireNonNull(component, "Component is required.");
        if (!hasSpouse()) {
            throw new UnsupportedOperationException(
                    "Single-person household not yet supported by " + component + ".");
        }
        return spouse;
    }

    public void validatePersonReferences() {
        if (!hasSpouse()) {
            primaryPerson.getAccounts().forEach(account ->
                    validateOwnership(account.getOwnership(), "account " + account.getName()));
            primaryPerson.getIncomeSources().forEach(income ->
                    validateOwnership(income.getOwnership(), "income " + income.getName()));
        }
    }

    public void validateOwnership(AccountOwnership ownership, String description) {
        Objects.requireNonNull(ownership, "Ownership is required.");
        if (!hasSpouse() && ownership == AccountOwnership.SPOUSE) {
            throw new IllegalArgumentException(
                    "SPOUSE ownership references an absent spouse: " + description + ".");
        }
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
                .add(spouse().map(Person::getTotalLiabilities).orElse(BigDecimal.ZERO));
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
                .add(spouse().map(person -> person.getGuaranteedIncome(projectionDate)).orElse(BigDecimal.ZERO));
    }

}
