package com.daviddunn.retirementplanner.account;

import com.daviddunn.retirementplanner.model.Person;
import java.math.BigDecimal;

public class TraditionalIRA extends Account {

    public TraditionalIRA(Person owner,
                          String accountName,
                          BigDecimal balance) {

        super(owner, accountName, balance);
    }
}