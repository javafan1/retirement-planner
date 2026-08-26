package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public class InheritedTraditionalIRA extends Account {

    private final InheritedAccountInformation inheritedAccountInformation;

    @JsonCreator
    public InheritedTraditionalIRA(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("currentBalance") BigDecimal currentBalance,
            @JsonProperty("inheritedAccountInformation")
            InheritedAccountInformation inheritedAccountInformation) {

        super(name, ownership, currentBalance, AccountType.INHERITED_TRADITIONAL_IRA);

        this.inheritedAccountInformation =
                Objects.requireNonNull(
                        inheritedAccountInformation,
                        "Inherited account information is required.");
    }

    @Override
    public AccountType getType() {
        return AccountType.INHERITED_TRADITIONAL_IRA;
    }

    public InheritedAccountInformation getInheritedAccountInformation() {
        return inheritedAccountInformation;
    }
}
