package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonStage4ACoupleGoldenTest {
    @Test void probabilitiesExpectedPvAndWeightedEstateRemainExact() throws Exception {
        var p = LocalDate.of(1963, 1, 2);
        var s = LocalDate.of(1965, 1, 2);
        var date = LocalDate.of(2025, 1, 1);
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.of(new BigDecimal("0.8")), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.of(new BigDecimal("1.2")), date, table.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        var prepared = new HouseholdLongevityScenarioFactory(table).create(p, s, assumptions);
        var text = new StringBuilder();
        prepared.primary().distribution().probabilities().forEach(v -> text.append(v).append('\n'));
        prepared.spouse().distribution().probabilities().forEach(v -> text.append(v).append('\n'));
        prepared.scenarios().forEach(v -> text.append(v).append('\n'));
        var distribution = new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(80, new BigDecimal("0.25")),
                new SocialSecurityMortalityProbability(95, new BigDecimal("0.75"))));
        var base = new SocialSecurityStrategyRequest(date, null,
                new SocialSecurityClaimingElection(AccountOwnership.PRIMARY, p, new BigDecimal("3000"), 2025, p.plusYears(67)),
                new SocialSecurityClaimingElection(AccountOwnership.SPOUSE, s, new BigDecimal("1200"), 2025, s.plusYears(67)),
                p.plusYears(60), s.plusYears(60), p.plusYears(95), s.plusYears(95), new BigDecimal("0.02"));
        var ages = java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList();
        var grid = new SocialSecurityMortalityWeightedClaimingGridCalculator().calculate(
                new SocialSecurityMortalityWeightedClaimingGridRequest(base, ages, ages, distribution, distribution,
                        date, date, new BigDecimal("0.015")));
        assertEquals(81, grid.cells().size());
        grid.cells().forEach(v -> text.append(v).append('\n'));
        text.append(grid.highestExpectedPresentValue().highestCells()).append('\n');
        var plan = Stage4TestPlans.plan();
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(plan,
                List.of(new SocialSecurityMortalityProbability(71, new BigDecimal("0.25")),
                        new SocialSecurityMortalityProbability(74, new BigDecimal("0.75"))),
                List.of(new SocialSecurityMortalityProbability(75, BigDecimal.ONE)));
        var result = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, Stage4TestPlans.strategy(plan), scenarios,
                        LocalDate.of(2030, 1, 1), new BigDecimal("0.03")));
        text.append(LongevityWeightedStrategyAggregate.from(result)).append('\n');
        result.scenarioOutcomes().forEach(v -> text.append(v).append('\n'));
        var path = Path.of("src/test/resources/single-person-stage4a-couple-golden.txt");
        assertEquals(Files.readString(path).replace("\r\n", "\n"), text.toString());
    }
}
