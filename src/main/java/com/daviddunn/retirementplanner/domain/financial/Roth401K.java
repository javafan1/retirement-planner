package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class Roth401K extends Account {

    @JsonCreator
    public Roth401K(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, ownership, currentBalance, AccountType.ROTH_401K);
    }

    @Override
    public AccountType getType() {
        return AccountType.ROTH_401K;
    }

    @Override
    @JsonIgnore
    public boolean isEligibleForRothConversion() {
        return true;
    }
}
