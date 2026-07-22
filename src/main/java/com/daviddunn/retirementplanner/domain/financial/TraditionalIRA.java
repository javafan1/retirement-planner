package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.PersonRole;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class TraditionalIRA extends Account {

    @JsonCreator
    public TraditionalIRA(
            @JsonProperty("name") String name,
            @JsonProperty("owner") PersonRole owner,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, owner, currentBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.TRADITIONAL_IRA;
    }
}