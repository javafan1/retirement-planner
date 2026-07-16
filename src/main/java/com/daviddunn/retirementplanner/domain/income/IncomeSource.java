package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "incomeType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Pension.class, name = "pension")
})
public abstract class IncomeSource {

    private String name;

    protected IncomeSource() {
    }

    protected IncomeSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

   @JsonIgnore
    public abstract BigDecimal getAnnualIncome();
}