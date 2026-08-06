package com.daviddunn.retirementplanner.testing;

import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.withdrawal.RothConversionStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static PlanningAssumptions planningAssumptions() {

        return planningAssumptions(
                RothConversionStrategy.NONE);
    }

    public static PlanningAssumptions planningAssumptions(
            RothConversionStrategy rothConversionStrategy) {

        return new PlanningAssumptions(

                new EconomicAssumptions(
                        new BigDecimal("0.07"),
                        new BigDecimal("0.025")),

                new TaxAssumptions(
                        new BigDecimal("0.025"),
                        new BigDecimal("0.025"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),

                new WithdrawalAssumptions(
                        WithdrawalStrategyType.TAXABLE_FIRST,
                        rothConversionStrategy),

                30,

                LocalDate.of(
                        2026,
                        1,
                        1));
    }

    public static FederalTaxRules
    marriedFilingJointlyFederalTaxRules() {

        return new FederalTaxRules(

                FilingStatus.MARRIED_FILING_JOINTLY,

                new BigDecimal("30000"),

                List.of(

                        new FederalTaxBracket(
                                BigDecimal.ZERO,
                                new BigDecimal("24800"),
                                new BigDecimal("0.10")),

                        new FederalTaxBracket(
                                new BigDecimal("24800"),
                                new BigDecimal("100800"),
                                new BigDecimal("0.12")),

                        new FederalTaxBracket(
                                new BigDecimal("100800"),
                                new BigDecimal("211400"),
                                new BigDecimal("0.22")),

                        new FederalTaxBracket(
                                new BigDecimal("211400"),
                                new BigDecimal("403550"),
                                new BigDecimal("0.24")),

                        new FederalTaxBracket(
                                new BigDecimal("403550"),
                                null,
                                new BigDecimal("0.32"))));
    }
}