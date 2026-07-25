package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountTypeTest {

    @Test
    void traditionalRetirementAccountsAreSubjectToOwnerRmd() {

        assertTrue(
                AccountType.TRADITIONAL_IRA
                        .isSubjectToOwnerRmd());

        assertTrue(
                AccountType.ROLLOVER_IRA
                        .isSubjectToOwnerRmd());

        assertTrue(
                AccountType.TRADITIONAL_401K
                        .isSubjectToOwnerRmd());

        assertTrue(
                AccountType.TRADITIONAL_403B
                        .isSubjectToOwnerRmd());
    }

    @Test
    void rothAccountsAreNotSubjectToOwnerRmd() {

        assertFalse(
                AccountType.ROTH_IRA
                        .isSubjectToOwnerRmd());

        assertFalse(
                AccountType.ROTH_401K
                        .isSubjectToOwnerRmd());
    }

    @Test
    void inheritedAccountsDoNotUseOwnerRmdRules() {

        assertFalse(
                AccountType.INHERITED_TRADITIONAL_IRA
                        .isSubjectToOwnerRmd());

        assertFalse(
                AccountType.INHERITED_ROTH_IRA
                        .isSubjectToOwnerRmd());
    }

    @Test
    void nonRetirementAccountsAreNotSubjectToOwnerRmd() {

        assertFalse(
                AccountType.BROKERAGE
                        .isSubjectToOwnerRmd());

        assertFalse(
                AccountType.CHECKING
                        .isSubjectToOwnerRmd());

        assertFalse(
                AccountType.SAVINGS
                        .isSubjectToOwnerRmd());
    }

    @Test
    void accountTypesHaveCorrectTaxTreatment() {

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                AccountType.TRADITIONAL_IRA
                        .getTaxTreatment());

        assertEquals(
                TaxTreatment.TAX_DEFERRED,
                AccountType.ROLLOVER_IRA
                        .getTaxTreatment());

        assertEquals(
                TaxTreatment.ROTH,
                AccountType.ROTH_IRA
                        .getTaxTreatment());

        assertEquals(
                TaxTreatment.TAXABLE,
                AccountType.BROKERAGE
                        .getTaxTreatment());

        assertEquals(
                TaxTreatment.CASH,
                AccountType.CHECKING
                        .getTaxTreatment());
    }

    @Test
    void iraTypesHaveIraRmdCategory() {

        assertEquals(
                RmdAccountCategory.IRA,
                AccountType.TRADITIONAL_IRA
                        .getRmdAccountCategory());

        assertEquals(
                RmdAccountCategory.IRA,
                AccountType.ROLLOVER_IRA
                        .getRmdAccountCategory());
    }

    @Test
    void nonRmdAccountsHaveNoRmdCategory() {

        assertEquals(
                RmdAccountCategory.NOT_APPLICABLE,
                AccountType.ROTH_IRA
                        .getRmdAccountCategory());

        assertEquals(
                RmdAccountCategory.NOT_APPLICABLE,
                AccountType.INHERITED_TRADITIONAL_IRA
                        .getRmdAccountCategory());

        assertEquals(
                RmdAccountCategory.NOT_APPLICABLE,
                AccountType.BROKERAGE
                        .getRmdAccountCategory());
    }
}