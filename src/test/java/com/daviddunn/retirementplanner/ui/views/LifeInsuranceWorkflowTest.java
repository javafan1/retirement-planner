package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.LifeInsuranceFinancialTest;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceWorkflowTest {
    @BeforeAll static void initialize() throws Exception { startFx(); }
    @Test void sectionPreservesQualificationWhileEditingAndOnlyMaterialEditsChangeFinancialIdentity() throws Exception {
        fx(()-> {
            var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
            var original=plan.getConditionalBenefits().getFirst();
            var section=new ConditionalBenefitsPane(); section.load(plan);
            var notifications=new AtomicInteger(); section.setOnChanged(notifications::incrementAndGet);
            var fingerprints=new AnalysisFingerprintService();
            String initial=fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none());
            section.commit(plan,original,BenefitFixtures.change(original,"grossAmount",original.grossAmount().setScale(8)));
            assertEquals(0,notifications.get(),"An unchanged qualified definition must not fail or invalidate the plan");
            var renamed=BenefitFixtures.change(original,"label","Renamed personal policy"); section.commit(plan,original,renamed);
            assertEquals(initial,fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
            var edited=BenefitFixtures.change(renamed,"grossAmount",new BigDecimal("450000")); section.commit(plan,renamed,edited);
            assertEquals(original.id(),plan.getConditionalBenefits().getFirst().id());
            assertEquals(original.lifeInsuranceQualification(),plan.getConditionalBenefits().getFirst().lifeInsuranceQualification());
            assertNotEquals(initial,fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
            assertEquals(2,notifications.get());
            var node=LifeInsurancePrincipalTest.document(); node.remove("lifeInsuranceQualification");
            node.put("taxCharacter","FULLY_TAXABLE_ORDINARY_NON_PENSION");
            var taxable=BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class);
            section.commit(plan,edited,taxable);
            assertFalse(plan.getConditionalBenefits().getFirst().excludesLifePrincipal());
            assertNotEquals(initial,fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
            assertEquals(List.of(taxable),plan.getConditionalBenefits());
            return null;
        });
    }
}
