package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdLifetimeScenarioMapperTest {
    @Test
    void birthdayOutcomesMapToCalendarYearWithoutChangingSourceOrProbability() {
        var primary = SocialSecurityDeathDateCalculator.calculateDeathDate(LocalDate.of(1960, 2, 29), 71);
        var spouse = SocialSecurityDeathDateCalculator.calculateDeathDate(LocalDate.of(1961, 9, 15), 70);
        var source = new SocialSecurityJointMortalityScenario(71, 70, primary, spouse,
                new BigDecimal("0.5"), new BigDecimal("0.4"), new BigDecimal("0.2"));
        var result = new HouseholdLifetimeScenarioMapper().map(source);
        assertEquals(LocalDate.of(2031, 2, 28), source.primaryDeathDate());
        assertEquals(Year.of(2031), result.primaryDeathYear().orElseThrow());
        assertEquals(LocalDate.of(2031, 1, 1), result.primaryDeathDate().orElseThrow());
        assertEquals(result.primaryDeathDate(), result.spouseDeathDate());
        assertEquals(new BigDecimal("0.2"), source.jointProbability());
        assertThrows(NullPointerException.class, () -> new HouseholdLifetimeScenarioMapper().map(null));
    }
}
