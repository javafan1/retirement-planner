package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;
import com.daviddunn.retirementplanner.domain.rules.IrmaaRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IrmaaRulesBuilder {

    private final List<IrmaaBracket> brackets =
            new ArrayList<>();

    private boolean customBracketsAdded;

    public static IrmaaRulesBuilder anIrmaaRules() {
        return new IrmaaRulesBuilder();
    }

    private IrmaaRulesBuilder() {

        /*
         * Provide a default standard Medicare
         * premium bracket so simple tests require
         * no additional setup.
         */
        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .build());
    }

    public IrmaaRulesBuilder withBracket(
            IrmaaBracket bracket) {

        Objects.requireNonNull(
                bracket,
                "IRMAA bracket is required.");

        /*
         * Remove the default bracket the first
         * time the caller supplies one.
         */
        if (!customBracketsAdded) {

            brackets.clear();
            customBracketsAdded = true;
        }

        brackets.add(bracket);

        return this;
    }

    public IrmaaRulesBuilder withBrackets(
            List<IrmaaBracket> brackets) {

        Objects.requireNonNull(
                brackets,
                "IRMAA brackets are required.");

        this.brackets.clear();
        this.brackets.addAll(brackets);

        customBracketsAdded = true;

        return this;
    }

    /**
     * Adds the official Married Filing Jointly
     * IRMAA brackets. This will eventually be
     * populated with the Medicare values for
     * the supported rules year.
     */
    public IrmaaRulesBuilder withDefaultMarriedJointBrackets() {

        brackets.clear();

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(0)
                        .withMaximumIncome(212000)
                        .withMonthlyPartBPremium("185.00")
                        .withMonthlyPartDPremium("0.00")
                        .build());

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(212001)
                        .withMaximumIncome(266000)
                        .withMonthlyPartBPremium("259.00")
                        .withMonthlyPartDPremium("13.70")
                        .build());

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(266001)
                        .withMaximumIncome(334000)
                        .withMonthlyPartBPremium("370.00")
                        .withMonthlyPartDPremium("35.30")
                        .build());

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(334001)
                        .withMaximumIncome(400000)
                        .withMonthlyPartBPremium("480.90")
                        .withMonthlyPartDPremium("57.00")
                        .build());

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(400001)
                        .withMaximumIncome(750000)
                        .withMonthlyPartBPremium("591.90")
                        .withMonthlyPartDPremium("78.60")
                        .build());

        brackets.add(
                IrmaaBracketBuilder
                        .anIrmaaBracket()
                        .withMinimumIncome(750001)
                        .withNoMaximumIncome()
                        .withMonthlyPartBPremium("628.90")
                        .withMonthlyPartDPremium("85.80")
                        .build());

        customBracketsAdded = true;

        return this;
    }

    public IrmaaRules build() {

        return new IrmaaRules(
                List.copyOf(brackets));
    }
}