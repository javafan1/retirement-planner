package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;

import java.math.BigDecimal;
import java.util.Objects;

public final class MedicarePremiumCalculator {

    private static final BigDecimal
            MONTHS_PER_YEAR =
            BigDecimal.valueOf(12);

    public MedicarePremiumCalculation calculate(

            FederalTaxCalculation federalTaxCalculation,

            FilingStatus filingStatus,

            GovernmentRules governmentRules,

            int coveredIndividuals) {

        Objects.requireNonNull(
                federalTaxCalculation,
                "Federal tax calculation is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (coveredIndividuals < 0) {
            throw new IllegalArgumentException(
                    "Covered individuals cannot be negative.");
        }

        BigDecimal modifiedAdjustedGrossIncome =
                federalTaxCalculation
                        .getAdjustedGrossIncome();

        IrmaaBracket bracket =
                governmentRules
                        .getIrmaaRules()
                        .getBracket(
                                filingStatus,
                                modifiedAdjustedGrossIncome);

        BigDecimal participantCount =
                BigDecimal.valueOf(coveredIndividuals);

        BigDecimal monthlyPartBPremium =
                bracket.getMonthlyPartBPremium()
                        .multiply(participantCount);

        BigDecimal annualPartBPremium =
                monthlyPartBPremium
                        .multiply(MONTHS_PER_YEAR);

        BigDecimal monthlyPartDPremium =
                bracket.getMonthlyPartDPremium()
                        .multiply(participantCount);

        BigDecimal annualPartDPremium =
                monthlyPartDPremium
                        .multiply(MONTHS_PER_YEAR);

        BigDecimal totalAnnualMedicarePremium =
                annualPartBPremium.add(
                        annualPartDPremium);

        return new MedicarePremiumCalculation(
                modifiedAdjustedGrossIncome,
                bracket,
                participantCount.intValue(),
                monthlyPartBPremium,
                annualPartBPremium,
                monthlyPartDPremium,
                annualPartDPremium,
                totalAnnualMedicarePremium);
    }
}