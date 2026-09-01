package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitSelection;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxDeferredFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingCalculator;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingResult;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.TaxIncomeCalculator;
import com.daviddunn.retirementplanner.domain.tax.SocialSecurityTaxCalculator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RothConversionBracketFillCalculatorTest {

    private RothConversionBracketFillCalculator calculator;

    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new RothConversionBracketFillCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void authoritativeSocialSecurityChangesConversionAndStillFillsTarget() {
        Person primary = new Person("Primary", "Person", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Person", LocalDate.of(1965, 2, 28));
        Household household = new Household(primary, spouse);
        BrokerageAccount brokerage = new BrokerageAccount(
                "Brokerage", AccountOwnership.PRIMARY, new BigDecimal("1000000"));
        ProjectedPortfolio portfolio = new ProjectedPortfolio(List.of(
                new ProjectedAccountBalance(brokerage, new BigDecimal("1000000"))));
        WithdrawalBreakdown withdrawals = new WithdrawalBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal target = new BigDecimal("100000");
        HouseholdSocialSecurityResult zero = HouseholdSocialSecurityResult.zero();
        HouseholdSocialSecurityResult auxiliary = new HouseholdSocialSecurityResult(
                new BigDecimal("20000"), BigDecimal.ZERO,
                new BigDecimal("20000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                SocialSecurityBenefitSelection.OWN,
                SocialSecurityBenefitSelection.NONE,
                new BigDecimal("40000"));

        BigDecimal zeroConversion = authoritativeConversion(
                household, portfolio, withdrawals, target, zero);
        BigDecimal auxiliaryConversion = authoritativeConversion(
                household, portfolio, withdrawals, target, auxiliary);
        BigDecimal repeatedAuxiliaryConversion = authoritativeConversion(
                household, portfolio, withdrawals, target, auxiliary);

        assertTrue(auxiliaryConversion.compareTo(zeroConversion) < 0);
        assertEquals(0, auxiliaryConversion.compareTo(repeatedAuxiliaryConversion));
        assertTarget(household, portfolio, withdrawals, target, zeroConversion, zero);
        assertTarget(household, portfolio, withdrawals, target,
                auxiliaryConversion, auxiliary);
    }

    @Test
    void fills22PercentBracketWhenTaxFundingUsesBrokerage() {

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

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        brokerage,
                                        new BigDecimal("1000000"))));

        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("25000"),
                        BigDecimal.ZERO);

        FederalTaxBracket targetBracket =
                new FederalTaxBracket(
                        new BigDecimal("100000"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal conversion =
                calculator.calculateConversion(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        new TaxableFirstWithdrawalStrategy(),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules,
                        targetBracket,
                        BigDecimal.ZERO);

        /*
         * Existing taxable income:
         *
         * Pension                 $18,000
         * IRA withdrawal           25,000
         *                         -------
         * AGI                     $43,000
         *
         * Standard deduction      -32,200
         *                         -------
         * Taxable income          $10,800
         *
         * Remaining room:
         *
         * $201,050 - $10,800
         * = $190,250
         */
        assertEquals(
                0,
                new BigDecimal("190250")
                        .compareTo(conversion));
    }

    @Test
    void fills22PercentBracketWhenTaxFundingUsesTraditionalIra() {

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

        Household household =
                new Household(
                        primary,
                        spouse);

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("1000000"))));

        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),
                        BigDecimal.ZERO);

        FederalTaxBracket targetBracket =
                new FederalTaxBracket(
                        new BigDecimal("100000"),
                        new BigDecimal("201050"),
                        new BigDecimal("0.22"));

        BigDecimal conversion =
                calculator.calculateConversion(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        new TaxDeferredFirstWithdrawalStrategy(),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules,
                        targetBracket,
                BigDecimal.ZERO);

        /*
         * The conversion must be less than the
         * initial bracket-room calculation because
         * the tax-funding withdrawal itself creates
         * additional taxable income.
         */
        assertTrue(
                conversion.compareTo(
                        new BigDecimal("201050")) < 0);

        /*
         * Verify that the resulting conversion,
         * when passed through the tax-funding
         * calculation, fills the target bracket.
         */
        TaxFundingCalculator taxFundingCalculator =
                new TaxFundingCalculator();

        TaxFundingResult result =
                taxFundingCalculator.calculate(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        new TaxDeferredFirstWithdrawalStrategy(),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules,
                        conversion,BigDecimal.ZERO);

        BigDecimal difference =
                targetBracket
                        .getUpperBound()
                        .subtract(
                                result
                                        .getFederalTaxCalculation()
                                        .getTaxableIncome())
                        .abs();

        assertTrue(
                difference.compareTo(
                        new BigDecimal("0.01")) <= 0);
    }

    @Test
    void reachesCustomTargetTaxableIncomeWhenTaxFundingUsesTraditionalIra() {

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

        Household household =
                new Household(
                        primary,
                        spouse);

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("1000000"))));

        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),
                        BigDecimal.ZERO);

        BigDecimal targetTaxableIncome =
                new BigDecimal("180000");

        BigDecimal conversion =
                calculator.calculateConversion(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        new TaxDeferredFirstWithdrawalStrategy(),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules,
                        targetTaxableIncome,
                        BigDecimal.ZERO);

        TaxFundingResult result =
                new TaxFundingCalculator()
                        .calculate(
                                household,
                                LocalDate.of(2026, 1, 1),
                                portfolio,
                                existingWithdrawals,
                                new TaxDeferredFirstWithdrawalStrategy(),
                                FilingStatus.MARRIED_FILING_JOINTLY,
                                rules,
                                conversion,
                                BigDecimal.ZERO);

        BigDecimal difference =
                targetTaxableIncome.subtract(
                        result
                                .getFederalTaxCalculation()
                                .getTaxableIncome())
                        .abs();

        assertTrue(
                difference.compareTo(
                        new BigDecimal("0.01")) <= 0);

        assertTrue(
                result.getAdditionalWithdrawal()
                        .compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void returnsZeroWhenTaxableIncomeAlreadyMeetsCustomTarget() {

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

        Household household =
                new Household(
                        primary,
                        spouse);

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("1000000"))));

        WithdrawalBreakdown existingWithdrawals =
                new WithdrawalBreakdown(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("100000"),
                        BigDecimal.ZERO);

        BigDecimal conversion =
                calculator.calculateConversion(
                        household,
                        LocalDate.of(2026, 1, 1),
                        portfolio,
                        existingWithdrawals,
                        new TaxDeferredFirstWithdrawalStrategy(),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules,
                        new BigDecimal("10000"),
                        BigDecimal.ZERO);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(conversion));
    }

    private BigDecimal authoritativeConversion(
            Household household,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown withdrawals,
            BigDecimal target,
            HouseholdSocialSecurityResult socialSecurity) {
        return calculator.calculateConversion(
                household, LocalDate.of(2026, 1, 1), portfolio, withdrawals,
                new TaxableFirstWithdrawalStrategy(),
                FilingStatus.MARRIED_FILING_JOINTLY, rules, target,
                BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
                socialSecurity);
    }

    private void assertTarget(
            Household household,
            ProjectedPortfolio portfolio,
            WithdrawalBreakdown withdrawals,
            BigDecimal target,
            BigDecimal conversion,
            HouseholdSocialSecurityResult socialSecurity) {
        TaxFundingResult result = new TaxFundingCalculator().calculate(
                household, LocalDate.of(2026, 1, 1), portfolio, withdrawals,
                new TaxableFirstWithdrawalStrategy(),
                FilingStatus.MARRIED_FILING_JOINTLY, rules, conversion,
                BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
                socialSecurity);
        assertTrue(target.subtract(result.getFederalTaxCalculation()
                .getTaxableIncome()).abs().compareTo(new BigDecimal("0.01")) <= 0);
        TaxIncome taxIncome = new TaxIncomeCalculator().calculate(
                household, LocalDate.of(2026, 1, 1), BigDecimal.ZERO,
                BigDecimal.ZERO, conversion, BigDecimal.ZERO, BigDecimal.ZERO,
                null, socialSecurity);
        assertEquals(0, socialSecurity.householdBenefit().compareTo(
                taxIncome.getSocialSecurityIncome()));
        BigDecimal taxableSocialSecurity = new SocialSecurityTaxCalculator()
                .calculateTaxableBenefits(
                        taxIncome, FilingStatus.MARRIED_FILING_JOINTLY, rules);
        assertEquals(0, taxableSocialSecurity.compareTo(
                result.getFederalTaxCalculation().getTaxableSocialSecurity()));
    }
}
