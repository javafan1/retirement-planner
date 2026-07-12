package com.daviddunn.retirementplanner.financial;

import com.daviddunn.retirementplanner.model.*;
import java.math.BigDecimal;

import  com.daviddunn.retirementplanner.model.AccountType;

public class RothIRA extends Account {

    public RothIRA(Person owner,
                   String name,
                   BigDecimal balance) {

        super(owner, name, AccountType.ROTH_IRA, balance);
    }
}