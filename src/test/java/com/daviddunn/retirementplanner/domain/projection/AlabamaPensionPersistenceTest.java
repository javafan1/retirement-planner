package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaPensionPersistenceTest {
    private Pension pension(AccountOwnership owner,PensionPlanClassification type) {
        var p=new Pension("Explicitly verified legal plan",owner,LocalDate.of(2020,1,1),null,
                new BigDecimal("1000"),BigDecimal.ZERO,new BigDecimal("750"));
        p.setPlanClassification(type);return p;
    }

    @ParameterizedTest
    @EnumSource(value=DeathScenario.class,names={"PRIMARY_DIES","SPOUSE_DIES"})
    void directSurvivorKeepsOriginalClassificationAndOwnRecipient(DeathScenario death,@TempDir Path dir) throws Exception {
        var plan=AlabamaProjectionAcceptanceTest.plan(true);
        // Isolate ordinary direct pension succession, not an unevidenced IRA inheritance.
        for(var account:List.copyOf(plan.getAccountPortfolio().getAccounts()))
            if(account.getType()!=AccountType.CHECKING) plan.getAccountPortfolio().removeAccount(account);
        plan.setRothConversionRequest(null);
        var first=pension(AccountOwnership.PRIMARY,PensionPlanClassification.DEFINED_BENEFIT_414J);
        var second=pension(AccountOwnership.SPOUSE,PensionPlanClassification.DEFINED_CONTRIBUTION);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(first);
        plan.getHousehold().getSpouse().addIncomeSource(second);
        var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),
                new TaxAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                        com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY,new BigDecimal(".25")),
                a.getWithdrawalAssumptions(),new DeathScenarioAssumptions(death,2028,67,BigDecimal.ONE),3,
                a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        var original=new ProjectionEngine().project(plan);
        // Basic federal deduction embedded in the annual rows proves death-year joint, later Single.
        assertEquals(0,new BigDecimal("32200").compareTo(original.getYearAt(1).getFederalStandardDeduction()));
        assertEquals(0,new BigDecimal("16100").compareTo(original.getYearAt(2).getFederalStandardDeduction()));
        var survivor=death.name().startsWith("PRIMARY")?AccountOwnership.SPOUSE:AccountOwnership.PRIMARY;
        for(var y:original.getYears().subList(1,3)) {
            var receipts=y.getIncomeSources().stream().filter(s->s.kind()==IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(2,receipts.size());
            assertTrue(receipts.stream().allMatch(s->s.owner().orElseThrow()==survivor));
            assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J,receipts.stream().filter(s->s.pension().orElseThrow().originalOwner()==AccountOwnership.PRIMARY)
                    .findFirst().orElseThrow().pension().orElseThrow().planClassification());
        }
        var repo=new JsonRetirementPlanRepository();var file=dir.resolve("saved-as.json");repo.save(plan,file);
        var loaded=repo.load(file);repo.save(loaded,dir.resolve("saved-again.json"));
        for(var copy:List.of(loaded,new RetirementPlanScenarioCopyService().copy(loaded)))
            assertEquals(FloridaProductionProjectionTest.financialRows(original),FloridaProductionProjectionTest.financialRows(new ProjectionEngine().project(copy)));
        var baseline=com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory.create(plan,"Classified pension");
        var baselineResult=new com.daviddunn.retirementplanner.domain.baseline.BaselineProjectionService(new ProjectionEngine()).projectBaseline(baseline);
        assertEquals(FloridaProductionProjectionTest.financialRows(original),FloridaProductionProjectionTest.financialRows(baselineResult));
        var frozen=RetainedProjectionSnapshot.capture(original);
        var frozenInputs=com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.freeze(plan);
        first.setPlanClassification(PensionPlanClassification.DEFINED_CONTRIBUTION);
        var savedInputs=com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.restoreInputs(frozenInputs);
        assertEquals(PensionPlanClassification.DEFINED_BENEFIT_414J,savedInputs.getHousehold().getPrimaryPerson().getIncomeSources().stream()
                .filter(Pension.class::isInstance).map(Pension.class::cast).findFirst().orElseThrow().getPlanClassification());
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule())
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        var restored=mapper.readValue(mapper.writeValueAsBytes(frozen),RetainedProjectionSnapshot.class).restoreProjection();
        assertEquals(original.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList(),restored.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList());
        assertTrue(restored.getYearAt(2).getIncomeSources().stream().filter(s->s.pension().isPresent())
                .anyMatch(s->s.pension().get().planClassification()==PensionPlanClassification.DEFINED_BENEFIT_414J));
    }

    @Test
    void unknownMaterialPensionFailsOnlyAlabamaAndLegacyJsonNeedsNoMigration(@TempDir Path dir) throws Exception {
        var plan=AlabamaProjectionAcceptanceTest.plan(false);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Unknown",AccountOwnership.PRIMARY,
                LocalDate.of(2020,1,1),null,new BigDecimal("1000"),BigDecimal.ZERO));
        var repo=new JsonRetirementPlanRepository();var file=dir.resolve("legacy.json");repo.save(plan,file);
        assertFalse(java.nio.file.Files.readString(file).contains("planClassification"));
        var loaded=repo.load(file);
        assertThrows(com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException.class,()->new ProjectionEngine().project(loaded));
        loaded.setPlanningAssumptions(loaded.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        assertFalse(new ProjectionEngine().project(loaded).getYears().isEmpty());
    }
}
