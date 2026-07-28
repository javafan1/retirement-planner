package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
Pension                       $18,000
Existing IRA withdrawal        25,000
                              -------
AGI                            43,000

Standard deduction            -32,200
                              -------
Taxable income                 10,800

Initial federal tax             1,080
 */

class TaxFundingCalculatorTest {

    private TaxFundingCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new TaxFundingCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void taxDeferredFundingAccountsForTaxCreatedByTaxWithdrawal() {

        Person primary =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1500"),
                        BigDecimal.ZERO));

        Person spouse =
                new Person(
                        "Lisa",
                        "Dunn",
                        LocalDate.of(1965, 2, 28));

        Household household =
                new Household(
                        primary,
                        spouse);

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("975000"))));

        /*
         * The household has already withdrawn
         * $25,000 from the Traditional IRA for
         * normal spending.
         */
        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("25000"),
                        BigDecimal.ZERO);

        WithdrawalStrategy withdrawalStrategy =
                new TaxDeferredFirstWithdrawalStrategy();

        TaxFundingResult result =
                calculator.calculate(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        withdrawalStrategy,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * The tax-funding withdrawal itself comes
         * from the Traditional IRA.
         *
         * Therefore the final tax must be greater
         * than the tax calculated from the initial
         * $25,000 withdrawal alone.
         */
        assertTrue(
                result.getAdditionalWithdrawal()
                        .signum() > 0);

        /*
         * At convergence, the amount withdrawn to
         * pay federal tax should approximately equal
         * the resulting federal income tax.
         */
        BigDecimal difference =
                result
                        .getAdditionalWithdrawal()
                        .subtract(
                                result
                                        .getFederalTaxCalculation()
                                        .getFederalIncomeTax())
                        .abs();

        assertTrue(
                difference.compareTo(
                        new BigDecimal("0.01")) <= 0);
    }

    @Test
    void brokerageFundingDoesNotCreateAdditionalOrdinaryIncomeTax() {

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

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1500"),
                        BigDecimal.ZERO));

        Household household =
                new Household(
                        primary,
                        spouse);

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        brokerage,
                                        new BigDecimal("100000"))));

        /*
         * $25,000 has already been withdrawn from
         * a Traditional IRA earlier in the year.
         */
        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("25000"),
                        BigDecimal.ZERO);

        WithdrawalStrategy withdrawalStrategy =
                new TaxableFirstWithdrawalStrategy();

        TaxFundingResult result =
                calculator.calculate(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        withdrawalStrategy,
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * Pension                       $18,000
         * IRA withdrawal                25,000
         *                               -------
         * AGI                            43,000
         *
         * Standard deduction           -32,200
         *                               -------
         * Taxable income                10,800
         *
         * Federal income tax             1,080
         *
         * The $1,080 tax-funding withdrawal
         * comes from the brokerage account.
         *
         * Under our current MVP model, that
         * withdrawal does not add ordinary income.
         */
        assertEquals(
                0,
                new BigDecimal("1080")
                        .compareTo(
                                result.getAdditionalWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("1080")
                        .compareTo(
                                result
                                        .getFederalTaxCalculation()
                                        .getFederalIncomeTax()));
    }
}