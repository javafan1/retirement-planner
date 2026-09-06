package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityClaimingGridRequestTest {

    @Test
    void axesAreDefensivelyCopiedAndCallerOrderIsPreserved() {
        List<Integer> primaryAges = new ArrayList<>(List.of(70, 62, 67));
        SocialSecurityClaimingGridRequest request = request(
                primaryAges,
                List.of(62, 70));

        primaryAges.clear();
        assertEquals(List.of(70, 62, 67), request.primaryClaimAges());
        assertThrows(UnsupportedOperationException.class,
                () -> request.primaryClaimAges().add(65));
    }

    @Test
    void invalidDuplicateNullAndEmptyAxesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(), List.of(62)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(62), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(61), List.of(62)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(62), List.of(71)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(62, 67, 62), List.of(67)));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(62), List.of(67, 67)));
        assertThrows(NullPointerException.class,
                () -> request(java.util.Arrays.asList(62, null), List.of(67)));
    }

    private SocialSecurityClaimingGridRequest request(
            List<Integer> primaryAges,
            List<Integer> spouseAges) {
        SocialSecurityStrategyRequest strategy = strategy();
        return new SocialSecurityClaimingGridRequest(
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
                        LocalDate.of(1963, 6, 4), 3000,
                        LocalDate.of(2030, 6, 4)),
                election(AccountOwnership.SPOUSE,
                        LocalDate.of(1965, 2, 28), 1200,
                        LocalDate.of(2032, 2, 28)),
                LocalDate.of(2023, 6, 4),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2048, 6, 4),
                LocalDate.of(2060, 2, 28),
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
