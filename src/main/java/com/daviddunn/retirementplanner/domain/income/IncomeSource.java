package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "incomeType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Pension.class, name = "pension")
})
public abstract class IncomeSource {

    private final String name;

    private final AccountOwnership ownership;

    private final LocalDate startDate;

    private final LocalDate endDate;

    protected IncomeSource(
            @JsonProperty("name") String name,
            @JsonProperty("owner")  AccountOwnership ownership,
            @JsonProperty("startDate") LocalDate startDate,
            @JsonProperty("endDate") LocalDate endDate) {

        this.name = Objects.requireNonNull(name);
        this.ownership = Objects.requireNonNull(ownership);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = endDate;
    }

    public String getName() {
        return name;
    }

    public AccountOwnership getOwnership() {
        return ownership;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @JsonIgnore
    public boolean isActive(LocalDate date) {

        Objects.requireNonNull(date);

        if (date.isBefore(startDate)) {
            return false;
        }

        return endDate == null || !date.isAfter(endDate);
    }

    @JsonIgnore
    public abstract BigDecimal getAnnualIncome(LocalDate projectionDate);
}