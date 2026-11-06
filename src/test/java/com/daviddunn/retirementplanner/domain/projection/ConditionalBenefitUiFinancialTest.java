package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog;
import com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRunService;
import javafx.scene.control.Button;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitUiFinancialTest {
    static final Path OUTPUT = Path.of("target", "phase22b4", "reporting");
    @BeforeAll static void initialize() throws Exception { startFx(); }

    static RetirementPlan acceptancePlan() throws Exception {
        var plan = plan(true);
        var monthly = create(plan, TriggerType.DEATH, PaymentPattern.MONTHLY_RECURRING);
        var originalLump = create(plan, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
        var lump = fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan, originalLump);
            dialog.show();
            input(dialog, "name").setText("Survivor Lump Sum");
            choice(dialog, "category").setValue(Category.DEATH_BENEFIT);
            date(dialog, "coverage-start").setValue(LocalDate.of(2030, 1, 1));
            date(dialog, "coverage-end").setValue(LocalDate.of(2040, 12, 31));
            ((Button) dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
            assertNotNull(dialog.getResult());
            return dialog.getResult();
        });
        monthly = BenefitFixtures.change(monthly, "label", "Survivor Income");
        plan.setConditionalBenefits(List.of(monthly, lump));
        return plan;
    }

    @Test void uiCreatedSurvivorPaymentsEnterCashAndFederalIncomeOnceAndReconcileRetainedAssets() throws Exception {
        var plan = acceptancePlan();
        var before = BenefitFixtures.JSON.writeValueAsString(plan);
        var result = new ProjectionEngine().project(plan);
        BigDecimal opening = new BigDecimal("500000");
        for (var year : result.getYears()) {
            String amount = year.getCalendarYear() == 2030 ? "0" : year.getCalendarYear() == 2031 ? "430000" : "30000";
            money(amount, year.getGuaranteedIncome());
            money(amount, year.getAdjustedGrossIncome());
            var sources = year.getIncomeSources().stream().flatMap(source -> source.conditionalBenefit().stream()).toList();
            assertEquals(year.getCalendarYear() == 2030 ? 0 : year.getCalendarYear() == 2031 ? 2 : 1, sources.size());
            assertTrue(sources.stream().allMatch(receipt -> receipt.recipient() == PersonReference.SPOUSE));
            assertTrue(sources.stream().allMatch(receipt -> receipt.gross().compareTo(receipt.taxable()) == 0));
            var cash = year.getHouseholdCashSettlement();
            assertTrue(cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal())
                    .subtract(cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus()))
                    .abs().compareTo(new BigDecimal(".02")) <= 0);
            money(opening.add(new BigDecimal(amount)).subtract(year.getTotalIncomeTax()).subtract(cash.medicarePremiums()).toPlainString(),
                    year.getEndingInvestableAssets());
            opening = year.getEndingInvestableAssets();
        }
        assertEquals(before, BenefitFixtures.JSON.writeValueAsString(plan), "No live account deposits or plan mutation");
        assertEquals(1, result.getYears().stream().flatMap(year -> year.getIncomeSources().stream())
                .flatMap(source -> source.conditionalBenefit().stream()).filter(receipt -> receipt.pattern() == PaymentPattern.ONE_TIME_LUMP_SUM).count());
    }

    @ParameterizedTest @EnumSource(value = StateTaxJurisdiction.class, names = {"MI", "IL"})
    void uiCreatedPaymentsMatchIndependentRothRmdIrmaaAndTaxFundingOracle(StateTaxJurisdiction state) throws Exception {
        var plan = ConditionalBenefitFinancialIntegrationTest.base(state);
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1955, 6, 15));
        for (var owner : plan.getHousehold().peopleByOwner().entrySet()) {
            var person = owner.getValue();
            person.getIncomeSources().stream().toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new SocialSecurityIncome("Zero", owner.getKey(), person.getBirthDate().plusYears(67),
                    null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2030));
        }
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(), 3, LocalDate.of(2031, 1, 1), a.getStateTaxAssumptions()));
        plan.getHousehold().addExpense(new Expense("Spending", new BigDecimal("250000")));
        var ira = new TraditionalIRA("Living IRA", AccountOwnership.SPOUSE, new BigDecimal("2000000"));
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                2031, new BigDecimal("2000000"), BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(ira);
        plan.getAccountPortfolio().addAccount(new RothIRA("Living Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2031, BigDecimal.ZERO,
                RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET, RothConversionFrequency.ANNUAL));
        plan.setConditionalBenefits(List.of(create(plan, TriggerType.DEATH, PaymentPattern.MONTHLY_RECURRING)));
        var control = new RetirementPlanScenarioCopyService().copy(plan);
        control.setConditionalBenefits(List.of());
        control.getHousehold().getSpouse().addIncomeSource(new Pension("Independent living-payment oracle", AccountOwnership.SPOUSE,
                LocalDate.of(2031, 1, 1), null, new BigDecimal("2500"), BigDecimal.ZERO, null));
        var actual = new ProjectionEngine().project(plan); var expected = new ProjectionEngine().project(control);
        for (int i = 0; i < actual.size(); i++) {
            var year = actual.getYearAt(i); var oracle = expected.getYearAt(i);
            for (var getter : ProjectionYear.class.getMethods()) {
                if (getter.getParameterCount() == 0 && getter.getReturnType() == BigDecimal.class
                        && !getter.getName().equals("getMichiganRetirementIncome"))
                    money(((BigDecimal) getter.invoke(oracle)).toPlainString(), (BigDecimal) getter.invoke(year));
            }
            assertTrue(year.getRequiredMinimumDistribution().signum() > 0);
            assertTrue(year.getTaxFundingWithdrawal().signum() > 0);
            assertEquals(oracle.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome(), year.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome());
        }
    }

    @Test void uiDefinitionsExecuteInIntegratedAndGeneratedStochasticExpenseAnalysesAndStayFrozenAfterEdits() throws Exception {
        var plan = acceptancePlan();
        plan.getHousehold().addExpense(new Expense("Synthetic stochastic spending", new BigDecimal("10000"), GrowthCategory.GENERAL,
                null, null, ExpenseType.RECURRING, new BigDecimal(".15")));
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var integrated = evaluator.evaluateCurrentStrategy(plan);
        // The authoritative candidate generator handles January-1 birthdays' previous-month claim date.
        var candidate = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).strategies().getFirst();
        var quick = new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of(candidate));
        assertTrue(quick.entries().getFirst().successful(), quick.entries().getFirst().toString());
        money("430000", integrated.projection().getYearAt(1).getAdjustedGrossIncome());
        var settings = new MonteCarloSettings(3, 4242, BigDecimal.ZERO, new BigDecimal(".05"));
        var service = new MonteCarloRunService();
        var run = service.run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var again = service.run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertNotNull(run.result().benefitExecution()); assertNotNull(run.result().expenseExecution());
        assertEquals(run.result().outcomes(), again.result().outcomes());
        var frozen = run.result().benefitExecution();
        var details = run.planDetails();
        plan.setConditionalBenefits(List.of());
        assertEquals(frozen, run.result().benefitExecution());
        assertEquals(details, run.planDetails());
        assertTrue(details.contains("400000")); assertTrue(details.contains("2500"));
    }

    @Test void frozenUiProjectionPdfRetainsOriginalDefinitionsAfterRenameEditAndDelete() throws Exception {
        Files.createDirectories(OUTPUT);
        var live = acceptancePlan(); var completed = new ProjectionEngine().project(live);
        var original = new RetirementPlanScenarioCopyService().copy(live);
        var retained = RetainedProjectionSnapshot.capture(completed);
        var report = ConditionalBenefitReportingTest.report(original, completed);
        var exporter = new ProjectionPdfExporter();
        exporter.export(original, completed, List.of(), report, OUTPUT.resolve("ui-current.pdf"));
        var changed = create(live, TriggerType.AGE, PaymentPattern.MONTHLY_RECURRING);
        live.setConditionalBenefits(List.of(changed));
        live.setConditionalBenefits(List.of());
        exporter.export(original, retained.restoreProjection(), List.of(), report, OUTPUT.resolve("ui-history.pdf"));
        try (var current = Loader.loadPDF(OUTPUT.resolve("ui-current.pdf").toFile());
             var history = Loader.loadPDF(OUTPUT.resolve("ui-history.pdf").toFile())) {
            String text = new PDFTextStripper().getText(current);
            assertEquals(text, new PDFTextStripper().getText(history));
            assertEquals(current.getNumberOfPages(), history.getNumberOfPages());
            assertTrue(text.contains("400,000")); assertTrue(text.contains("Survivor Lump Sum"));
            assertTrue(text.toLowerCase(Locale.ROOT).contains("fully taxable"));
            for (int page = Math.max(0, current.getNumberOfPages() - 2); page < current.getNumberOfPages(); page++)
                javax.imageio.ImageIO.write(new PDFRenderer(current).renderImageWithDPI(page, 96), "png",
                        OUTPUT.resolve("ui-projection-page-" + page + ".png").toFile());
        }
        Path fixture = Path.of("target", "phase22b4", "synthetic-conditional-benefits.json");
        if (!Files.exists(fixture)) new JsonRetirementPlanRepository().save(original, fixture);
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " versus " + actual);
    }
}
