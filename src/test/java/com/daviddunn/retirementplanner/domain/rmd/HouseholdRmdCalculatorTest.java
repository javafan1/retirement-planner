package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
/*

That completes the household-level RMD calculation end to end

RetirementPlan
    │
    ├── Household
    │     ├── Primary DOB
    │     └── Spouse DOB
    │
    └── AccountPortfolio
           │
           ▼
HouseholdRmdCalculator
    │
    ├── Primary OwnerRmdResult
    │     ├── IRA RMD
    │     ├── 401(k) RMDs
    │     └── 403(b) RMDs
    │
    └── Spouse OwnerRmdResult
          ├── IRA RMD
          ├── 401(k) RMDs
          └── 403(b) RMDs
 */

class HouseholdRmdCalculatorTest {

    private HouseholdRmdCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new HouseholdRmdCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void calculatesPrimaryAndSpouseRmdsSeparately() {

        /*
         * Primary was born in 1959.
         * RMD starting age = 73.
         *
         * Spouse was born in 1960.
         * RMD starting age = 75.
         *
         * Projection year = 2032.
         *
         * Primary is subject to RMDs.
         * Spouse is not yet subject to RMDs.
         */

        Person primary =
                new Person(
                        "Primary",
                        "Person",
                        LocalDate.of(
                                1959,
                                6,
                                15));

        Person spouse =
                new Person(
                        "Spouse",
                        "Person",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("250000")));

        portfolio.addAccount(
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("600000")));

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.06"),
                        new BigDecimal("0.025"),
                        40,
                        LocalDate.of(
                                2026,
                                7,
                                1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        HouseholdRmdResult result =
                calculator.calculate(
                        plan,
                        2032,
                        rules);

        /*
         * Primary is age 73 during 2032.
         *
         * Age 73 divisor = 26.5
         *
         * IRA:
         * $500,000 / 26.5 = $18,867.92
         *
         * 401(k):
         * $250,000 / 26.5 = $9,433.96
         *
         * Total:
         * $28,301.88
         */

        assertEquals(
                0,
                new BigDecimal("18867.92")
                        .compareTo(
                                result
                                        .getPrimaryRmd()
                                        .getIraRmd()));

        assertEquals(
                0,
                new BigDecimal("9433.96")
                        .compareTo(
                                result
                                        .getPrimaryRmd()
                                        .getTraditional401kRmdTotal()));

        assertEquals(
                0,
                new BigDecimal("28301.88")
                        .compareTo(
                                result
                                        .getPrimaryRmd()
                                        .getTotalRmd()));

        /*
         * Spouse was born in 1960 and does
         * not begin owner RMDs until age 75.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result
                                .getSpouseRmd()
                                .getTotalRmd()));

        /*
         * Household total therefore equals
         * the primary owner's total.
         */
        assertEquals(
                0,
                new BigDecimal("28301.88")
                        .compareTo(
                                result.getTotalRmd()));
    }

    @Test
    void usesSnapshotBalancesForEntireHouseholdRmd() {

        /*
         * Primary born in 1960:
         * RMD starting age = 75.
         *
         * Projection year 2035 = age 75.
         */
        Person primary =
                new Person(
                        "Primary",
                        "Person",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Person spouse =
                new Person(
                        "Spouse",
                        "Person",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        TraditionalIRA primaryIra =
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000"));

        TraditionalIRA spouseIra =
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("400000"));

        portfolio.addAccount(primaryIra);
        portfolio.addAccount(spouseIra);

        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.06"),
                        new BigDecimal("0.025"),
                        40,
                        LocalDate.of(
                                2026,
                                7,
                                1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        /*
         * Actual account balances:
         *
         * Primary = $500,000
         * Spouse  = $400,000
         *
         * Now create very different projected
         * December 31, 2034 balances.
         */
        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);

        projectedPortfolio =
                projectedPortfolio.withBalance(
                        primaryIra,
                        new BigDecimal("1000000"));

        projectedPortfolio =
                projectedPortfolio.withBalance(
                        spouseIra,
                        new BigDecimal("750000"));

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        HouseholdRmdResult result =
                calculator.calculate(
                        plan,
                        snapshot,
                        2035,
                        rules);

        /*
         * Age 75 divisor = 24.6
         *
         * Primary:
         *
         * $1,000,000 / 24.6
         * = $40,650.41
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                result
                                        .getPrimaryRmd()
                                        .getTotalRmd()));

        /*
         * Spouse:
         *
         * $750,000 / 24.6
         * = $30,487.80
         */
        assertEquals(
                0,
                new BigDecimal("30487.80")
                        .compareTo(
                                result
                                        .getSpouseRmd()
                                        .getTotalRmd()));

        /*
         * Household:
         *
         * $40,650.41
         * +30,487.80
         * ----------
         * $71,138.21
         */
        assertEquals(
                0,
                new BigDecimal("71138.21")
                        .compareTo(
                                result.getTotalRmd()));

        /*
         * And verify that running the calculation
         * did not mutate the actual plan.
         */
        assertEquals(
                0,
                new BigDecimal("500000")
                        .compareTo(
                                primaryIra.getCurrentBalance()));

        assertEquals(
                0,
                new BigDecimal("400000")
                        .compareTo(
                                spouseIra.getCurrentBalance()));
    }
    @Test
    void rmdCalculationContinuesAfterPrimaryDeath() {

        /*
         * Primary:
         * Born in 1960.
         * RMD starting age = 75.
         *
         * Spouse:
         * Born in 1960.
         * RMD starting age = 75.
         *
         * Primary dies in 2035.
         *
         * MVP assumption:
         * RMD calculations continue using
         * the existing owner-based calculation
         * after death.
         */
        Person primary =
                new Person(
                        "Primary",
                        "Person",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Person spouse =
                new Person(
                        "Spouse",
                        "Person",
                        LocalDate.of(
                                1960,
                                6,
                                15));

        Household household =
                new Household(
                        primary,
                        spouse);

        AccountPortfolio portfolio =
                new AccountPortfolio();

        TraditionalIRA primaryIra =
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000"));

        TraditionalIRA spouseIra =
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("400000"));

        portfolio.addAccount(
                primaryIra);

        portfolio.addAccount(
                spouseIra);

        /*
         * Primary dies in 2035.
         *
         * Survivor claiming age is required by
         * DeathScenarioAssumptions but does not
         * affect the RMD calculation in this MVP.
         */
        PlanningAssumptions assumptions =
                new PlanningAssumptions(
                        new EconomicAssumptions(
                                new BigDecimal("0.06"),
                                new BigDecimal("0.025")),

                        new TaxAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO),

                        new WithdrawalAssumptions(
                                WithdrawalStrategyType.TAXABLE_FIRST),

                        new DeathScenarioAssumptions(
                                DeathScenario.PRIMARY_DIES,
                                2035,
                                67),

                        40,
                        LocalDate.of(
                                2026,
                                7,
                                1));

        RetirementPlan plan =
                new RetirementPlan(
                        household,
                        portfolio,
                        assumptions);

        /*
         * Snapshot representing December 31, 2034.
         *
         * Primary = $1,000,000
         * Spouse  = $400,000
         */
        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);

        projectedPortfolio =
                projectedPortfolio.withBalance(
                        primaryIra,
                        new BigDecimal("1000000"));

        projectedPortfolio =
                projectedPortfolio.withBalance(
                        spouseIra,
                        new BigDecimal("400000"));

        RmdBalanceSnapshot deathYearSnapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        /*
         * 2035 is the primary's death year.
         *
         * Both spouses are age 75.
         *
         * Primary:
         * $1,000,000 / 24.6 = $40,650.41
         *
         * Spouse:
         * $400,000 / 24.6 = $16,260.16
         */
        HouseholdRmdResult deathYearResult =
                calculator.calculate(
                        plan,
                        deathYearSnapshot,
                        2035,
                        rules);

        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                deathYearResult
                                        .getPrimaryRmd()
                                        .getTotalRmd()));

        assertEquals(
                0,
                new BigDecimal("16260.16")
                        .compareTo(
                                deathYearResult
                                        .getSpouseRmd()
                                        .getTotalRmd()));

        /*
         * Create the following year's snapshot.
         *
         * For this regression test we intentionally
         * keep the balances the same. We are testing
         * whether the death scenario changes the RMD
         * calculation, not investment growth or
         * account withdrawals.
         */
        RmdBalanceSnapshot postDeathSnapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2035,
                                12,
                                31),
                        projectedPortfolio);

        /*
         * 2036 is the first year after death.
         *
         * MVP behavior:
         * Continue the existing owner-based RMD
         * calculation.
         *
         * Primary age = 76
         * Distribution period = 23.7
         *
         * $1,000,000 / 23.7 = $42,194.09
         *
         * Spouse age = 76
         *
         * $400,000 / 23.7 = $16,877.64
         */
        HouseholdRmdResult postDeathResult =
                calculator.calculate(
                        plan,
                        postDeathSnapshot,
                        2036,
                        rules);

        assertEquals(
                0,
                new BigDecimal("42194.09")
                        .compareTo(
                                postDeathResult
                                        .getPrimaryRmd()
                                        .getTotalRmd()));

        assertEquals(
                0,
                new BigDecimal("16877.64")
                        .compareTo(
                                postDeathResult
                                        .getSpouseRmd()
                                        .getTotalRmd()));
    }

}