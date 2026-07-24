package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountTaxTreatmentTest {

    @Test
    void traditionalIraIsTaxDeferred() {

        Account account =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void rothIraIsRoth() {

        Account account =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.ROTH,
                account.getTaxTreatment());
    }

    @Test
    void rolloverIraIsTaxDeferred() {

        Account account =
                new RolloverIRA(
                        "Rollover IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void traditional401kIsTaxDeferred() {

        Account account =
                new Traditional401K(
                        "401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void traditional403bIsTaxDeferred() {

        Account account =
                new Traditional403B(
                        "403(b)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void roth401kIsRoth() {

        Account account =
                new Roth401K(
                        "Roth 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.ROTH,
                account.getTaxTreatment());
    }

    @Test
    void brokerageAccountIsTaxable() {

        Account account =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                TaxTreatment.TAXABLE,
                account.getTaxTreatment());
    }

    @Test
    void checkingAccountIsCash() {

        Account account =
                new CheckingAccount(
                        "Checking",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("25000"));

        assertEquals(
                TaxTreatment.CASH,
                account.getTaxTreatment());
    }

    @Test
    void savingsAccountIsCash() {

        Account account =
                new SavingsAccount(
                        "Savings",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000"));

        assertEquals(
                TaxTreatment.CASH,
                account.getTaxTreatment());
    }

    @Test
    void inheritedTraditionalIraIsTaxDeferred() {

        InheritedAccountInformation inheritedInfo =
                new InheritedAccountInformation(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        BeneficiaryRelationship.SIBLING);

        Account account =
                new InheritedTraditionalIRA(
                        "Inherited IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("34000"),
                        inheritedInfo);

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());

        assertEquals(
                AccountType.INHERITED_TRADITIONAL_IRA,
                account.getType());
    }

    @Test
    void inheritedRothIraIsRoth() {

        InheritedAccountInformation inheritedInfo =
                new InheritedAccountInformation(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        BeneficiaryRelationship.SIBLING);

        Account account =
                new InheritedRothIRA(
                        "Inherited Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("34000"),
                        inheritedInfo);

        assertEquals(
                TaxTreatment.ROTH,
                account.getTaxTreatment());

        assertEquals(
                AccountType.INHERITED_ROTH_IRA,
                account.getType());
    }

}