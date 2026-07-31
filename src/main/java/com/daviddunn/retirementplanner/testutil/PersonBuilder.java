package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PersonBuilder {

    private String firstName =
            "John";

    private String lastName =
            "Doe";

    private LocalDate birthDate =
            LocalDate.of(1963, 1, 1);

    private final List<Account> accounts =
            new ArrayList<>();

    private final List<IncomeSource> incomeSources =
            new ArrayList<>();

    public static PersonBuilder aPerson() {
        return new PersonBuilder();
    }

    private PersonBuilder() {
    }

    public PersonBuilder withFirstName(
            String firstName) {

        this.firstName =
                Objects.requireNonNull(firstName);

        return this;
    }

    public PersonBuilder withLastName(
            String lastName) {

        this.lastName =
                Objects.requireNonNull(lastName);

        return this;
    }

    public PersonBuilder withBirthDate(
            LocalDate birthDate) {

        this.birthDate =
                Objects.requireNonNull(birthDate);

        return this;
    }

    public PersonBuilder bornOn(
            int year,
            int month,
            int dayOfMonth) {

        this.birthDate =
                LocalDate.of(
                        year,
                        month,
                        dayOfMonth);

        return this;
    }

    public PersonBuilder withAccount(
            Account account) {

        accounts.add(
                Objects.requireNonNull(account));

        return this;
    }

    public PersonBuilder withIncomeSource(
            IncomeSource incomeSource) {

        incomeSources.add(
                Objects.requireNonNull(incomeSource));

        return this;
    }

    public Person build() {

        Person person =
                new Person(
                        firstName,
                        lastName,
                        birthDate);

        for (Account account : accounts) {
            person.addAccount(account);
        }

        for (IncomeSource incomeSource : incomeSources) {
            person.addIncomeSource(incomeSource);
        }

        return person;
    }

    public PersonBuilder withIncomeSources(
            IncomeSource... incomeSources) {

        Objects.requireNonNull(
                incomeSources,
                "Income sources are required.");

        for (IncomeSource incomeSource : incomeSources) {
            withIncomeSource(incomeSource);
        }

        return this;
    }

    public PersonBuilder withAccounts(
            Account... accounts) {

        Objects.requireNonNull(
                accounts,
                "Accounts are required.");

        for (Account account : accounts) {
            withAccount(account);
        }

        return this;
    }

}