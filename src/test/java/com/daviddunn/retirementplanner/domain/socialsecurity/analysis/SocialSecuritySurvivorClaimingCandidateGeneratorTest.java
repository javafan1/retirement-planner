package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialSecuritySurvivorClaimingCandidateGeneratorTest {

    private final SocialSecuritySurvivorClaimingCandidateGenerator generator =
            new SocialSecuritySurvivorClaimingCandidateGenerator();

    @Test
    void wholeYearsPlusExactNonIntegerSurvivorFraAreOrderedAndUnique() {
        LocalDate birthDate = LocalDate.of(1958, 6, 4);
        List<SocialSecuritySurvivorClaimingCandidate> candidates =
                generator.generate(birthDate);

        assertEquals(8, candidates.size());
        assertEquals(LocalDate.of(2018, 6, 4), candidates.getFirst().claimDate());
        assertEquals(LocalDate.of(2024, 10, 4), candidates.getLast().claimDate());
        assertEquals("Age 66 years 4 months", candidates.getLast().label());
        assertTrue(candidates.getLast().wholeYearAge().isEmpty());
        assertEquals(candidates.size(), candidates.stream()
                .map(SocialSecuritySurvivorClaimingCandidate::claimDate)
                .distinct().count());
    }

    @Test
    void exactWholeYearFraAppearsOnlyOnce() {
        List<SocialSecuritySurvivorClaimingCandidate> candidates =
                generator.generate(LocalDate.of(1963, 6, 4));

        assertEquals(List.of(60, 61, 62, 63, 64, 65, 66, 67),
                candidates.stream().map(candidate ->
                        candidate.wholeYearAge().orElseThrow()).toList());
    }

    @Test
    void januaryFirstUsesExistingEffectiveBirthConvention() {
        List<SocialSecuritySurvivorClaimingCandidate> candidates =
                generator.generate(LocalDate.of(1963, 1, 1));

        assertEquals(LocalDate.of(2022, 12, 31),
                candidates.getFirst().claimDate());
        assertEquals(SocialSecuritySurvivorBenefitCalculator
                        .calculateSurvivorFullRetirementDate(
                                LocalDate.of(1963, 1, 1)),
                candidates.getLast().claimDate());
    }

    @Test
    void leapDayCandidatesRemainValidAndChronological() {
        List<SocialSecuritySurvivorClaimingCandidate> candidates =
                generator.generate(LocalDate.of(1964, 2, 29));

        assertEquals(LocalDate.of(2024, 2, 29), candidates.getFirst().claimDate());
        assertEquals(LocalDate.of(2031, 2, 28), candidates.getLast().claimDate());
        for (int index = 1; index < candidates.size(); index++) {
            assertTrue(candidates.get(index - 1).claimDate()
                    .isBefore(candidates.get(index).claimDate()));
        }
    }
}
