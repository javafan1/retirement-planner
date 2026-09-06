package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityDeathAgeMatrixRequestTest {

    @Test
    void axesAreDefensivelyCopiedInCallerOrder() {
        List<Integer> primaryAges = new ArrayList<>(List.of(85, 80, 90));
        SocialSecurityDeathAgeMatrixRequest request = request(
                primaryAges,
                List.of(75, 95));

        primaryAges.clear();
        assertEquals(List.of(85, 80, 90), request.primaryDeathAges());
        assertThrows(UnsupportedOperationException.class,
                () -> request.primaryDeathAges().add(100));
    }

    @Test
    void emptyDuplicateAndOutOfRangeAxesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(), List.of(80)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(80), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(80, 85, 80), List.of(90)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(80), List.of(90, 90)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(0), List.of(90)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(80), List.of(121)));
    }

    private SocialSecurityDeathAgeMatrixRequest request(
            List<Integer> primaryAges,
            List<Integer> spouseAges) {
        SocialSecurityStrategyRequest strategy = strategy();
        return new SocialSecurityDeathAgeMatrixRequest(
                strategy,
                strategy,
                primaryAges,
                spouseAges,
                strategy.analysisDate(),
                new BigDecimal("0.01"));
    }

    private SocialSecurityStrategyRequest strategy() {
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2025, 1, 1),
                null,
                election(AccountOwnership.PRIMARY,
                        LocalDate.of(1963, 1, 2), 3000,
                        LocalDate.of(2030, 1, 2)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 1, 2), 1200,
                        LocalDate.of(2032, 1, 2)),
                LocalDate.of(2023, 1, 2),
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2053, 1, 2),
                LocalDate.of(2060, 1, 2),
                new BigDecimal("0.02"));
    }

    private SocialSecurityClaimingElection election(
            AccountOwnership owner,
            LocalDate birthDate,
            int benefit,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                owner,
                birthDate,
                BigDecimal.valueOf(benefit),
                2025,
                claimDate);
    }
}
