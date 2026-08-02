package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.rules.RmdLifeExpectancyFactor;
import com.daviddunn.retirementplanner.domain.rules.RmdRules;
import com.daviddunn.retirementplanner.domain.rules.RmdStartingAgeRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RmdRulesBuilder {

    private final List<RmdStartingAgeRule> startingAgeRules =
            new ArrayList<>();

    private final List<RmdLifeExpectancyFactor> uniformLifetimeTable =
            new ArrayList<>();

    private boolean customStartingAgeRules;

    private boolean customLifeExpectancyFactors;

    public static RmdRulesBuilder anRmdRules() {
        return new RmdRulesBuilder();
    }

    private RmdRulesBuilder() {

        /*
         * Default starting age rule.
         */
        startingAgeRules.add(
                new RmdStartingAgeRule(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        75));

        /*
         * Default life expectancy factor.
         */
        uniformLifetimeTable.add(
                new RmdLifeExpectancyFactor(
                        75,
                        new BigDecimal("24.6")));
    }

    public RmdRulesBuilder withStartingAgeRule(
            RmdStartingAgeRule rule) {

        Objects.requireNonNull(rule);

        if (!customStartingAgeRules) {
            startingAgeRules.clear();
            customStartingAgeRules = true;
        }

        startingAgeRules.add(rule);

        return this;
    }

    public RmdRulesBuilder withStartingAgeRule(
            int firstBirthYear,
            int lastBirthYear,
            int rmdStartingAge) {

        return withStartingAgeRule(
                new RmdStartingAgeRule(
                        firstBirthYear,
                        lastBirthYear,
                        rmdStartingAge));
    }

    public RmdRulesBuilder withLifeExpectancyFactor(
            RmdLifeExpectancyFactor factor) {

        Objects.requireNonNull(factor);

        if (!customLifeExpectancyFactors) {
            uniformLifetimeTable.clear();
            customLifeExpectancyFactors = true;
        }

        uniformLifetimeTable.add(factor);

        return this;
    }

    public RmdRulesBuilder withLifeExpectancyFactor(
            int age,
            String factor) {

        return withLifeExpectancyFactor(
                new RmdLifeExpectancyFactor(
                        age,
                        new BigDecimal(factor)));
    }

    public RmdRules build() {

        return new RmdRules(
                List.copyOf(startingAgeRules),
                List.copyOf(uniformLifetimeTable));
    }
}