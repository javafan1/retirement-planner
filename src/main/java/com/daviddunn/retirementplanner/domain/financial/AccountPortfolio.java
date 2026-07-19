package com.daviddunn.retirementplanner.domain.financial;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AccountPortfolio {

    private final List<Account> accounts;

    public AccountPortfolio() {
        this(new ArrayList<>());
    }

    @JsonCreator
    public AccountPortfolio(
            @JsonProperty("accounts") List<Account> accounts) {

        this.accounts = new ArrayList<>(
                Objects.requireNonNull(accounts, "accounts"));
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    public void add(Account account) {
        accounts.add(Objects.requireNonNull(account));
    }

    public void remove(Account account) {
        accounts.remove(account);
    }

    public void replace(Account oldAccount,
                        Account newAccount) {

        Objects.requireNonNull(oldAccount);
        Objects.requireNonNull(newAccount);

        int index = accounts.indexOf(oldAccount);

        if (index >= 0) {
            accounts.set(index, newAccount);
        }
    }

    @JsonIgnore
    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    @JsonIgnore
    public int size() {
        return accounts.size();
    }
}