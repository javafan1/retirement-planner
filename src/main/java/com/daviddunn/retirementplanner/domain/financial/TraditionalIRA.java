package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;

import java.math.BigDecimal;

public class TraditionalIRA extends Account {

    public TraditionalIRA() {
        super();
    }

    public TraditionalIRA(String name,
                          BigDecimal balance) {

        super(name,
                AccountType.TRADITIONAL_IRA,
                balance);
    }
}