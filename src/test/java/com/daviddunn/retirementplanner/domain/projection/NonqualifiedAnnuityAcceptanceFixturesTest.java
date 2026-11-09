package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

class NonqualifiedAnnuityAcceptanceFixturesTest {
    @Test void createIsolatedProductionRepositoryFixturesWithoutOverwritingAnyExistingPlan() throws Exception {
        var root=Path.of("target/phase23d");Files.createDirectories(root);
        var folder=Files.createTempDirectory(root,"acceptance-").toAbsolutePath();
        var repo=new JsonRetirementPlanRepository();var paths=new ArrayList<String>();
        var single=NonqualifiedAnnuityFinancialTest.fixture(false);save(repo,single,folder.resolve("01-single.json"),paths);
        var primary=NonqualifiedAnnuityFinancialTest.fixture(true);save(repo,primary,folder.resolve("02-primary-annuitant.json"),paths);
        var spouse=NonqualifiedAnnuityFinancialTest.fixture(true);
        spouse.setConditionalBenefits(List.of(BenefitFixtures.change(spouse.getConditionalBenefits().getFirst(),"recipient",PersonReference.SPOUSE)));
        save(repo,spouse,folder.resolve("03-spouse-annuitant.json"),paths);
        var partial=NonqualifiedAnnuityFinancialTest.fixture(true);
        partial.setConditionalBenefits(List.of(com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity(
                PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2030,7,1),"1200","720","10000")));
        save(repo,partial,folder.resolve("04-july-commencement.json"),paths);
        save(repo,NonqualifiedAnnuityFinancialTest.fixture(true),folder.resolve("05-basis-exhaustion.json"),paths);
        var taxable=NonqualifiedAnnuityFinancialTest.fixture(true);
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)BenefitFixtures.JSON.valueToTree(taxable.getConditionalBenefits().getFirst());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.get("annuityTerms")).put("openingInvestment",BigDecimal.ZERO);
        taxable.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class)));
        save(repo,taxable,folder.resolve("06-exhausted-fully-taxable.json"),paths);
        var life=NonqualifiedAnnuityFinancialTest.fixture(true);setDeath(life);
        life.setConditionalBenefits(List.of(life.getConditionalBenefits().getFirst(),LifeInsurancePrincipalTest.principal()));
        save(repo,life,folder.resolve("07-life-principal.json"),paths);
        var premium=NonqualifiedAnnuityFinancialTest.fixture(true);setDeath(premium);
        premium.getHousehold().addExpense(new Expense("PRIMARY term premium",new BigDecimal("2000"),GrowthCategory.NONE,
                LocalDate.of(2030,1,1),LocalDate.of(2039,12,31),ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,BigDecimal.ZERO,
                new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,ContractualPremiumPolicy.Insured.PRIMARY)));
        save(repo,premium,folder.resolve("08-contractual-premium.json"),paths);
        var compared=NonqualifiedAnnuityFinancialTest.fixture(true);
        var c=new ApplicationController(repo,new JsonApplicationSettingsRepository(folder.resolve("isolated-settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("isolated-analyses"))));
        try {
            c.newPlan(compared);c.getCurrentProjection();c.saveCurrentAsBaseline("Original $1,200 quote / $720 recovery");
            compared.setConditionalBenefits(List.of(BenefitFixtures.change(compared.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("1500"))));
            c.markModified();c.invalidateProjection();c.getCurrentProjection();
            var path=folder.resolve("09-current-baseline.json");assertFalse(Files.exists(path));c.saveAs(path);paths.add(path.toString());
        } finally {NonqualifiedAnnuityHistoryTest.stop(c);}
        var niit=NonqualifiedAnnuityFinancialTest.fixture(true);
        niit.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        save(repo,niit,folder.resolve("10-niit-218-88.json"),paths);
        var risk=NonqualifiedAnnuityFinancialTest.fixture(true);
        risk.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        risk.getHousehold().addExpense(new Expense("Qualified-funded household spending",new BigDecimal("60000")));
        save(repo,risk,folder.resolve("11-qualified-market-risk.json"),paths);
        Files.write(root.resolve("latest-acceptance-paths.txt"),paths);
        Files.writeString(folder.resolve("README.txt"),"Synthetic arithmetic assumptions only, not a carrier quote or legal determination.\n"
                +"Default full year: gross 14400, recovery 8640, taxable 5760, closing basis 1360. Next year: recovery 1360, taxable 13040, basis 0.\n"
                +"July: gross 7200 / recovery 4320 / taxable 2880. Primary dies January 1 2031 in 07 and 08; annuity then pays 0.\n"
                +"07 separately pays qualifying life principal 400000 in 2031; 08 premium is 2000 in 2030 and 0 after insured death.\n"
                +"09 Current gross monthly 1500; frozen Baseline 1200. Both keep documented exclusion 720 and opening basis 10000.\n"
                +"10 MFJ 2030: Roth conversion 294240 + taxable annuity 5760 = MAGI 300000; NIIT 218.88; federal total 49686.88.\n");
        Files.writeString(folder.resolve("README.txt"),
                "Use zero market volatility for fixtures 01-10, whose cash balances have no assumed growth. Positive unclassified cash growth must be rejected, not tax-exempted.\n"
                +"11 permits fixed-horizon qualified-account market risk (for example market volatility .12). Keep longevity market volatility zero if retained surplus would otherwise grow without a recognized-income split. Ordinary expense volatility and mortality remain supported. Inputs are never automatically changed.\n",
                StandardOpenOption.APPEND);
    }
    private static void setDeath(RetirementPlan plan) {
        var a=plan.getPlanningAssumptions();plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES,2031,67),3,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
    }
    private static void save(JsonRetirementPlanRepository repo,RetirementPlan plan,Path path,List<String> paths) throws Exception {
        assertFalse(Files.exists(path));repo.save(plan,path);assertEquals(plan.getConditionalBenefits(),repo.load(path).getConditionalBenefits());paths.add(path.toString());
    }
}
