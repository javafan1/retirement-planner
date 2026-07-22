//package com.daviddunn.retirementplanner.domain.financial;
//
//import com.daviddunn.retirementplanner.domain.model.AccountType;
//import com.daviddunn.retirementplanner.domain.model.Person;
//
//import java.math.BigDecimal;
//
//public class RothIRA extends Account {
//
////    public RothIRA( String rothIra, BigDecimal bigDecimal) {
////        super();
////    }
//
//    protected RothIRA() {
//        super();
//    }
//
//    public RothIRA(String name,
//                   BigDecimal balance) {
//
//        super(name,
//                AccountType.ROTH_IRA,
//                balance);
//    }
//}

package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.PersonRole;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class RothIRA extends Account {

    @JsonCreator
    public RothIRA(
            @JsonProperty("name") String name,
            @JsonProperty("owner") PersonRole owner,
            @JsonProperty("currentBalance") BigDecimal currentBalance) {

        super(name, owner, currentBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.ROTH_IRA;
    }
}