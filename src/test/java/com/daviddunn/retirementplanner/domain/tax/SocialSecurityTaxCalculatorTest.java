package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityTaxCalculatorTest {

    private SocialSecurityTaxCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new SocialSecurityTaxCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void combinedIncomeBelowFirstThresholdProducesNoTaxableBenefits() {

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("10000"),
                        new BigDecimal("20000"),
                        BigDecimal.ZERO);

        /*
         * MFJ combined income:
         *
         * Other income                 $10,000
         * 50% of Social Security        10,000
         *                              -------
         * Combined income              $20,000
         *
         * First threshold = $32,000.
         */

        BigDecimal taxableBenefits =
                calculator.calculateTaxableBenefits(
                        taxIncome,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        taxableBenefits));
    }

    @Test
    void combinedIncomeBetweenThresholdsUsesFiftyPercentTier() {

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("25000"),
                        new BigDecimal("20000"),
                        BigDecimal.ZERO);

        /*
         * MFJ combined income:
         *
         * Other income                 $25,000
         * 50% of Social Security        10,000
         *                              -------
         * Combined income              $35,000
         *
         * Amount above first threshold:
         *
         * $35,000 - $32,000 = $3,000
         *
         * Taxable benefits:
         *
         * $3,000 × 50% = $1,500
         */

        BigDecimal taxableBenefits =
                calculator.calculateTaxableBenefits(
                        taxIncome,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        assertEquals(
                0,
                new BigDecimal("1500.00")
                        .compareTo(
                                taxableBenefits));
    }

    @Test
    void combinedIncomeAboveSecondThresholdUsesEightyFivePercentTier() {

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("43000"),
                        new BigDecimal("60000"),
                        BigDecimal.ZERO);

        /*
         * MFJ combined income:
         *
         * Other income                 $43,000
         * 50% of Social Security        30,000
         *                              -------
         * Combined income              $73,000
         *
         * First tier:
         *
         * ($44,000 - $32,000) × 50%
         * = $6,000
         *
         * Second tier:
         *
         * ($73,000 - $44,000) × 85%
         * = $24,650
         *
         * Total taxable benefits:
         *
         * $6,000 + $24,650
         * = $30,650
         */

        BigDecimal taxableBenefits =
                calculator.calculateTaxableBenefits(
                        taxIncome,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        assertEquals(
                0,
                new BigDecimal("30650.00")
                        .compareTo(
                                taxableBenefits));
    }

    @Test
    void taxableBenefitsCannotExceedEightyFivePercentOfBenefits() {

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("200000"),
                        new BigDecimal("60000"),
                        BigDecimal.ZERO);

        /*
         * Maximum taxable Social Security:
         *
         * $60,000 × 85% = $51,000.
         */

        BigDecimal taxableBenefits =
                calculator.calculateTaxableBenefits(
                        taxIncome,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        assertEquals(
                0,
                new BigDecimal("51000.00")
                        .compareTo(
                                taxableBenefits));
    }
}