package com.daviddunn.retirementplanner.domain.estate;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.SavingsAccount;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterTaxEstateCalculatorTest {

    private final AfterTaxEstateCalculator calculator =
            new AfterTaxEstateCalculator();

    @Test
    void calculatesTaxOnTaxDeferredAssets() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedAccountSnapshot iraSnapshot =
                new ProjectedAccountSnapshot(
                        ira,
                        new BigDecimal("1000000"));

        BigDecimal tax =
                calculator.calculateEstimatedTax(
                        List.of(iraSnapshot),
                        new BigDecimal("0.25"));

        assertEquals(
                0,
                new BigDecimal("250000")
                        .compareTo(tax));
    }

    @Test
    void doesNotTaxRothTaxableOrCashAssets() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA roth =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000"));

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000"));

        SavingsAccount savings =
                new SavingsAccount(
                        "Savings",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        List<ProjectedAccountSnapshot> snapshots =
                List.of(
                        new ProjectedAccountSnapshot(
                                ira,
                                new BigDecimal("1000000")),

                        new ProjectedAccountSnapshot(
                                roth,
                                new BigDecimal("200000")),

                        new ProjectedAccountSnapshot(
                                brokerage,
                                new BigDecimal("300000")),

                        new ProjectedAccountSnapshot(
                                savings,
                                new BigDecimal("100000"))
                );

        BigDecimal tax =
                calculator.calculateEstimatedTax(
                        snapshots,
                        new BigDecimal("0.25"));

        assertEquals(
                0,
                new BigDecimal("250000")
                        .compareTo(tax));
    }

    @Test
    void zeroHeirTaxRateProducesZeroEstimatedTax() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedAccountSnapshot iraSnapshot =
                new ProjectedAccountSnapshot(
                        ira,
                        new BigDecimal("1000000"));

        BigDecimal tax =
                calculator.calculateEstimatedTax(
                        List.of(iraSnapshot),
                        BigDecimal.ZERO);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(tax));
    }

    @Test
    void calculatesAfterTaxEstateValue() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        RothIRA roth =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000"));

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000"));

        SavingsAccount savings =
                new SavingsAccount(
                        "Savings",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        ProjectionYear year =
                ProjectionYearBuilder
                        .aProjectionYear()
                        .withEndingInvestableAssets(1600000)
                        .withEndingAccountSnapshots(
                                List.of(
                                        new ProjectedAccountSnapshot(
                                                ira,
                                                new BigDecimal("1000000")),

                                        new ProjectedAccountSnapshot(
                                                roth,
                                                new BigDecimal("200000")),

                                        new ProjectedAccountSnapshot(
                                                brokerage,
                                                new BigDecimal("300000")),

                                        new ProjectedAccountSnapshot(
                                                savings,
                                                new BigDecimal("100000"))
                                ))
                        .build();

        BigDecimal estimatedHeirTax =
                calculator.calculateEstimatedTax(
                        year.getEndingAccountSnapshots(),
                        new BigDecimal("0.25"));

        BigDecimal afterTaxEstate =
                calculator.calculateAfterTaxEstateValue(
                        year.getEndingInvestableAssets(),
                        estimatedHeirTax);

        assertEquals(
                0,
                new BigDecimal("1350000")
                        .compareTo(afterTaxEstate));
    }
}