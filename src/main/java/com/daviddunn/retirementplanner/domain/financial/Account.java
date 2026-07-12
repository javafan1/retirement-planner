package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.Institution;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import com.daviddunn.retirementplanner.domain.model.AccountType;


public abstract class Account {

    private Person owner;
    private Institution institution;

    private String name;
    private BigDecimal curremtBalance;
    private final AccountType type;

//    public Account(Person owner, String name, BigDecimal curremtBalance) {
//        this.owner = owner;
//        this.name = name;
//        this.curremtBalance = curremtBalance;
//    }

    protected Account(Person owner,
                      String name,
                      AccountType type,
                      BigDecimal balance) {

        this.owner = owner;
        this.name = name;
        this.type = type;
        this.curremtBalance = balance;
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

    public BigDecimal getCurrentBalance() {
        return curremtBalance;
    }

    public void deposit(BigDecimal amount) {
        curremtBalance = curremtBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {

        curremtBalance = curremtBalance.subtract(amount);
    }


    //public BigDecimal getCurrentBalance() { return curremtBalance;}


}


