package com.daviddunn.retirementplanner.account;

import com.daviddunn.retirementplanner.model.Person;
import java.math.BigDecimal;

public class RothIRA extends Account {

    public RothIRA(Person owner,
                   String name,
                   BigDecimal balance) {

        super(owner, name, balance);
    }
}