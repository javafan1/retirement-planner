

package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AccountPortfolio {

    private final List<Account> accounts;

    @JsonCreator
    public AccountPortfolio(
            @JsonProperty("accounts") List<Account> accounts) {

        this.accounts = new ArrayList<>();

        if (accounts != null) {
            this.accounts.addAll(accounts);
        }
    }

    public AccountPortfolio() {
        this(List.of());
    }

//    public void addAccount(Account account) {
//        accounts.add(account);
//    }

    public List<Account> getAccounts() {
        return List.copyOf(accounts);
    }

    public List<Account> getAccounts(AccountOwnership ownership) {

        return accounts.stream()
                .filter(account -> account.getOwnership() == ownership)
                .toList();
    }

    @JsonIgnore
    public BigDecimal getTotalBalance() {

        return accounts.stream()
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @JsonIgnore
    public BigDecimal getTotalBalance(AccountOwnership owner) {

        return getAccounts(owner).stream()
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addAccount(Account account) {
        accounts.add(Objects.requireNonNull(account));
    }

    public void removeAccount(Account account) {
        accounts.remove(Objects.requireNonNull(account));
    }

    public void replaceAccount(Account oldAccount,
                               Account newAccount) {

        Objects.requireNonNull(oldAccount);
        Objects.requireNonNull(newAccount);

        int index = accounts.indexOf(oldAccount);

        if (index >= 0) {
            accounts.set(index, newAccount);
        }
    }
}

/*
package com.daviddunn.retirementplanner.domain.financial;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
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

    @JsonIgnore
    public BigDecimal getTotalBalance() {

        BigDecimal total = BigDecimal.ZERO;

        for (Account account : accounts) {
            total = total.add(account.getCurrentBalance());
        }

        return total;
    }

    @JsonIgnore
    public int getAccountCount() {
        return accounts.size();
    }

//    @JsonIgnore
//    public BigDecimal getNetWorth() {
//        return getTotalBalance();
//    }

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
*/

