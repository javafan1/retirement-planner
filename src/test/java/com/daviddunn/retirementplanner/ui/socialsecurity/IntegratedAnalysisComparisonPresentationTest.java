package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegratedAnalysisComparisonPresentationTest {
    private IntegratedSocialSecurityCompleteStrategySearchResult deterministic() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        return new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(62), List.of(62),
                        standard.primarySurvivorCandidates().subList(0, 1), standard.spouseSurvivorCandidates().subList(0, 1),
                        standard.rankingMeasure(), 0));
    }
    @Test void compatibleResultsJoinAndCombineAllRoles() {
        var weighted = LongevityWeightedIntegratedPresentationTest.model(
                List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)),
                Optional.of(LongevityWeightedIntegratedPresentationTest.entry(0, "100", null)));
        var rows = IntegratedAnalysisComparisonPresentation.create(weighted, deterministic(), 7, 7, true);
        assertEquals(1, rows.size());
        assertEquals("Deterministic highest / Longevity-weighted highest / Current", rows.getFirst().role());
        assertTrue(rows.getFirst().deterministicRank().isPresent());
        assertTrue(rows.getFirst().weightedRank().isPresent());
        assertTrue(rows.getFirst().deterministicEstate().isPresent());
    }
    @Test void staleOrDifferentRevisionPreventsJoinWithoutDiscardingWeightedValues() {
        var weighted = LongevityWeightedIntegratedPresentationTest.model(
                List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100", 1)), Optional.empty());
        var deterministic = deterministic();
        for (var rows : List.of(
                IntegratedAnalysisComparisonPresentation.create(weighted, deterministic, 6, 7, true),
                IntegratedAnalysisComparisonPresentation.create(weighted, deterministic, 7, 8, true),
                IntegratedAnalysisComparisonPresentation.create(weighted, deterministic, 7, 7, false),
                IntegratedAnalysisComparisonPresentation.create(weighted, null, 7, 7, true))) {
            assertTrue(rows.getFirst().deterministicRank().isEmpty());
            assertTrue(rows.getFirst().weightedPv().isPresent());
        }
    }
    @Test void differentCompleteElectionsPreventUniverseJoin() {
        var weighted = LongevityWeightedIntegratedPresentationTest.model(
                List.of(LongevityWeightedIntegratedPresentationTest.entry(2, "100", 1)), Optional.empty());
        var rows = IntegratedAnalysisComparisonPresentation.create(weighted, deterministic(), 7, 7, true);
        assertTrue(rows.getFirst().deterministicEstate().isEmpty());
        assertEquals("Longevity-weighted highest", rows.getFirst().role());
    }
}
