package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.income.FullRetirementAgeCalculator;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitCalculator;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.socialsecurity.SocialSecurityStrategyAnalysisRequestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in, read-only diagnostic harness for the saved David/Lisa plan. */
@EnabledIfSystemProperty(named = "audit.plan", matches = ".+")
class DavidClaimingAuditTest {

    private final RetirementPlanScenarioCopyService copier =
            new RetirementPlanScenarioCopyService();
    private final IntegratedSocialSecurityStrategyEvaluator evaluator =
            new IntegratedSocialSecurityStrategyEvaluator();

    @Test
    void auditTwoExactStrategies() throws Exception {
        RetirementPlan plan = new JsonRetirementPlanRepository().load(
                Path.of(System.getProperty("audit.plan")));
        Strategies strategies = strategies(plan);
        var early = evaluator.evaluate(plan, strategies.early());
        var late = evaluator.evaluate(plan, strategies.late());

        printInputs(plan, strategies);
        printBenefitSanity(plan);
        printSummary("BASE", early.metrics(), late.metrics());
        printSurplusAudit(early, late);
        printYears(early, late);
        printAccounts(early, late, List.of(2027, 2033, 2038, 2040, 2063));
        printSensitivities(plan, strategies);
        printLongevity(plan, strategies);

        assertEquals(new ProjectionMetricsCalculator().calculate(
                plan, early.projection()), early.metrics());
        assertEquals(new ProjectionMetricsCalculator().calculate(
                plan, late.projection()), late.metrics());

        var restricted = new IntegratedSocialSecurityCompleteStrategySearchRequest(
                plan, List.of(63, 70), List.of(62),
                List.of(strategies.early().primarySurvivorElection()),
                List.of(strategies.early().spouseSurvivorElection()),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 2);
        var search = new IntegratedSocialSecurityCompleteStrategySearchCalculator()
                .calculate(restricted);
        assertEquals(early.metrics(), search.entryFor(strategies.early())
                .orElseThrow().metrics().orElseThrow());
        assertEquals(late.metrics(), search.entryFor(strategies.late())
                .orElseThrow().metrics().orElseThrow());
        System.out.println("RECONCILIATION,resultsSummary=PASS,restrictedSearch=PASS");
    }

    private Strategies strategies(RetirementPlan plan) {
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        var generator = new SocialSecuritySurvivorClaimingCandidateGenerator();
        var primarySurvivor = generator.generate(primary.getBirthDate()).getLast();
        var spouseSurvivor = generator.generate(spouse.getBirthDate()).getLast();
        LocalDate spouseDate = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(spouse.getBirthDate(), 62);
        return new Strategies(
                new SocialSecurityHouseholdClaimingStrategy(
                        63, 62,
                        SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                                primary.getBirthDate(), 63),
                        spouseDate, primarySurvivor, spouseSurvivor),
                new SocialSecurityHouseholdClaimingStrategy(
                        70, 62,
                        SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                                primary.getBirthDate(), 70),
                        spouseDate, primarySurvivor, spouseSurvivor));
    }

    private void printInputs(RetirementPlan plan, Strategies strategies) {
        PlanningAssumptions assumptions = plan.getPlanningAssumptions();
        System.out.println("INPUT,early=" + strategies.early());
        System.out.println("INPUT,late=" + strategies.late());
        System.out.println("INPUT,death=" + assumptions.getDeathScenarioAssumptions());
        System.out.println("INPUT,start=" + assumptions.getProjectionStartDate()
                + ",years=" + assumptions.getProjectionLengthYears()
                + ",endYear=" + (assumptions.getProjectionStartDate().getYear()
                + assumptions.getProjectionLengthYears() - 1));
        System.out.println("INPUT,return=" + assumptions.getInvestmentReturnRate()
                + ",generalInflation=" + assumptions.getGeneralInflationRate()
                + ",healthInflation=" + assumptions.getHealthcareInflationRate()
                + ",ssCola=" + assumptions.getSocialSecurityColaRate());
        plan.getAccountPortfolio().getAccounts().forEach(account ->
                System.out.println("BEGIN_ACCOUNT," + account.getName() + ","
                        + account.getClass().getSimpleName() + ","
                        + account.getTaxTreatment() + "," + account.getCurrentBalance()));
    }

    private void printBenefitSanity(RetirementPlan plan) {
        Person david = plan.getHousehold().getPrimaryPerson();
        Person lisa = plan.getHousehold().getSpouse();
        BigDecimal davidPia = socialSecurityPia(david);
        BigDecimal lisaPia = socialSecurityPia(lisa);
        LocalDate davidFra = SocialSecurityRetirementDateCalculator
                .calculateFullRetirementDate(david.getBirthDate());
        LocalDate lisaFra = SocialSecurityRetirementDateCalculator
                .calculateFullRetirementDate(lisa.getBirthDate());
        for (int age : List.of(63, 70)) {
            LocalDate date = SocialSecurityRetirementDateCalculator
                    .calculateRetirementClaimDate(david.getBirthDate(), age);
            BigDecimal factor = SocialSecurityBenefitCalculator
                    .calculateRetirementBenefitFactor(david.getBirthDate(), date);
            System.out.println("BENEFIT,David,age=" + age + ",fra=" + davidFra
                    + ",claim=" + date + ",factor=" + factor
                    + ",monthly2027=" + davidPia.multiply(factor));
        }
        LocalDate lisaDate = SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(lisa.getBirthDate(), 62);
        BigDecimal lisaFactor = SocialSecurityBenefitCalculator
                .calculateRetirementBenefitFactor(lisa.getBirthDate(), lisaDate);
        System.out.println("BENEFIT,Lisa,age=62,fra=" + lisaFra + ",claim=" + lisaDate
                + ",factor=" + lisaFactor + ",monthly2027=" + lisaPia.multiply(lisaFactor));
        System.out.println("FRA,David=" + FullRetirementAgeCalculator.determine(david.getBirthDate())
                + ",Lisa=" + FullRetirementAgeCalculator.determine(lisa.getBirthDate()));
    }

    private BigDecimal socialSecurityPia(Person person) {
        return person.getIncomeSources().stream()
                .filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                .map(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::cast)
                .findFirst().orElseThrow().getFullRetirementMonthlyBenefit();
    }

    private void printSummary(String label, ProjectionMetrics early, ProjectionMetrics late) {
        System.out.println("SUMMARY," + label + ",metric,early,late,earlyMinusLate");
        metric(label, "householdSS", early.lifetimeHouseholdSocialSecurity(), late.lifetimeHouseholdSocialSecurity());
        metric(label, "primarySS", early.lifetimePrimarySocialSecurity(), late.lifetimePrimarySocialSecurity());
        metric(label, "spouseSS", early.lifetimeSpouseSocialSecurity(), late.lifetimeSpouseSocialSecurity());
        metric(label, "taxes", early.totalTaxes(), late.totalTaxes());
        metric(label, "withdrawals", early.lifetimePortfolioWithdrawals(), late.lifetimePortfolioWithdrawals());
        metric(label, "investmentGrowth", early.totalInvestmentGrowth(), late.totalInvestmentGrowth());
        metric(label, "rothConversions", early.lifetimeRothConversions(), late.lifetimeRothConversions());
        metric(label, "rmds", early.lifetimeRequiredMinimumDistributions(), late.lifetimeRequiredMinimumDistributions());
        metric(label, "medicare", early.lifetimeMedicarePremiums(), late.lifetimeMedicarePremiums());
        metric(label, "endingInvestable", early.endingInvestableAssets(), late.endingInvestableAssets());
        metric(label, "endingNetWorth", early.endingNetWorth(), late.endingNetWorth());
        metric(label, "afterTaxEstate", early.afterTaxEstate(), late.afterTaxEstate());
    }

    private void printSurplusAudit(
            IntegratedSocialSecurityStrategyResult early,
            IntegratedSocialSecurityStrategyResult late) {

        BigDecimal earlySurplus = BigDecimal.ZERO;
        BigDecimal lateSurplus = BigDecimal.ZERO;
        BigDecimal earlyGuaranteedAttribution = BigDecimal.ZERO;
        BigDecimal lateGuaranteedAttribution = BigDecimal.ZERO;
        BigDecimal earlyRmdAttribution = BigDecimal.ZERO;
        BigDecimal lateRmdAttribution = BigDecimal.ZERO;
        BigDecimal earlyGuaranteedIncome = BigDecimal.ZERO;
        BigDecimal lateGuaranteedIncome = BigDecimal.ZERO;
        int earlyYears = 0;
        int lateYears = 0;
        Integer earlyFirst = null;
        Integer lateFirst = null;

        System.out.println("SURPLUS,year,scenario,ss,otherGuaranteedIncome,expenses,medicare,taxes,retainedHouseholdSurplus,retainedFromGuaranteedIncome,retainedFromExcessRmd,taxFundingWithdrawal,endingRetainedNonQualifiedAssets");
        for (int index = 0; index < early.projection().size(); index++) {
            ProjectionYear e = early.projection().getYearAt(index);
            ProjectionYear l = late.projection().getYearAt(index);
            BigDecimal eSurplus = e.getRetainedHouseholdSurplus();
            BigDecimal lSurplus = l.getRetainedHouseholdSurplus();
            earlySurplus = earlySurplus.add(eSurplus);
            lateSurplus = lateSurplus.add(lSurplus);
            earlyGuaranteedAttribution = earlyGuaranteedAttribution
                    .add(e.getRetainedFromGuaranteedIncome());
            lateGuaranteedAttribution = lateGuaranteedAttribution
                    .add(l.getRetainedFromGuaranteedIncome());
            earlyRmdAttribution = earlyRmdAttribution
                    .add(e.getRetainedFromExcessRmd());
            lateRmdAttribution = lateRmdAttribution
                    .add(l.getRetainedFromExcessRmd());
            earlyGuaranteedIncome = earlyGuaranteedIncome.add(e.getGuaranteedIncome());
            lateGuaranteedIncome = lateGuaranteedIncome.add(l.getGuaranteedIncome());

            if (eSurplus.signum() > 0) {
                earlyYears++;
                if (earlyFirst == null) {
                    earlyFirst = e.getCalendarYear();
                }
                printSurplusYear(e, "EARLY", eSurplus);
            }
            if (lSurplus.signum() > 0) {
                lateYears++;
                if (lateFirst == null) {
                    lateFirst = l.getCalendarYear();
                }
                printSurplusYear(l, "LATE", lSurplus);
            }
        }

        System.out.println("SURPLUS_SUMMARY,scenario,years,firstYear,retainedHouseholdSurplus,retainedFromGuaranteedIncome,retainedFromExcessRmd,endingRetainedNonQualifiedAssets");
        System.out.println("SURPLUS_SUMMARY,EARLY," + earlyYears + "," + earlyFirst + ","
                + n(earlySurplus) + "," + n(earlyGuaranteedAttribution) + ","
                + n(earlyRmdAttribution) + "," + n(early.projection().getLastYear()
                .getEndingRetainedNonQualifiedAssets()));
        System.out.println("SURPLUS_SUMMARY,LATE," + lateYears + "," + lateFirst + ","
                + n(lateSurplus) + "," + n(lateGuaranteedAttribution) + ","
                + n(lateRmdAttribution) + "," + n(late.projection().getLastYear()
                .getEndingRetainedNonQualifiedAssets()));
        System.out.println("GUARANTEED_INCOME_TOTAL,EARLY," + n(earlyGuaranteedIncome));
        System.out.println("GUARANTEED_INCOME_TOTAL,LATE," + n(lateGuaranteedIncome));
    }

    private void printSurplusYear(
            ProjectionYear year,
            String scenario,
            BigDecimal surplus) {
        BigDecimal socialSecurity = year.getSocialSecurityResult().householdBenefit();
        System.out.println("SURPLUS," + year.getCalendarYear() + "," + scenario + ","
                + n(socialSecurity) + ","
                + n(year.getGuaranteedIncome().subtract(socialSecurity)) + ","
                + n(year.getAnnualExpenses()) + ","
                + n(year.getAnnualMedicarePremium()) + ","
                + n(year.getTotalIncomeTax()) + "," + n(surplus) + ","
                + n(year.getRetainedFromGuaranteedIncome()) + ","
                + n(year.getRetainedFromExcessRmd()) + ","
                + n(year.getTaxFundingWithdrawal()) + ","
                + n(year.getEndingRetainedNonQualifiedAssets()));
    }

    private void metric(String label, String name, BigDecimal early, BigDecimal late) {
        System.out.println("METRIC," + label + "," + name + "," + n(early) + "," + n(late)
                + "," + n(early.subtract(late)));
    }

    private void printYears(
            IntegratedSocialSecurityStrategyResult early,
            IntegratedSocialSecurityStrategyResult late) {
        System.out.println("YEAR,year,ssE,ssL,ssDiff,cumSsDiff,taxE,taxL,taxDiff,wdE,wdL,wdDiff,cumWdDiff,growthE,growthL,growthDiff,endE,endL,endDiff,rothE,rothL,rmdE,rmdL,medicareE,medicareL,expenseE,expenseL");
        BigDecimal cumulativeSs = BigDecimal.ZERO;
        BigDecimal cumulativeWithdrawal = BigDecimal.ZERO;
        for (int index = 0; index < early.projection().size(); index++) {
            ProjectionYear e = early.projection().getYearAt(index);
            ProjectionYear l = late.projection().getYearAt(index);
            BigDecimal ssDiff = e.getSocialSecurityResult().householdBenefit()
                    .subtract(l.getSocialSecurityResult().householdBenefit());
            BigDecimal wdDiff = e.getPortfolioWithdrawal().subtract(l.getPortfolioWithdrawal());
            cumulativeSs = cumulativeSs.add(ssDiff);
            cumulativeWithdrawal = cumulativeWithdrawal.add(wdDiff);
            System.out.println("YEAR," + e.getCalendarYear() + ","
                    + e.getSocialSecurityResult().householdBenefit() + ","
                    + l.getSocialSecurityResult().householdBenefit() + "," + ssDiff + ","
                    + cumulativeSs + "," + e.getTotalIncomeTax() + ","
                    + l.getTotalIncomeTax() + "," + e.getTotalIncomeTax().subtract(l.getTotalIncomeTax()) + ","
                    + e.getPortfolioWithdrawal() + "," + l.getPortfolioWithdrawal() + ","
                    + wdDiff + "," + cumulativeWithdrawal + ","
                    + e.getInvestmentGrowth() + "," + l.getInvestmentGrowth() + ","
                    + e.getInvestmentGrowth().subtract(l.getInvestmentGrowth()) + ","
                    + e.getEndingInvestableAssets() + "," + l.getEndingInvestableAssets() + ","
                    + e.getEndingInvestableAssets().subtract(l.getEndingInvestableAssets()) + ","
                    + e.getRothConversion() + "," + l.getRothConversion() + ","
                    + e.getRequiredMinimumDistribution() + "," + l.getRequiredMinimumDistribution() + ","
                    + e.getAnnualMedicarePremium() + "," + l.getAnnualMedicarePremium() + ","
                    + e.getAnnualExpenses() + "," + l.getAnnualExpenses());
            printSocialSecurityYear(e, "E");
            printSocialSecurityYear(l, "L");
            System.out.println("TAX," + e.getCalendarYear() + ",E," + e.getTaxableSocialSecurity()
                    + "," + e.getRothConversion() + "," + e.getRequiredMinimumDistribution()
                    + "," + e.getAdjustedGrossIncome() + "," + e.getFederalTaxableIncome()
                    + "," + e.getFederalIncomeTax() + "," + e.getMichiganIncomeTax());
            System.out.println("TAX," + l.getCalendarYear() + ",L," + l.getTaxableSocialSecurity()
                    + "," + l.getRothConversion() + "," + l.getRequiredMinimumDistribution()
                    + "," + l.getAdjustedGrossIncome() + "," + l.getFederalTaxableIncome()
                    + "," + l.getFederalIncomeTax() + "," + l.getMichiganIncomeTax());
            System.out.println("ROTH_AUDIT," + e.getCalendarYear() + ",E,"
                    + e.getRequestedRothConversion() + "," + e.getRothConversion() + ","
                    + e.getRothConversionShortfall());
            System.out.println("ROTH_AUDIT," + l.getCalendarYear() + ",L,"
                    + l.getRequestedRothConversion() + "," + l.getRothConversion() + ","
                    + l.getRothConversionShortfall());
            printMedicareYear(e, "E");
            printMedicareYear(l, "L");
        }
    }

    private void printMedicareYear(ProjectionYear year, String scenario) {
        var medicare = year.getMedicarePremiumCalculation();
        System.out.println("MEDICARE," + year.getCalendarYear() + "," + scenario + ","
                + medicare.annualPartBPremium() + "," + medicare.annualPartDPremium()
                + "," + medicare.totalAnnualMedicarePremium() + ","
                + year.getAnnualExpenses() + "," + year.getCashFlowNeed() + ","
                + year.getPortfolioWithdrawal());
    }

    private void printSocialSecurityYear(ProjectionYear year, String scenario) {
        HouseholdSocialSecurityResult value = year.getSocialSecurityResult();
        System.out.println("SS," + year.getCalendarYear() + "," + scenario + ","
                + value.primaryOwnBenefit() + "," + value.spouseOwnBenefit() + ","
                + value.primarySpousalExcessBenefit() + "," + value.spouseSpousalExcessBenefit() + ","
                + value.primarySurvivorCandidate() + "," + value.spouseSurvivorCandidate() + ","
                + value.primarySelectedBenefit() + "," + value.spouseSelectedBenefit() + ","
                + value.householdBenefit());
    }

    private void printAccounts(
            IntegratedSocialSecurityStrategyResult early,
            IntegratedSocialSecurityStrategyResult late,
            List<Integer> checkpoints) {
        for (int year : checkpoints) {
            printAccountYear("E", findYear(early, year));
            printAccountYear("L", findYear(late, year));
        }
    }

    private ProjectionYear findYear(IntegratedSocialSecurityStrategyResult result, int year) {
        return result.projection().getYears().stream()
                .filter(value -> value.getCalendarYear() == year).findFirst().orElseThrow();
    }

    private void printAccountYear(String scenario, ProjectionYear year) {
        for (ProjectedAccountSnapshot snapshot : year.getEndingAccountSnapshots()) {
            Account account = snapshot.getAccount();
            System.out.println("ACCOUNT," + year.getCalendarYear() + "," + scenario + ","
                    + account.getName() + "," + account.getClass().getSimpleName() + ","
                    + account.getTaxTreatment() + "," + n(snapshot.getEndingBalance()));
        }
        System.out.println("ESTATE," + year.getCalendarYear() + "," + scenario + ","
                + n(year.getEndingInvestableAssets()) + "," + n(year.getEstimatedHeirTax()) + ","
                + n(year.getAfterTaxEstateValue()) + ",retained="
                + n(year.getEndingRetainedNonQualifiedAssets()));
    }

    private void printSensitivities(RetirementPlan plan, Strategies strategies) {
        for (String rate : List.of("0.00", "0.02", "0.03", "0.045", "0.06", "0.08")) {
            RetirementPlan copy = copier.copy(plan);
            replaceAssumptions(copy, new BigDecimal(rate), null, null);
            printExperiment("return=" + rate, copy, strategies);
        }
        RetirementPlan noRoth = copier.copy(plan);
        RothConversionRequest current = noRoth.getRothConversionRequest();
        noRoth.setRothConversionRequest(new RothConversionRequest(
                false, current.getStartYear(), current.getAnnualAmount(), current.getStopRule(),
                current.getStrategy(), current.getFrequency(), current.getCustomTargetTaxableIncome()));
        printExperiment("roth=disabled", noRoth, strategies);
        for (String haircut : List.of("0.00", "0.17", "0.30")) {
            RetirementPlan copy = copier.copy(plan);
            replaceAssumptions(copy, null, null, new BigDecimal(haircut));
            printExperiment("heirHaircut=" + haircut, copy, strategies);
        }
    }

    private void printLongevity(RetirementPlan plan, Strategies strategies) {
        for (int deathYear : List.of(2038, 2048, 2053, 2058)) {
            RetirementPlan copy = copier.copy(plan);
            replaceAssumptions(copy, null,
                    new DeathScenarioAssumptions(
                            DeathScenario.PRIMARY_DIES, deathYear, 67, new BigDecimal("0.6")),
                    null);
            printExperiment("DavidDies=" + deathYear, copy, strategies);
        }
    }

    private void printExperiment(String name, RetirementPlan plan, Strategies strategies) {
        var early = evaluator.evaluate(plan, strategies.early()).metrics();
        var late = evaluator.evaluate(plan, strategies.late()).metrics();
        System.out.println("EXPERIMENT," + name + "," + n(early.afterTaxEstate()) + ","
                + n(late.afterTaxEstate()) + "," + n(early.afterTaxEstate().subtract(late.afterTaxEstate()))
                + ",ssDiff=" + n(early.lifetimeHouseholdSocialSecurity()
                .subtract(late.lifetimeHouseholdSocialSecurity())));
    }

    private void replaceAssumptions(
            RetirementPlan plan,
            BigDecimal investmentReturn,
            DeathScenarioAssumptions death,
            BigDecimal heirHaircut) {
        PlanningAssumptions old = plan.getPlanningAssumptions();
        EconomicAssumptions economics = old.getEconomicAssumptions();
        TaxAssumptions tax = old.getTaxAssumptions();
        EconomicAssumptions replacementEconomics = new EconomicAssumptions(
                investmentReturn == null ? economics.getExpectedAnnualInvestmentReturn() : investmentReturn,
                economics.getGeneralInflationRate(), economics.getHealthcareInflationRate(),
                economics.getSocialSecurityColaRate());
        TaxAssumptions replacementTax = new TaxAssumptions(
                tax.getFederalTaxBracketGrowthRate(), tax.getStandardDeductionGrowthRate(),
                tax.getStateIncomeTaxRate(), tax.getLocalIncomeTaxRate(), tax.getFilingStatus(),
                heirHaircut == null ? tax.getEstimatedHeirTaxRateOnTaxDeferredAssets() : heirHaircut,
                tax.getFutureFederalMarginalRateAdjustment(),
                tax.getFutureFederalMarginalRateEffectiveYear());
        plan.setPlanningAssumptions(new PlanningAssumptions(
                replacementEconomics, replacementTax, old.getWithdrawalAssumptions(),
                death == null ? old.getDeathScenarioAssumptions() : death,
                old.getProjectionLengthYears(), old.getProjectionStartDate()));
    }

    private void printSocialSecurityOnly(RetirementPlan plan, Strategies strategies) {
        var context = new SocialSecurityStrategyAnalysisRequestFactory().create(
                plan, SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(),
                new BigDecimal("0.01"),
                plan.getPlanningAssumptions().getProjectionStartDate());
        SocialSecurityMortalityWeightedClaimingGridRequest grid =
                context.request().retirementGridRequest();
        SocialSecurityStrategyRequest early = strategyRequest(
                grid.baseStrategy(), strategies.early());
        SocialSecurityStrategyRequest late = strategyRequest(
                grid.baseStrategy(), strategies.late());
        SocialSecurityMortalityWeightedComparisonResult result =
                new SocialSecurityMortalityWeightedComparisonCalculator().calculate(
                        new SocialSecurityMortalityWeightedComparisonRequest(
                                early, late, grid.primaryMortality(), grid.spouseMortality(),
                                grid.mortalityBaseDate(), grid.presentValueBaseDate(),
                                grid.realDiscountRate()));
        System.out.println("SS_ONLY,63," + result.expectedNominalStrategyA() + ","
                + result.expectedRealStrategyA() + ","
                + result.expectedPresentValueStrategyA());
        System.out.println("SS_ONLY,70," + result.expectedNominalStrategyB() + ","
                + result.expectedRealStrategyB() + ","
                + result.expectedPresentValueStrategyB());
        System.out.println("SS_ONLY,DIFF_EARLY_MINUS_LATE,"
                + result.expectedNominalDifference() + ","
                + result.expectedRealDifference() + ","
                + result.expectedPresentValueDifference());
    }

    private SocialSecurityStrategyRequest strategyRequest(
            SocialSecurityStrategyRequest base,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        SocialSecurityClaimingElection primary = base.primaryElection();
        SocialSecurityClaimingElection spouse = base.spouseElection();
        return new SocialSecurityStrategyRequest(
                base.analysisDate(), null,
                new SocialSecurityClaimingElection(
                        primary.owner(), primary.birthDate(),
                        primary.fullRetirementMonthlyBenefit(), primary.benefitValuationYear(),
                        strategy.primaryRetirementClaimDate()),
                new SocialSecurityClaimingElection(
                        spouse.owner(), spouse.birthDate(),
                        spouse.fullRetirementMonthlyBenefit(), spouse.benefitValuationYear(),
                        strategy.spouseRetirementClaimDate()),
                strategy.primarySurvivorElection().claimDate(),
                strategy.spouseSurvivorElection().claimDate(),
                base.primaryDeathDate(), base.spouseDeathDate(),
                base.socialSecurityColaRate());
    }

    private record Strategies(
            SocialSecurityHouseholdClaimingStrategy early,
            SocialSecurityHouseholdClaimingStrategy late) {
    }

    private static String n(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
