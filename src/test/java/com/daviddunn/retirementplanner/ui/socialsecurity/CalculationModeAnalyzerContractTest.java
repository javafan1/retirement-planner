package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.IndividualLongevityScenarios;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeAnalyzerContractTest {
    @ParameterizedTest @EnumSource(FinancialCalculationMode.class)
    void genuinelyComputedWeightedModesRoundTripAndRejectDifferentPolicy(FinancialCalculationMode mode) throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(false);plan.setFinancialCalculationMode(mode);
        var opening=plan.getPlanningAssumptions().getProjectionStartDate();
        var mortality=IndividualLongevityScenarios.create(plan.getHousehold(),
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment.standard(),opening,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityTables.ssaPeriod2022());
        var result=new SinglePersonMortalityAnalysis().calculate(plan,mortality,opening,new java.math.BigDecimal("0.0100"),
                SinglePersonMortalityAnalysis.Mode.INTEGRATED,com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        var payload=new WeightedSnapshots.Payload(null,WeightedSnapshots.Individual.from(result));
        var context=SnapshotFixtures.individualContext(plan);var kind=AnalysisKind.INDIVIDUAL_WEIGHTED;
        var fingerprints=new AnalysisFingerprintService();var assessment=fingerprints.assess(kind,plan,ConditionalBenefitAnalyzerValidation.inputs(payload,context),null);
        var completion=new AcceptedAnalysisCompletion(UUID.randomUUID(),null,kind,UUID.randomUUID(),Instant.now(),assessment.fingerprint(),
                assessment.contextFingerprint(),AnalysisVersions.algorithms(kind),Map.of(),payload,context).withConditionalBenefits(plan);
        var codec=new AnalysisSnapshotCodec();var saved=completion.presentationSnapshot(codec);
        assertEquals(5,saved.benefitExecution().version());assertEquals(mode,saved.benefitExecution().calculationMode());
        assertEquals(saved,codec.decode(codec.encode(saved)));
        plan.setFinancialCalculationMode(mode==FinancialCalculationMode.BASIC?FinancialCalculationMode.ADVANCED:FinancialCalculationMode.BASIC);
        var changed=fingerprints.assess(kind,plan,ConditionalBenefitAnalyzerValidation.inputs(payload,context),null);
        assertEquals(AnalysisCompatibilityPolicy.State.OUTDATED,new AnalysisCompatibilityPolicy().assess(saved,changed,AnalysisVersions.algorithms(kind),saved.references()).state());
        assertEquals(mode,saved.benefitExecution().calculationMode());
    }
}
