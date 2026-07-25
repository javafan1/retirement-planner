package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class RmdLifeExpectancyFactor {

    private final int age;
    private final BigDecimal distributionPeriod;

    @JsonCreator
    public RmdLifeExpectancyFactor(

            @JsonProperty("age")
            int age,

            @JsonProperty("distributionPeriod")
            BigDecimal distributionPeriod) {

        if (age <= 0) {
            throw new IllegalArgumentException(
                    "Age must be greater than zero.");
        }

        this.distributionPeriod =
                Objects.requireNonNull(
                        distributionPeriod,
                        "Distribution period is required.");

        if (distributionPeriod.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Distribution period must be greater than zero.");
        }

        this.age =
                age;
    }

    public int getAge() {
        return age;
    }

    public BigDecimal getDistributionPeriod() {
        return distributionPeriod;
    }

    @Override
    public String toString() {

        return "RmdLifeExpectancyFactor{" +
                "age=" +
                age +
                ", distributionPeriod=" +
                distributionPeriod +
                '}';
    }
}