package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class BrokerageAccount extends Account {

    @JsonCreator
    public BrokerageAccount(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, ownership, currentBalance, AccountType.BROKERAGE);
    }

    @Override
    public AccountType getType() {
        return AccountType.BROKERAGE;
    }
}
