package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederalTaxCalculatorTest {

    private FederalTaxCalculator calculator;
    private GovernmentRules rules;

    /*
    TaxIncome
   │
   ├─ Pension                     $18,000
   ├─ Social Security             $60,000
   └─ Tax-deferred withdrawal     $25,000
   │
   ▼
Taxable Social Security           $30,650
   │
   ▼
AGI                               $73,650
   │
   ▼
Standard deduction               -$32,200
   │
   ▼
Taxable income                    $41,450
   │
   ▼
Federal income tax                 $4,478
     */

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new FederalTaxCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void calculatesFederalTaxFromRetirementIncome() {

        /*
         * Household retirement income:
         *
         * Pension income                 $18,000
         * Social Security                $60,000
         * Tax-deferred withdrawals       $25,000
         */
        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("18000"),
                        new BigDecimal("60000"),
                        new BigDecimal("25000"));

        FederalTaxCalculation calculation =
                calculator.calculate(
                        taxIncome,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * Ordinary income before SS:
         *
         * $18,000 + $25,000
         * = $43,000
         *
         * Combined income:
         *
         * $43,000 + 50% of $60,000
         * = $73,000
         *
         * Taxable Social Security:
         *
         * First tier:
         * ($44,000 - $32,000) × 50%
         * = $6,000
         *
         * Second tier:
         * ($73,000 - $44,000) × 85%
         * = $24,650
         *
         * Total = $30,650
         */
        assertEquals(
                0,
                new BigDecimal("30650")
                        .compareTo(
                                calculation
                                        .getTaxableSocialSecurity()));

        /*
         * AGI:
         *
         * $43,000 ordinary income
         * + $30,650 taxable Social Security
         * = $73,650
         */
        assertEquals(
                0,
                new BigDecimal("73650")
                        .compareTo(
                                calculation
                                        .getAdjustedGrossIncome()));

        /*
         * 2026 MFJ standard deduction:
         *
         * $73,650 - $32,200
         * = $41,450 taxable income
         */
        assertEquals(
                0,
                new BigDecimal("41450")
                        .compareTo(
                                calculation
                                        .getTaxableIncome()));

        /*
         * Federal income tax:
         *
         * First $24,800 × 10%
         * = $2,480
         *
         * Remaining:
         *
         * $41,450 - $24,800
         * = $16,650
         *
         * $16,650 × 12%
         * = $1,998
         *
         * Total:
         *
         * $2,480 + $1,998
         * = $4,478
         */
        assertEquals(
                0,
                new BigDecimal("4478")
                        .compareTo(
                                calculation
                                        .getFederalIncomeTax()));
    }
}