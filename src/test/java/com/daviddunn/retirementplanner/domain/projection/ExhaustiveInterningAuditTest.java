package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Historical Gate 3B comparison keeps the explicit unoptimized representation as its measurement baseline. */
class ExhaustiveInterningAuditTest {
    @Test void syntheticMetricReuseIsNotRepresentativeOfIndependentlyCalculatedRetirementPairs() throws Exception {
        var synthetic = (ExhaustiveSnapshot) ExhaustiveCapacityInvestigationTest.fixture(21).payload();
        assertEquals(5184, synthetic.entries().size());
        assertEquals(1, synthetic.entries().stream().map(ExhaustiveSnapshot.Entry::metrics).distinct().count());
        assertEquals(1, synthetic.entries().stream().map(ExhaustiveSnapshot.Entry::differences).distinct().count());
        assertEquals(81, synthetic.entries().stream().map(e -> List.of(e.strategy().primaryAge(), e.strategy().spouseAge())).distinct().count());

        var plan = ExhaustiveSnapshotTest.plan(40);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 2, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1962, 6, 1));
        plan.getAccountPortfolio().getAccounts().forEach(a -> a.setOpeningRmdAccountData(null));
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse()))
            for (var source : List.copyOf(person.getIncomeSources())) if (source instanceof SocialSecurityIncome ss)
                person.replaceIncomeSource(ss, new SocialSecurityIncome(ss.getName(), ss.getOwnership(), person.getBirthDate().plusYears(67),
                        null, ss.getFullRetirementMonthlyBenefit(), 67, BigDecimal.ZERO, 2026));
        var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(62, 70), List.of(62, 70),
                standard.primarySurvivorCandidates().subList(0, 2), standard.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 20);
        var actual = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request);
        assertEquals(16, actual.totalStrategyCount()); assertEquals(16, actual.successfulStrategyCount());
        long uniqueMetrics = actual.entries().stream().map(e -> e.metrics().orElseThrow()).distinct().count();
        long uniqueDifferences = actual.entries().stream().map(e -> e.differencesFromCurrentPlan().orElseThrow()).distinct().count();
        assertTrue(uniqueMetrics > 1, "Actual distinct retirement dates must not all be replaced with one metric record");
        assertTrue(uniqueDifferences > 1);
        var lastSynthetic = synthetic.entries().stream().filter(e -> e.strategy().primaryAge() == 70 && e.strategy().spouseAge() == 70
                && e.strategy().primarySurvivor().equals(standard.primarySurvivorCandidates().getFirst())
                && e.strategy().spouseSurvivor().equals(standard.spouseSurvivorCandidates().getFirst())).findFirst().orElseThrow();
        var actualLast = actual.entryFor(lastSynthetic.strategy().restore()).orElseThrow();
        assertNotEquals(0, actualLast.metrics().orElseThrow().afterTaxEstate().compareTo(lastSynthetic.metrics().afterTaxEstate()),
                "The copied synthetic outcome is not the independently evaluated outcome for the same elections");

        var mapper = ExhaustiveCapacityInvestigationTest.mapper();
        var snapshot = new ExhaustiveSnapshotTest().envelope(ExhaustiveSnapshotTest.payload(actual));
        byte[] raw = mapper.writeValueAsBytes(snapshot);
        var experiment = new ExhaustiveCapacityInvestigationTest.Experiment(mapper);
        byte[] interned = experiment.encode(raw);
        assertArrayEquals(raw, experiment.expand(interned));
        System.out.printf(Locale.ROOT,
                "INTERNING_AUDIT syntheticCandidates=5184 syntheticRetirementPairs=81 syntheticMetricRecords=1 syntheticDifferenceRecords=1 actualCandidates=%d actualMetricRecords=%d actualDifferenceRecords=%d actualRetained=%d actualRawBytes=%d experimentalRawBytes=%d experimentalPoolEntries=%d reductionPercent=%.3f%n",
                actual.totalStrategyCount(), uniqueMetrics, uniqueDifferences,
                actual.entries().stream().filter(e -> e.retainedDetail().isPresent()).count(), raw.length, interned.length, experiment.pool.size(),
                (1.0 - ((double) interned.length / raw.length)) * 100.0);
    }
}
