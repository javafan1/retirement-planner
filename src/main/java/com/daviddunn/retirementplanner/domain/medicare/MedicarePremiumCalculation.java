package com.daviddunn.retirementplanner.domain.medicare;

import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;

import java.math.BigDecimal;
import java.util.Objects;

public record MedicarePremiumCalculation(

        BigDecimal modifiedAdjustedGrossIncome,

        IrmaaBracket irmaaBracket,

        int coveredMedicareParticipants,

        BigDecimal monthlyPartBPremium,

        BigDecimal annualPartBPremium,

        BigDecimal monthlyPartDPremium,

        BigDecimal annualPartDPremium,

        BigDecimal totalAnnualMedicarePremium) {

    public MedicarePremiumCalculation {

        Objects.requireNonNull(
                modifiedAdjustedGrossIncome,
                "Modified adjusted gross income is required.");

        Objects.requireNonNull(
                irmaaBracket,
                "IRMAA bracket is required.");

        Objects.requireNonNull(
                monthlyPartBPremium,
                "Monthly Part B premium is required.");

        Objects.requireNonNull(
                annualPartBPremium,
                "Annual Part B premium is required.");

        Objects.requireNonNull(
                monthlyPartDPremium,
                "Monthly Part D premium is required.");

        Objects.requireNonNull(
                annualPartDPremium,
                "Annual Part D premium is required.");

        Objects.requireNonNull(
                totalAnnualMedicarePremium,
                "Total annual Medicare premium is required.");

        Objects.requireNonNull(
                coveredMedicareParticipants,
                "Covered Medicare Participants is required.");
    }
}