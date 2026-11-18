package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.AlabamaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlabamaPensionPreflightTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void narrowsReviewToUnclassifiedPositivePensionReceipts(boolean couple) {
        var plan = AlabamaProjectionAcceptanceTest.plan(couple);
        var household = plan.getHousehold();
        var primary = household.getPrimaryPerson();
        assertTrue(AlabamaPensionPreflight.pensionsToReview(household, plan.getPlanningAssumptions()).isEmpty());

        var first = pension("First", AccountOwnership.PRIMARY, 2027, null, "2500", null);
        var second = pension("Second", couple ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY,
                2029, null, "2500", null);
        var db = pension("DB", AccountOwnership.PRIMARY, 2027, null, "2500", null);
        db.setPlanClassification(PensionPlanClassification.DEFINED_BENEFIT_414J);
        var dc = pension("DC", AccountOwnership.PRIMARY, 2027, null, "2500", null);
        dc.setPlanClassification(PensionPlanClassification.DEFINED_CONTRIBUTION);
        for (var pension : List.of(first, db, dc,
                pension("Expired", AccountOwnership.PRIMARY, 2020, 2026, "2500", null),
                pension("Future", AccountOwnership.PRIMARY, 2030, null, "2500", null),
                pension("Zero", AccountOwnership.PRIMARY, 2027, null, "0", null))) {
            primary.addIncomeSource(pension);
        }
        (couple ? household.getSpouse() : primary).addIncomeSource(second);
        assertEquals(List.of(first, second),
                AlabamaPensionPreflight.pensionsToReview(household, plan.getPlanningAssumptions()));
        assertEquals(PensionPlanClassification.UNKNOWN, first.getPlanClassification());
        assertEquals(PensionPlanClassification.UNKNOWN, second.getPlanClassification());

        var failure = assertThrows(StateTaxUnavailableException.class, () -> new ProjectionEngine().project(plan));
        assertTrue(AlabamaPensionPreflight.explainsFailure(new IllegalStateException(failure)));
        assertTrue(AlabamaPensionPreflight.pensionsToReview(household, plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.michigan())).isEmpty());
    }

    @Test
    void excludesDeceasedOwnersWithoutPaymentsAndIncludesDirectSurvivorReceipts() {
        var plan = AlabamaProjectionAcceptanceTest.plan(true);
        var household = plan.getHousehold();
        var primary = household.getPrimaryPerson();
        var inactive = pension("Stops at death", AccountOwnership.PRIMARY, 2028, null, "2500", null);
        var survivor = pension("Survivor receipt", AccountOwnership.PRIMARY, 2029, null, "2500", "1000");
        primary.addIncomeSource(inactive);
        primary.addIncomeSource(survivor);
        var before = plan.getPlanningAssumptions();
        var after = new PlanningAssumptions(before.getEconomicAssumptions(), before.getTaxAssumptions(),
                before.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2028, 67, BigDecimal.ONE),
                before.getProjectionLengthYears(), before.getProjectionStartDate(), before.getStateTaxAssumptions());
        assertEquals(List.of(survivor), AlabamaPensionPreflight.pensionsToReview(household, after));
    }

    private static Pension pension(String name, AccountOwnership owner, int startYear, Integer endYear,
                                   String monthly, String survivorMonthly) {
        return new Pension(name, owner, LocalDate.of(startYear, 1, 1),
                endYear == null ? null : LocalDate.of(endYear, 12, 31),
                new BigDecimal(monthly), BigDecimal.ZERO,
                survivorMonthly == null ? null : new BigDecimal(survivorMonthly));
    }
}
