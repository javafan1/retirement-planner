package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxIncomeCalculatorTest {

    @Test
    void separatesPensionSocialSecurityAndTaxDeferredWithdrawals() {

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        /*
         * Primary pension:
         *
         * $1,000/month × 12 = $12,000
         */
        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1000"),
                        BigDecimal.ZERO));

        /*
         * Spouse pension:
         *
         * $500/month × 12 = $6,000
         */
        spouse.addIncomeSource(
                new Pension(
                        "Spouse Pension",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("500"),
                        BigDecimal.ZERO));

        /*
         * Primary Social Security:
         *
         * Full retirement benefit = $3,000/month.
         * Claiming at age 67 means 100% of the
         * full retirement benefit.
         *
         * $3,000 × 12 = $36,000.
         */
        primary.addIncomeSource(
                new SocialSecurityIncome(
                        "Primary Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO));

        /*
         * Spouse Social Security:
         *
         * Full retirement benefit = $2,000/month.
         * Claiming at age 67.
         *
         * $2,000 × 12 = $24,000.
         */
        spouse.addIncomeSource(
                new SocialSecurityIncome(
                        "Spouse Social Security",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("2000"),
                        67,
                        BigDecimal.ZERO));

        /*
         * We'll add Social Security after verifying
         * its constructor matches the current model.
         */

        Household household =
                new Household(
                        primary,
                        spouse);

        TaxIncomeCalculator calculator =
                new TaxIncomeCalculator();

        TaxIncome taxIncome =
                calculator.calculate(
                        household,
                        LocalDate.of(2026, 1, 1),
                        new BigDecimal("25000"),BigDecimal.ZERO,BigDecimal.ZERO);

        assertEquals(
                0,
                new BigDecimal("18000")
                        .compareTo(
                                taxIncome.getPensionIncome()));

        assertEquals(
                0,
                new BigDecimal("60000")
                        .compareTo(
                                taxIncome
                                        .getSocialSecurityIncome()));

        assertEquals(
                0,
                new BigDecimal("25000")
                        .compareTo(
                                taxIncome
                                        .getTaxDeferredWithdrawals()));

        /*
         * $18,000 pensions
         * + $25,000 tax-deferred withdrawals
         * -----------------------------------
         * $43,000 ordinary income before
         * Social Security.
         */
        assertEquals(
                0,
                new BigDecimal("43000")
                        .compareTo(
                                taxIncome
                                        .getOrdinaryIncomeBeforeSocialSecurity()));
    }


}