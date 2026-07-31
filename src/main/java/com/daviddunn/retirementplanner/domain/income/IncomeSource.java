package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
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
        @JsonSubTypes.Type(
                value = Pension.class,
                name = "pension"),

        @JsonSubTypes.Type(
                value = SocialSecurityIncome.class,
                name = "socialSecurity")
})
public abstract class IncomeSource {

    private final String name;

    private final AccountOwnership ownership;

    private final LocalDate startDate;

    private final LocalDate endDate;

    protected IncomeSource(
            @JsonProperty("name") String name,
            @JsonProperty("ownership") AccountOwnership ownership,
            @JsonProperty("startDate") LocalDate startDate,
            @JsonProperty("endDate") LocalDate endDate) {

        this.name = Objects.requireNonNull(name);
        this.ownership = Objects.requireNonNull(ownership);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = endDate;
    }

    @JsonIgnore
    protected int getActiveMonths(int calendarYear) {

        LocalDate yearStart =
                LocalDate.of(calendarYear, 1, 1);

        LocalDate yearEnd =
                LocalDate.of(calendarYear, 12, 31);

        if (startDate.isAfter(yearEnd)) {
            return 0;
        }

        if (endDate != null &&
                endDate.isBefore(yearStart)) {
            return 0;
        }

        LocalDate effectiveStart =
                startDate.isAfter(yearStart)
                        ? startDate
                        : yearStart;

        LocalDate effectiveEnd =
                endDate != null && endDate.isBefore(yearEnd)
                        ? endDate
                        : yearEnd;

        return effectiveEnd.getMonthValue()
                - effectiveStart.getMonthValue()
                + 1;
    }

    //protected abstract BigDecimal calculateAnnualIncome(LocalDate projectionDate);

    protected abstract BigDecimal calculateAnnualIncome(
            Person person,
            LocalDate projectionDate,
            int activeMonths);

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
    public final BigDecimal getAnnualIncome(
            Person person, LocalDate projectionDate) {

        Objects.requireNonNull(projectionDate);

        int activeMonths =
                getActiveMonths(
                        projectionDate.getYear());

        if (activeMonths == 0) {
            return BigDecimal.ZERO;
        }

        return calculateAnnualIncome(
                person,
                projectionDate,
                activeMonths);
    }
}