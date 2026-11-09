package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeAcceptanceFixturesTest {
    static void growth(RetirementPlan plan) {
        var a=plan.getPlanningAssumptions();plan.setPlanningAssumptions(new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal(".05"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),3,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
    }
    static RetirementPlan basic() {var plan=NonqualifiedAnnuityFinancialTest.fixture(true);plan.setFinancialCalculationMode(FinancialCalculationMode.BASIC);return plan;}
    @Test void isolatedProductionRepositoryNativePlansAndOriginalModeHistories() throws Exception {
        var root=Path.of("target/phase23e");Files.createDirectories(root);var dir=Files.createTempDirectory(root,"acceptance-").toAbsolutePath();
        var paths=new ArrayList<String>();var repo=new JsonRetirementPlanRepository();
        var brokerage=basic();brokerage.getAccountPortfolio().addAccount(new BrokerageAccount("Taxable brokerage",AccountOwnership.PRIMARY,new BigDecimal("500000")));growth(brokerage);
        save(repo,brokerage,dir.resolve("01-basic-brokerage.json"),paths);
        var advanced=basic();advanced.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        advanced.getAccountPortfolio().addAccount(new BrokerageAccount("Taxable brokerage",AccountOwnership.PRIMARY,new BigDecimal("500000")));growth(advanced);
        save(repo,advanced,dir.resolve("02-advanced-rejection.json"),paths);
        var pension=basic();pension.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Generic pension",AccountOwnership.PRIMARY,
                LocalDate.of(2025,1,1),null,new BigDecimal("500"),BigDecimal.ZERO,new BigDecimal("250")));
        save(repo,pension,dir.resolve("03-basic-pension.json"),paths);
        save(repo,basic(),dir.resolve("04-basic-basis-exhaustion.json"),paths);
        var niit=CalculationModeHistoryTest.plan();niit.setFinancialCalculationMode(FinancialCalculationMode.ADVANCED);
        save(repo,niit,dir.resolve("05-advanced-218-88.json"),paths);
        comparison(repo,dir.resolve("06-basic-comparison.json"),FinancialCalculationMode.BASIC,paths,true);
        comparison(repo,dir.resolve("07-advanced-comparison.json"),FinancialCalculationMode.ADVANCED,paths,true);
        var insured=basic();var a=insured.getPlanningAssumptions();
        insured.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES,2031,67),3,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        insured.setConditionalBenefits(List.of(insured.getConditionalBenefits().getFirst(),LifeInsurancePrincipalTest.principal()));
        insured.getHousehold().addExpense(new Expense("PRIMARY contractual premium",new BigDecimal("2000"),GrowthCategory.NONE,
                LocalDate.of(2030,1,1),LocalDate.of(2039,12,31),ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,BigDecimal.ZERO,
                new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,ContractualPremiumPolicy.Insured.PRIMARY)));
        save(repo,insured,dir.resolve("08-insurance-and-premium.json"),paths);
        var legacy=NonqualifiedAnnuityFinancialTest.fixture(true);legacy.setConditionalBenefits(List.of());
        var c=controller(repo,dir);
        try {c.newPlan(legacy);c.getCurrentProjection();c.saveCurrentAsBaseline("Legacy no-annuity comparison");c.saveAs(dir.resolve("09-legacy-comparison.json"));paths.add(dir.resolve("09-legacy-comparison.json").toString());}
        finally{NonqualifiedAnnuityHistoryTest.stop(c);}
        comparison(repo,dir.resolve("10-basic-advanced-history.json"),FinancialCalculationMode.ADVANCED,paths,false);
        Files.write(root.resolve("latest-acceptance-paths.txt"),paths);
        Files.writeString(dir.resolve("README.txt"),"Synthetic plans only. No insurer quote or tax qualification determination.\n"
                +"01 Basic supports positive brokerage and 5% return; 02 same financial case Advanced must refuse unsupported NIIT sources.\n"
                +"03 Basic adds $500 monthly pension. 04 annual gross 14400/recovery 8640/taxable 5760/basis 1360; next recovery 1360/taxable 13040; third fully taxable. NIIT Not Modeled.\n"
                +"05 MFJ first-year AGI 300000; ordinary tax 49468; NIIT 218.88; federal total 49686.88. Zero return for admitted Advanced cash balances.\n"
                +"06 Current Basic / saved Baseline Advanced; 07 and 10 Current Advanced / saved Baseline Basic. Saved original results differ intentionally.\n"
                +"Use Run Comparison Using Current Calculation Mode for fresh same-policy deltas; original Baseline details/history remain unchanged.\n"
                +"06/07 Current monthly 1500 / Baseline 1200, both documented exclusion 720/opening basis 10000. Common Basic tax difference 864, ending/estate difference 9936. Common Advanced tax difference 1000.80, ending/estate difference 9799.20.\n"
                +"08 PRIMARY dies Jan 1 2031: premium 0, annuity 0, separate life principal 400000 gross/excluded and no taxable principal. Premium is 2000 in 2030.\n"
                +"09 omits mode deliberately and preserves old no-annuity/no-NIIT calculations. 10 retains original Basic and Advanced snapshots for restart/swap/PDF inspection.\n"
                +"Both modes keep all features. For Advanced, use zero market volatility when cash or retained surplus lacks recognized-yield/gain attribution. Basic keeps existing market/expense modeling without NIIT.\n");
    }
    static ApplicationController controller(JsonRetirementPlanRepository repo,Path dir) {
        return new ApplicationController(repo,new JsonApplicationSettingsRepository(dir.resolve("isolated-settings-"+UUID.randomUUID()+".json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("isolated-analyses"))));
    }
    static void comparison(JsonRetirementPlanRepository repo,Path path,FinancialCalculationMode mode,List<String> paths,boolean different) throws Exception {
        var c=controller(repo,path.getParent());
        try {
            var plan=CalculationModeHistoryTest.plan();plan.setFinancialCalculationMode(mode==FinancialCalculationMode.BASIC?FinancialCalculationMode.ADVANCED:FinancialCalculationMode.BASIC);
            c.newPlan(plan);c.getCurrentProjection();c.saveCurrentAsBaseline("Original opposite-mode baseline");
            plan.setFinancialCalculationMode(mode);
            if(different)plan.setConditionalBenefits(List.of(BenefitFixtures.change(plan.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("1500"))));
            c.markModified();c.invalidateProjection();c.getCurrentProjection();c.getBaselineProjection();
            var completed=c.calculateCommonModeComparison().toCompletableFuture().get(20,TimeUnit.SECONDS);assertTrue(c.acceptCommonModeComparison(completed));
            var compared=c.compareProjections();assertEquals(mode,compared.calculationMode());
            if(different){assertEquals(0,new BigDecimal(mode==FinancialCalculationMode.BASIC?"864":"1000.80").compareTo(compared.getTotalTaxesChange()));assertEquals(0,new BigDecimal(mode==FinancialCalculationMode.BASIC?"9936":"9799.20").compareTo(compared.getAfterTaxEstateChange()));}
            assertFalse(Files.exists(path));c.saveAs(path);assertEquals(mode,repo.load(path).getFinancialCalculationMode());paths.add(path.toString());
        }finally{NonqualifiedAnnuityHistoryTest.stop(c);}
    }
    static void save(JsonRetirementPlanRepository repo,RetirementPlan plan,Path path,List<String> paths) throws Exception {
        assertFalse(Files.exists(path));repo.save(plan,path);assertEquals(plan.getFinancialCalculationMode(),repo.load(path).getFinancialCalculationMode());paths.add(path.toString());
    }
}
