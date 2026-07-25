package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;
import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccountFactoryTest {

    @Test
    void factoryCreatesRolloverIra() {

        Account account =
                AccountFactory.create(
                        AccountType.ROLLOVER_IRA,
                        "Rollover IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.ROLLOVER_IRA,
                account.getType());

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesTraditional401k() {

        Account account =
                AccountFactory.create(
                        AccountType.TRADITIONAL_401K,
                        "401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.TRADITIONAL_401K,
                account.getType());

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesTraditional403b() {

        Account account =
                AccountFactory.create(
                        AccountType.TRADITIONAL_403B,
                        "403(b)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.TRADITIONAL_403B,
                account.getType());

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesRoth401k() {

        Account account =
                AccountFactory.create(
                        AccountType.ROTH_401K,
                        "Roth 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.ROTH_401K,
                account.getType());

        assertEquals(
                TaxTreatment.ROTH,
                account.getTaxTreatment());
    }

    @Test
    void supportedTypesContainImplementedAccountTypes() {

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.TRADITIONAL_IRA));

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.ROLLOVER_IRA));

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.TRADITIONAL_401K));

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.TRADITIONAL_403B));

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.ROTH_IRA));

        assertTrue(
                AccountFactory.getSupportedTypes()
                        .contains(AccountType.ROTH_401K));
    }

    @Test
    void factoryCreatesBrokerageAccount() {

        Account account =
                AccountFactory.create(
                        AccountType.BROKERAGE,
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.BROKERAGE,
                account.getType());

        assertEquals(
                TaxTreatment.TAXABLE,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesCheckingAccount() {

        Account account =
                AccountFactory.create(
                        AccountType.CHECKING,
                        "Checking",
                        AccountOwnership.JOINT,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.CHECKING,
                account.getType());

        assertEquals(
                TaxTreatment.CASH,
                account.getTaxTreatment());
    }
    @Test
    void factoryCreatesSavingsAccount() {

        Account account =
                AccountFactory.create(
                        AccountType.SAVINGS,
                        "Savings",
                        AccountOwnership.JOINT,
                        new BigDecimal("100000"));

        assertEquals(
                AccountType.SAVINGS,
                account.getType());

        assertEquals(
                TaxTreatment.CASH,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesInheritedTraditionalIra() {

        InheritedAccountInformation inheritedInfo =
                new InheritedAccountInformation(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        BeneficiaryRelationship.SIBLING);

        Account account =
                AccountFactory.createInherited(
                        AccountType.INHERITED_TRADITIONAL_IRA,
                        "Inherited Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("34000"),
                        inheritedInfo);

        assertEquals(
                AccountType.INHERITED_TRADITIONAL_IRA,
                account.getType());

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                account.getTaxTreatment());
    }

    @Test
    void factoryCreatesInheritedRothIra() {

        InheritedAccountInformation inheritedInfo =
                new InheritedAccountInformation(
                        LocalDate.of(1960, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        BeneficiaryRelationship.SIBLING);

        Account account =
                AccountFactory.createInherited(
                        AccountType.INHERITED_ROTH_IRA,
                        "Inherited Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("34000"),
                        inheritedInfo);

        assertEquals(
                AccountType.INHERITED_ROTH_IRA,
                account.getType());

        assertEquals(
                TaxTreatment.ROTH,
                account.getTaxTreatment());
    }
}
