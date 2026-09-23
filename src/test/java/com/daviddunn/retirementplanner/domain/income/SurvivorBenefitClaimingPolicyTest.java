package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SurvivorBenefitClaimingPolicyTest {
    private final Person primary = new Person("David", "Test", LocalDate.of(1963, 6, 4));
    private final Person spouse = new Person("Lisa", "Test", LocalDate.of(1965, 2, 28));
    private final Household household = new Household(primary, spouse);

    @ParameterizedTest
    @CsvSource({"2024,60,67,false", "2026,60,67,false", "2028,62,67,false",
            "2030,64,67,false", "2032,66,67,false", "2033,67,67,true", "2037,71,71,true"})
    void primaryDeathConstrainsSpouseElection(int year, int first, int last, boolean immediate) {
        var choices = SurvivorBenefitClaimingPolicy.choices(household, DeathScenario.PRIMARY_DIES, year);
        assertSame(spouse, choices.survivor());
        assertEquals(first, choices.ages().getFirst());
        assertEquals(last, choices.ages().getLast());
        assertEquals(immediate, choices.immediateAtDeath());
        assertEquals(last - first + 1, choices.ages().size());
    }

    @ParameterizedTest
    @CsvSource({"2028,64,67,false", "2035,71,71,true"})
    void spouseDeathConstrainsPrimaryElection(int year, int first, int last, boolean immediate) {
        var choices = SurvivorBenefitClaimingPolicy.choices(household, DeathScenario.SPOUSE_DIES, year);
        assertSame(primary, choices.survivor());
        assertEquals(first, choices.ages().getFirst());
        assertEquals(last, choices.ages().getLast());
        assertEquals(immediate, choices.immediateAtDeath());
    }

    @Test void bothSurviveHasNoElection() {
        assertEquals(List.of(), SurvivorBenefitClaimingPolicy.choices(household, DeathScenario.BOTH_SURVIVE, null).ages());
    }

    @Test void dateIsNeverBeforeDeathAndLateDeathIsImmediateEvenForALaterStoredElection() {
        assertEquals(LocalDate.of(2030, 1, 1), SurvivorBenefitClaimingPolicy.claimDate(
                spouse.getBirthDate(), LocalDate.of(2030, 1, 1), 62));
        assertEquals(LocalDate.of(2032, 2, 28), SurvivorBenefitClaimingPolicy.claimDate(
                spouse.getBirthDate(), LocalDate.of(2030, 1, 1), 67));
        assertEquals(LocalDate.of(2033, 1, 1), SurvivorBenefitClaimingPolicy.claimDate(
                spouse.getBirthDate(), LocalDate.of(2033, 1, 1), 70));
    }
}
