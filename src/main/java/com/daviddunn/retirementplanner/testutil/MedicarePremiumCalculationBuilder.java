package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculation;
import com.daviddunn.retirementplanner.domain.rules.IrmaaBracket;

import java.math.BigDecimal;
import java.util.Objects;

public class MedicarePremiumCalculationBuilder {

    private BigDecimal modifiedAdjustedGrossIncome =
            BigDecimal.ZERO;

    private IrmaaBracket irmaaBracket =
            IrmaaBracketBuilder
                    .anIrmaaBracket()
                    .build();

    private int coveredMedicareParticipants = 1;

    private BigDecimal monthlyPartBPremium =
            BigDecimal.ZERO;

    private BigDecimal annualPartBPremium =
            BigDecimal.ZERO;

    private BigDecimal monthlyPartDPremium =
            BigDecimal.ZERO;

    private BigDecimal annualPartDPremium =
            BigDecimal.ZERO;

    private BigDecimal totalAnnualMedicarePremium =
            BigDecimal.ZERO;

    public static MedicarePremiumCalculationBuilder
    aMedicarePremiumCalculation() {

        return new MedicarePremiumCalculationBuilder();
    }

    private MedicarePremiumCalculationBuilder() {
    }

    public MedicarePremiumCalculationBuilder
    withModifiedAdjustedGrossIncome(long amount) {

        this.modifiedAdjustedGrossIncome =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withIrmaaBracket(
            IrmaaBracket bracket) {

        this.irmaaBracket =
                Objects.requireNonNull(bracket);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withCoveredMedicareParticipants(
            int participants) {

        this.coveredMedicareParticipants =
                participants;

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withMonthlyPartBPremium(long amount) {

        this.monthlyPartBPremium =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withAnnualPartBPremium(long amount) {

        this.annualPartBPremium =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withMonthlyPartDPremium(long amount) {

        this.monthlyPartDPremium =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withAnnualPartDPremium(long amount) {

        this.annualPartDPremium =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculationBuilder
    withTotalAnnualMedicarePremium(long amount) {

        this.totalAnnualMedicarePremium =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MedicarePremiumCalculation build() {

        return new MedicarePremiumCalculation(
                modifiedAdjustedGrossIncome,
                irmaaBracket,
                coveredMedicareParticipants,
                monthlyPartBPremium,
                annualPartBPremium,
                monthlyPartDPremium,
                annualPartDPremium,
                totalAnnualMedicarePremium);
    }
}