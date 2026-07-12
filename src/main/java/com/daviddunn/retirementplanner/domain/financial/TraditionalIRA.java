package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import java.math.BigDecimal;

public class TraditionalIRA extends Account {

    public TraditionalIRA(Person owner,
                          String name,
                          BigDecimal balance) {

        super(owner,
                name,
                AccountType.TRADITIONAL_IRA,
                balance);
    }
}