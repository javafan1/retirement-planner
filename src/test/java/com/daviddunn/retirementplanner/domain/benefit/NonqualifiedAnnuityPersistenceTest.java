package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NonqualifiedAnnuityPersistenceTest {
    @TempDir Path directory;
    @Test void saveLoadCopiesAndFinancialIdentityRetainDocumentedFacts() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);var repo=new JsonRetirementPlanRepository();
        repo.save(plan,directory.resolve("original.json"));var restored=repo.load(directory.resolve("original.json"));
        assertEquals(plan.getConditionalBenefits(),restored.getConditionalBenefits());
        assertEquals(plan.getConditionalBenefits(),new RetirementPlanScenarioCopyService().copy(plan).getConditionalBenefits());
        var fingerprints=new AnalysisFingerprintService();
        var before=fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none());
        var changed=BenefitFixtures.change(BenefitFixtures.change(plan.getConditionalBenefits().getFirst(),"label","Renamed annuity"),"id",UUID.randomUUID());
        plan.setConditionalBenefits(List.of(changed));
        assertEquals(before,fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
        plan.setConditionalBenefits(List.of(BenefitFixtures.change(changed,"grossAmount",new BigDecimal("1500"))));
        assertNotEquals(before,fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE,plan,AnalysisFingerprintService.Inputs.none()));
    }
    @Test void malformedAndUnqualifiedPersistedFactsDoNotFallBackToTaxableIncome() {
        var definition=NonqualifiedAnnuityFinancialTest.fixture(true).getConditionalBenefits().getFirst();
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)BenefitFixtures.JSON.valueToTree(definition);
        var terms=(com.fasterxml.jackson.databind.node.ObjectNode)node.get("annuityTerms");
        terms.put("qualification","UNVERIFIED");
        assertThrows(Exception.class,()->BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class));
        terms.put("qualification",NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1.name());
        terms.put("unexpected",true);
        assertThrows(Exception.class,()->BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class));
        terms.remove("unexpected");terms.remove("openingInvestment");
        assertThrows(Exception.class,()->BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class));
        assertThrows(IllegalArgumentException.class,()->BenefitFixtures.change(definition,"annuityTerms",null));
        assertThrows(IllegalArgumentException.class,()->BenefitFixtures.change(definition,"taxCharacter","FULLY_TAXABLE_ORDINARY_NON_PENSION"));
    }
}
