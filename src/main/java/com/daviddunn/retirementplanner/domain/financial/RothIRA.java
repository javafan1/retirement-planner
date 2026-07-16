package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.Person;

import java.math.BigDecimal;

public class RothIRA extends Account {

    public RothIRA(Person david, String rothIra, BigDecimal bigDecimal) {
        super();
    }

    public RothIRA(String name,
                   BigDecimal balance) {

        super(name,
                AccountType.ROTH_IRA,
                balance);
    }
}