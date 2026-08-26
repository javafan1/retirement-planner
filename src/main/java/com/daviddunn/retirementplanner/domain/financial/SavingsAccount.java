package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class SavingsAccount extends Account {

    @JsonCreator
    public SavingsAccount(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, ownership, currentBalance, AccountType.SAVINGS);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }
}
