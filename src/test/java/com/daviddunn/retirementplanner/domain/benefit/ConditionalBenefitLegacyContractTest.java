package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitLegacyContractTest {
    /** Oracle captured from the accepted HEAD classes compiled separately, before B1. */
    @Test void originalFrozenDocumentsAndAllStoredFingerprintContractsRemainByteForByteCompatible() throws Exception {
        var actual = new ArrayList<String>();
        for (boolean couple : new boolean[]{false, true}) for (boolean positive : new boolean[]{false, true}) {
            var plan = BenefitFixtures.plan(couple);
            if (positive) plan.getHousehold().addExpense(new Expense("Volatile", new BigDecimal("1000"), GrowthCategory.HEALTHCARE,
                    null, null, ExpenseType.RECURRING, new BigDecimal(".17")));
            actual.add(couple + "/" + positive + "/frozen=" + CompleteProjectionSnapshot.freeze(plan));
            var kind = couple ? AnalysisKind.COUPLE_EXHAUSTIVE : AnalysisKind.INDIVIDUAL_DETERMINISTIC;
            actual.add("integrated=" + new AnalysisFingerprintService().fingerprint(kind, plan, AnalysisFingerprintService.Inputs.none()));
            var settings = MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal(".15"));
            var mortality = new MonteCarloMortalityRequest(plan, settings,
                    LongevitySessionSettings.defaults(plan.getPlanningAssumptions().getProjectionStartDate()), 67);
            for (boolean longevity : new boolean[]{false, true}) {
                var standalone = MonteCarloFingerprintService.standalone(plan, settings, longevity ? mortality : null);
                var assumptions = longevity ? new MonteCarloStrategyComparisonRequest.Longevity(mortality)
                        : new MonteCarloStrategyComparisonRequest.Fixed(settings, standalone.request().start(), standalone.request().endingYear(),
                                couple ? MonteCarloLifetime.couple(com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario.bothSurvive())
                                        : new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
                var paired = MonteCarloFingerprintService.comparison(new MonteCarloStrategyComparisonRequest(
                        new MonteCarloStrategyCandidate("Current", plan), new MonteCarloStrategyCandidate("Baseline", plan), assumptions));
                var service = new MonteCarloFingerprintService();
                for (var inputs : List.of(standalone, paired)) {
                    var contract = service.acceptanceContract(inputs);
                    var result = service.assess(inputs, contract);
                    assertTrue(result.valid(), result.problem());
                    actual.add(inputs.kind() + "/" + contract + "=" + result.fingerprint());
                    if (!positive) actual.add(inputs.kind() + "/V2=" + service.assess(inputs, MonteCarloFingerprintService.Contract.TEMPORARY_NUMERIC_V2).fingerprint());
                }
            }
        }
        try (var source = getClass().getResourceAsStream("/conditional-benefits/legacy-input-contracts.txt")) {
            assertNotNull(source);
            assertEquals(new String(source.readAllBytes(), StandardCharsets.UTF_8).lines().toList(), actual);
        }
    }
}
