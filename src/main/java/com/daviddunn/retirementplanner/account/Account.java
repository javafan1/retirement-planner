package com.daviddunn.retirementplanner.account;

import com.daviddunn.retirementplanner.model.Person;
import java.math.BigDecimal;



public class Account {

    private Person owner;
    private String name;
    private BigDecimal balance;

    public Account(Person owner, String name, BigDecimal balance) {
        this.owner = owner;
        this.name = name;
        this.balance = balance;
    }

    public Person getOwner() {
        return owner;
    }

    public String getAccountName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

}