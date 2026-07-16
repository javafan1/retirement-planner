package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.Institution;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "accountType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TraditionalIRA.class, name = "traditional"),
        @JsonSubTypes.Type(value = RothIRA.class, name = "roth")
})
public abstract class Account {

    //private Person owner;


    //private Institution institution;

    private String name;
    private BigDecimal currentBalance;
    private AccountType type;

//    public Account(Person owner, String name, BigDecimal curremtBalance) {
//        this.owner = owner;
//        this.name = name;
//        this.curremtBalance = curremtBalance;
//    }

    protected Account() {
    }

    protected Account(String name,
                      AccountType type,
                      BigDecimal balance) {

        //this.owner = owner;
        this.name = name;
        this.type = type;
        this.currentBalance = balance;
    }

    public AccountType getType() {
        return type;
    }

//    public Person getOwner() {
//        return owner;
//    }

//    public Institution getInstitution() {
//        return institution;
//    }


    public String getName() {
        return name;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

//    public void setOwner(Person owner) {
//        this.owner = owner;
//    }

//    public void setInstitution(Institution institution) {
//        this.institution = institution;
//    }

    public void setName(String name) {
        this.name = name;
    }

    protected void setType(AccountType type) {
        this.type = type;
    }

    public void deposit(BigDecimal amount) {
        currentBalance = currentBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {

        currentBalance = currentBalance.subtract(amount);
    }

    protected void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

}


