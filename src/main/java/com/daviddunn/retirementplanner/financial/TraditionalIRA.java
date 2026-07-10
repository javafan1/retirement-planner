package com.daviddunn.retirementplanner.financial;

import com.daviddunn.retirementplanner.model.Person;
import com.daviddunn.retirementplanner.model.financial.AccountType;
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