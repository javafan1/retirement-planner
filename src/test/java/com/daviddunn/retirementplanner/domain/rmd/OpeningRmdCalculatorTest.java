package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.Traditional403B;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpeningRmdCalculatorTest {

    private GovernmentRules rules;
    private final OpeningRmdCalculator calculator = new OpeningRmdCalculator();

    @BeforeEach
    void setUp() throws Exception {
        rules = new GovernmentRulesRepository().load(
                "/rules/government-rules-2026.json");
    }

    @Test
    void calculatesAnnualAndRemainingIraRmdFromOpeningData() {
        TraditionalIRA firstIra = ira("First IRA", "600000", "5000");
        TraditionalIRA secondIra = ira("Second IRA", "400000", "10000");

        OpeningRmdCalculation calculation = calculator.calculate(
                plan(firstIra, secondIra), 2026, rules);

        BigDecimal annual = new BigDecimal("1000000")
                .divide(new BigDecimal("23.7"), 2, RoundingMode.HALF_UP);

        assertMoney(annual,
                calculation.getAnnualRequirement().getPrimaryRmd().getIraRmd());
        assertMoney(new BigDecimal("15000"),
                calculation.getDistributedBeforeProjection());
        assertMoney(annual.subtract(new BigDecimal("15000")),
                calculation.getRemainingRequirement().getPrimaryRmd().getIraRmd());
    }

    @Test
    void retainsEmployerPlanRmdsAsAccountSpecificObligations() {
        Traditional401K account401k = new Traditional401K(
                "401(k)", AccountOwnership.PRIMARY, new BigDecimal("450000"));
        account401k.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("500000"), new BigDecimal("10000")));

        Traditional403B account403b = new Traditional403B(
                "403(b)", AccountOwnership.PRIMARY, new BigDecimal("225000"));
        account403b.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal("250000"), BigDecimal.ZERO));

        OpeningRmdCalculation calculation = calculator.calculate(
                plan(account401k, account403b), 2026, rules);

        BigDecimal annual401k = new BigDecimal("500000")
                .divide(new BigDecimal("23.7"), 2, RoundingMode.HALF_UP);
        BigDecimal annual403b = new BigDecimal("250000")
                .divide(new BigDecimal("23.7"), 2, RoundingMode.HALF_UP);

        assertMoney(annual401k,
                calculation.getAnnualRequirement().getPrimaryRmd()
                        .getTraditional401kRmds().getFirst().getAmount());
        assertMoney(annual401k.subtract(new BigDecimal("10000")),
                calculation.getRemainingRequirement().getPrimaryRmd()
                        .getTraditional401kRmds().getFirst().getAmount());
        assertMoney(annual403b,
                calculation.getRemainingRequirement().getPrimaryRmd()
                        .getTraditional403bRmds().getFirst().getAmount());
    }

    @Test
    void requiresOpeningDataForEligibleOwnerButNotForYoungerOwner() {
        TraditionalIRA eligibleIra = new TraditionalIRA(
                "Eligible IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> calculator.calculate(plan(eligibleIra), 2026, rules));

        assertEquals(true, exception.getMessage().contains(
                "Opening RMD information is required"));

        Person primary = new Person("Primary", "Person", LocalDate.of(1965, 1, 1));
        Household household = new Household(primary,
                new Person("Spouse", "Person", LocalDate.of(1965, 1, 1)));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(eligibleIra);
        RetirementPlan youngerPlan = new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 2,
                        LocalDate.of(2026, 7, 1)));

        assertMoney(BigDecimal.ZERO,
                calculator.calculate(youngerPlan, 2026, rules)
                        .getAnnualRequirement().getTotalRmd());
    }

    private TraditionalIRA ira(String name, String priorBalance, String alreadyTaken) {
        TraditionalIRA account = new TraditionalIRA(
                name, AccountOwnership.PRIMARY, new BigDecimal("900000"));
        account.setOpeningRmdAccountData(new OpeningRmdAccountData(
                2026, new BigDecimal(priorBalance), new BigDecimal(alreadyTaken)));
        return account;
    }

    private RetirementPlan plan(com.daviddunn.retirementplanner.domain.financial.Account... accounts) {
        Household household = new Household(
                new Person("Primary", "Person", LocalDate.of(1950, 1, 1)),
                new Person("Spouse", "Person", LocalDate.of(1965, 1, 1)));
        AccountPortfolio portfolio = new AccountPortfolio();
        for (var account : accounts) {
            portfolio.addAccount(account);
        }
        return new RetirementPlan(household, portfolio,
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 2,
                        LocalDate.of(2026, 7, 1)));
    }

    private static void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
