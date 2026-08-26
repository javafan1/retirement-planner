package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "accountType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TraditionalIRA.class, name = "traditional"),
        @JsonSubTypes.Type(value = RothIRA.class, name = "roth"),
        @JsonSubTypes.Type(value = Traditional401K.class, name = "traditional401k"),
        @JsonSubTypes.Type(
                value = Traditional403B.class,
                name = "traditional403b"),
        @JsonSubTypes.Type(
                value = Roth401K.class,
                name = "roth401k"),
        @JsonSubTypes.Type(
                value = BrokerageAccount.class,
                name = "brokerage"),

        @JsonSubTypes.Type(value = RolloverIRA.class, name = "rollover"),

        @JsonSubTypes.Type(
                value = CheckingAccount.class,
                name = "checking"),

        @JsonSubTypes.Type(
                value = SavingsAccount.class,
                name = "savings"),

        @JsonSubTypes.Type(
                value = InheritedTraditionalIRA.class,
                name = "inheritedTraditional"),

        @JsonSubTypes.Type(
                value = InheritedRothIRA.class,
                name = "inheritedRoth")

})
public abstract class Account {

    private final String name;
    private final AccountOwnership ownership;
    private BigDecimal currentBalance;
    private OpeningRmdAccountData openingRmdAccountData;

    protected Account(
            String name,
            AccountOwnership ownership,
            BigDecimal currentBalance,
            AccountType accountType) {

        this.name = Objects.requireNonNull(name, "name");
        this.ownership = Objects.requireNonNullElse(ownership, AccountOwnership.PRIMARY);
        this.currentBalance = Objects.requireNonNull(currentBalance, "currentBalance");

        if (!accountType.allowsOwnership(this.ownership)) {
            throw new IllegalArgumentException(
                    accountType + " must be owned by PRIMARY or SPOUSE; JOINT ownership is not permitted.");
        }
    }

    public String getName() {
        return name;
    }

    public AccountOwnership getOwnership() {
        return ownership;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = Objects.requireNonNull(currentBalance, "currentBalance");
    }

    public OpeningRmdAccountData getOpeningRmdAccountData() {
        return openingRmdAccountData;
    }

    public void setOpeningRmdAccountData(
            OpeningRmdAccountData openingRmdAccountData) {

        if (openingRmdAccountData != null
                && !getType().isSubjectToOwnerRmd()) {
            throw new IllegalArgumentException(
                    "Opening RMD information is only valid for owner-RMD-eligible retirement accounts.");
        }

        this.openingRmdAccountData = openingRmdAccountData;
    }

    public void deposit(BigDecimal amount) {

        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative.");
        }

        currentBalance = currentBalance.add(amount);
    }

    public void withdraw(BigDecimal amount) {

        Objects.requireNonNull(amount, "amount");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Withdrawal amount cannot be negative.");
        }

        currentBalance = currentBalance.subtract(amount);
    }

    @JsonIgnore
    public abstract AccountType getType();

    @JsonIgnore
    public TaxTreatment getTaxTreatment() {
        return getType().getTaxTreatment();
    }

    @JsonIgnore
    public boolean isEligibleForRothConversion() {
        return false;
    }

    @JsonIgnore
    public ProjectionAssetType getProjectionAssetType() {
        return getType().getProjectionAssetType();
    }
}
