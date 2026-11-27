package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.*;
import static com.daviddunn.retirementplanner.domain.model.DeathScenario.*;
import static org.junit.jupiter.api.Assertions.*;

class SpousalSuccessorOrdinaryWithdrawalTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "80565.20, 20000, 2434.80, 11074.36",
            "95565.20, 36148.40, 3583.20, 15629.04"
    })
    void beneficiaryEmployerPaysSeparateMandatorySpendingAndTaxDebits(
            String expenses, String spendingDraw, String premium, String solvedTax) {
        var plan = SpousalBeneficiary401kRmdExecutionTest.fixture("246000");
        var person = plan.getHousehold().getSpouse();
        var date = LocalDate.of(2035, 1, 1);
        person.replaceIncomeSource(person.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Spouse benefit", SPOUSE, person.getBirthDate().plusYears(67),
                        null, amount("2000"), 67, BigDecimal.ZERO, 2033));
        person.addIncomeSource(new Pension("Spouse pension", SPOUSE, date, null, amount("2000"), BigDecimal.ZERO));
        reserve(plan, "0");
        spend(plan, 2033, "31130.40");
        spend(plan, 2034, "21565.20");
        plan.getAccountPortfolio().getAccounts().get(1).setCurrentBalance(BigDecimal.ZERO);
        spend(plan, 2035, expenses);

        var projection = applied(plan);
        money("0", projection.getYearAt(0).getEndingRetainedNonQualifiedAssets());
        money("0", projection.getYearAt(1).getEndingRetainedNonQualifiedAssets());
        var year = projection.getYearAt(2);
        money("16.4", year.beneficiary401kRmds().getFirst().divisor());
        money("15000", year.getRequiredMinimumDistribution());
        money(spendingDraw, successor(year, SPOUSE, IncomeSourceAttribution.Purpose.CASH_FLOW));
        // Pension $24,000, taxable SS $20,400, RMD $15,000 and the ordinary spending draw.
        // After the $16,100 deduction, tax before funding is $8,638 or $12,190.648.
        // Solving x = tax before funding + .22x gives the two independent tax funding oracles.
        // In the second case, the base Medicare premium pushes final MAGI above $109,000.
        var taxDraw = successor(year, SPOUSE, IncomeSourceAttribution.Purpose.TAX_FUNDING);
        assertTrue(taxDraw.subtract(amount(solvedTax)).abs().compareTo(amount("0.05")) <= 0, taxDraw::toPlainString);
        assertTrue(taxDraw.subtract(year.getFederalIncomeTax()).abs().compareTo(amount("0.05")) <= 0);
        money("20400", year.getTaxableSocialSecurity());
        money(amount("59400").add(amount(spendingDraw)).add(taxDraw).toPlainString(), year.getModifiedAdjustedGrossIncome());
        money(premium, year.getAnnualMedicarePremium());
        money(amount("231000").subtract(amount(spendingDraw)).subtract(taxDraw).toPlainString(),
                year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("0", year.getEndingRetainedNonQualifiedAssets());
    }

    @Test
    void reverseDeathBeneficiaryIraCanDrawBeforeMandatoryCommencement() {
        var plan = SpousalBeneficiaryIraRmdExecutionTest.select(
                SpousalIraRmdExecutionTest.fixture(SPOUSE_DIES, 2034, "0", "500000", true));
        birth(plan, SPOUSE, LocalDate.of(1970, 6, 15));
        reserve(plan, "4869.60");
        spend(plan, 2035, "27565.20");
        var year = applied(plan).getYearAt(2);
        money("0", year.beneficiaryIraRmd().requiredAmount());
        money("0", year.getRequiredMinimumDistribution());
        money("30000", successor(year, PRIMARY, IncomeSourceAttribution.Purpose.CASH_FLOW));
        var taxDraw = successor(year, PRIMARY, IncomeSourceAttribution.Purpose.TAX_FUNDING);
        money(amount("470000").subtract(taxDraw).toPlainString(),
                year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        assertTrue(year.getIncomeSources().stream().noneMatch(row -> row.successorRmd().isPresent()));
    }

    @Test
    void validRolloverFundsOrdinarySpendingFromOriginalEmployerPosition() {
        var plan = SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2034, "500000", "0", false);
        birth(plan, PRIMARY, LocalDate.of(1970, 6, 15));
        var portfolio = plan.getAccountPortfolio();
        portfolio.replaceAccount(portfolio.getAccounts().getFirst(), new Traditional401K("Decedent plan", PRIMARY, amount("500000")));
        reserve(plan, "4869.60");
        spend(plan, 2035, "7565.20");

        var year = applied(plan).getYearAt(2);
        assertEquals(1, year.spousal401kRollovers().size());
        money("500000", year.spousal401kRollovers().getFirst().eligibleTransferredBalance());
        money("0", year.getRequiredMinimumDistribution());
        money("10000", successor(year, SPOUSE, IncomeSourceAttribution.Purpose.CASH_FLOW));
        money("490000", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("10000", year.getAdjustedGrossIncome());
        assertEquals(portfolio.getAccounts().size(), year.getEndingAccountSnapshots().size());
    }

    @Test
    void ownerHalfBirthdayWithinAnnualWindowRefusesWhileBeneficiaryMayDraw() {
        var plan = SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2034, "500000", "0", false);
        birth(plan, PRIMARY, LocalDate.of(1970, 6, 15));
        birth(plan, SPOUSE, LocalDate.of(1976, 1, 2));
        reserve(plan, "0");
        spend(plan, 2035, "10000");
        var result = assertThrows(SpousalIraRmdUnavailable.class, () -> applied(plan));
        assertEquals(SpousalIraRmdUnavailable.Reason.UNSUPPORTED_EARLY_DISTRIBUTION, result.reason());
        assertEquals(2035, result.year());
        birth(plan, SPOUSE, LocalDate.of(1975, 7, 1));
        money("10000", successor(applied(plan).getYearAt(2), SPOUSE, IncomeSourceAttribution.Purpose.CASH_FLOW));
        birth(plan, SPOUSE, LocalDate.of(1976, 1, 2));
        var beneficiary = SpousalBeneficiaryIraRmdExecutionTest.select(plan);
        money("10000", successor(applied(beneficiary).getYearAt(2), SPOUSE, IncomeSourceAttribution.Purpose.CASH_FLOW));
    }

    @Test
    void ownerIraPaysTwentyThousandMandatoryAndFortyThousandOrdinaryInReverseDeathOrder() {
        var plan = SpousalIraRmdExecutionTest.fixture(SPOUSE_DIES, 2034, "0", "492000", true);
        birth(plan, SPOUSE, LocalDate.of(1970, 6, 15));
        reserve(plan, "4869.60");
        spend(plan, 2035, "57565.20");
        var year = applied(plan).getYearAt(2);
        // Survivor turns 75 in 2035. IRS Uniform Lifetime divisor 24.6 gives $20,000 on $492,000.
        money("20000", year.getRequiredMinimumDistribution());
        money("40000", successor(year, PRIMARY, IncomeSourceAttribution.Purpose.CASH_FLOW));
        var taxes = successor(year, PRIMARY, IncomeSourceAttribution.Purpose.TAX_FUNDING);
        money(amount("432000").subtract(taxes).toPlainString(), year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money(amount("60000").add(taxes).toPlainString(), year.getAdjustedGrossIncome());
    }

    @Test
    void deferredBeneficiaryDebitsExhaustOnePositionThenContinueInExistingOrder() {
        var plan = SpousalIraRmdExecutionTest.fixture(SPOUSE_DIES, 2034, "1000", "1000", false);
        SpousalBeneficiaryIraRmdExecutionTest.select(plan);
        birth(plan, SPOUSE, LocalDate.of(1970, 6, 15));
        var portfolio = plan.getAccountPortfolio();
        portfolio.addAccount(new Traditional401K("Beneficiary plan", SPOUSE, amount("500000")));
        portfolio.addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Taxable reserve", JOINT, amount("1000")));
        reserve(plan, "4869.60");
        spend(plan, 2035, "7565.20");
        var year = applied(plan).getYearAt(2);
        money("0", year.beneficiary401kRmds().getFirst().requiredAmount());
        money("0", year.beneficiaryIraRmd().requiredAmount());
        money("8000", successor(year, PRIMARY, IncomeSourceAttribution.Purpose.CASH_FLOW));
        var rows = year.getIncomeSources().stream()
                .filter(row -> row.purpose() == IncomeSourceAttribution.Purpose.CASH_FLOW
                        && row.kind() != IncomeSourceAttribution.Kind.RMD && row.account().isPresent()).toList();
        assertEquals(java.util.List.of(AccountType.BROKERAGE, AccountType.TRADITIONAL_IRA,
                AccountType.TRADITIONAL_IRA, AccountType.TRADITIONAL_401K),
                rows.stream().map(row -> row.account().orElseThrow().type()).toList());
        money("0", year.getEndingAccountSnapshots().get(0).getEndingBalance());
        money("0", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("493000", year.getEndingAccountSnapshots().get(4).getEndingBalance());
    }

    @Test
    void ownIraCanBeExhaustedExactlyInYearWithNoRmd() {
        var plan = SpousalIraRmdExecutionTest.fixture(PRIMARY_DIES, 2034, "10000", "20000", false);
        birth(plan, PRIMARY, LocalDate.of(1970, 6, 15));
        reserve(plan, "4869.60");
        spend(plan, 2035, "7565.20");
        var year = applied(plan).getYearAt(2);
        money("0", year.getRequiredMinimumDistribution());
        money("10000", successor(year, SPOUSE, IncomeSourceAttribution.Purpose.CASH_FLOW));
        money("0", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("20000", year.getEndingAccountSnapshots().get(1).getEndingBalance());
        money("0", year.getTaxFundingWithdrawal());
    }

    static Projection applied(RetirementPlan plan) {
        return assertInstanceOf(ProjectionExecutionResult.Completed.class,
                new ProjectionEngine().projectSpousalSuccessorOrdinary(plan)).projection();
    }

    private static BigDecimal successor(ProjectionYear year, AccountOwnership recipient, IncomeSourceAttribution.Purpose purpose) {
        var rows = year.getIncomeSources().stream()
                .filter(row -> row.purpose() == purpose && row.successorOrdinary().isPresent()).toList();
        for (var row : rows) {
            assertEquals(recipient, row.owner().orElseThrow());
            assertNotEquals(recipient, row.account().orElseThrow().owner());
            assertEquals(year.getCalendarYear(), row.taxYear().orElseThrow());
            assertEquals(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, row.kind());
            assertEquals(row.grossAmount(), row.modeledTaxableAmount().orElseThrow());
        }
        return rows.stream().map(IncomeSourceAttribution::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void birth(RetirementPlan plan, AccountOwnership owner, LocalDate date) {
        var person = plan.getHousehold().peopleByOwner().get(owner);
        person.setBirthDate(date);
        person.replaceIncomeSource(person.getIncomeSources().getFirst(),
                new SocialSecurityIncome("Own benefit", owner, date.plusYears(70), null,
                        BigDecimal.ZERO, 70, BigDecimal.ZERO, 2033));
    }

    private static void reserve(RetirementPlan plan, String balance) {
        plan.getAccountPortfolio().getAccounts().stream().filter(account -> account.getType() == AccountType.CHECKING)
                .findFirst().orElseThrow().setCurrentBalance(amount(balance));
    }

    private static void spend(RetirementPlan plan, int year, String amount) {
        var date = LocalDate.of(year, 1, 1);
        plan.getHousehold().addExpense(new Expense("One-time spending", amount(amount), GrowthCategory.GENERAL,
                date, date, ExpenseType.ONE_TIME));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, amount(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
