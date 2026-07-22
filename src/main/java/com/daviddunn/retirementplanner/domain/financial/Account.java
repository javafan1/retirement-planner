package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.Objects;

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

    private final String name;
    private final AccountOwnership ownership;
    private BigDecimal currentBalance;

    @JsonCreator
    protected Account(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        this.name = Objects.requireNonNull(name, "name");
        this.ownership = Objects.requireNonNullElse(ownership, AccountOwnership.PRIMARY);
        this.currentBalance = Objects.requireNonNull(currentBalance, "currentBalance");
    }

    public String getName() {
        return name;
    }

    public AccountOwnership getOwnership() {
        return ownership;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = Objects.requireNonNull(currentBalance, "currentBalance");
    }

    public void deposit(BigDecimal amount) {

        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative.");
        }

        currentBalance = currentBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {

        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Withdrawal amount cannot be negative.");
        }

        currentBalance = currentBalance.subtract(amount);
    }

    @JsonIgnore
    public abstract AccountType getType();
}