package com.daviddunn.retirementplanner.financial;

import com.daviddunn.retirementplanner.model.*;
import java.math.BigDecimal;
import com.daviddunn.retirementplanner.model.financial.AccountType;


public abstract class Account {

    private Person owner;
    private Institution institution;

    private String name;
    private BigDecimal balance;
    private final AccountType type;

//    public Account(Person owner, String name, BigDecimal balance) {
//        this.owner = owner;
//        this.name = name;
//        this.balance = balance;
//    }

    protected Account(Person owner,
                      String name,
                      AccountType type,
                      BigDecimal balance) {

        this.owner = owner;
        this.name = name;
        this.type = type;
        this.balance = balance;
    }

    public AccountType getType() {
        return type;
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