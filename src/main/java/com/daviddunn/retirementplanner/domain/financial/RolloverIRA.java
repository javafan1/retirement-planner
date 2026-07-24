package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class RolloverIRA extends Account {

    @JsonCreator
    public RolloverIRA(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, ownership, currentBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.ROLLOVER_IRA;
    }
}