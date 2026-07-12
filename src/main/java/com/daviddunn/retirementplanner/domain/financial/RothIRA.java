package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.*;
import java.math.BigDecimal;

import com.daviddunn.retirementplanner.domain.model.AccountType;

public class RothIRA extends Account {

    public RothIRA(Person owner,
                   String name,
                   BigDecimal balance) {

        super(owner, name, AccountType.ROTH_IRA, balance);
    }
}