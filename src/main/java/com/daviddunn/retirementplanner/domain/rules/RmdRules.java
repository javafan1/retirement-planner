package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class RmdRules {

    private final List<RmdStartingAgeRule> startingAgeRules;
    private final List<RmdLifeExpectancyFactor> uniformLifetimeTable;

    @JsonCreator
    public RmdRules(

            @JsonProperty("startingAgeRules")
            List<RmdStartingAgeRule> startingAgeRules,

            @JsonProperty("uniformLifetimeTable")
            List<RmdLifeExpectancyFactor> uniformLifetimeTable) {

        this.startingAgeRules =
                List.copyOf(
                        Objects.requireNonNull(
                                startingAgeRules,
                                "RMD starting age rules are required."));

        this.uniformLifetimeTable =
                List.copyOf(
                        Objects.requireNonNull(
                                uniformLifetimeTable,
                                "Uniform Lifetime Table is required."));

        if (this.startingAgeRules.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one RMD starting age rule is required.");
        }

        if (this.uniformLifetimeTable.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uniform Lifetime Table cannot be empty.");
        }
    }

    public List<RmdStartingAgeRule> getStartingAgeRules() {
        return startingAgeRules;
    }

    public List<RmdLifeExpectancyFactor> getUniformLifetimeTable() {
        return uniformLifetimeTable;
    }

    public int getRmdStartingAge(
            int birthYear) {

        return startingAgeRules
                .stream()
                .filter(rule ->
                        rule.appliesTo(birthYear))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No RMD starting age rule found for birth year: "
                                        + birthYear))
                .getRmdStartingAge();
    }

    public RmdLifeExpectancyFactor getLifeExpectancyFactor(
            int age) {

        return uniformLifetimeTable
                .stream()
                .filter(factor ->
                        factor.getAge() == age)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No RMD life expectancy factor found for age: "
                                        + age));
    }

    @Override
    public String toString() {

        return "RmdRules{" +
                "startingAgeRules=" +
                startingAgeRules +
                ", uniformLifetimeTable=" +
                uniformLifetimeTable +
                '}';
    }
}