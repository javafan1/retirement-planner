package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.RetirementIncomeSourceOrigin;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxEvidence;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY;
import static com.daviddunn.retirementplanner.domain.model.AccountOwnership.SPOUSE;
import static com.daviddunn.retirementplanner.domain.model.WithdrawalStrategyType.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

public class ArkansasProjectionIntegrationTest {
    static final LocalDate OPENING = LocalDate.of(2026, 1, 1);
    static final LocalDate AGE_60 = LocalDate.of(1965, 7, 1);

    @Test
    void discontinuitySurplusIsDebitedOnceAndSpentFromRetainedPoolAfterGrowth() {
        var plan = surplusPlan("1000000", 2);
        var engine = validationEngine();
        long start = System.nanoTime();
        var projection = engine.project(plan, ProjectionEvaluationContext.empty(),
                ProjectionEconomicPath.annual(Map.of(2026, ZERO, 2027, b(".04"))));
        var year = projection.getFirstYear();
        System.out.println("Arkansas validation discontinuity plus following year us=" + (System.nanoTime() - start) / 1000
                + " " + year.arkansasValidationTax().orElseThrow().fundingMetrics());
        money("16974.00", year.getTaxFundingWithdrawal());
        money("16972.66", year.getFederalIncomeTax().add(year.getStateIncomeTax()));
        money("1.34", year.getRetainedFromTaxFundingSurplus());
        money("0", year.getRetainedFromExcessRmd());
        money("0", year.getRetainedFromGuaranteedIncome());
        money("896829", year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money("896830.34", year.getEndingInvestableAssets());
        money("672623.09", year.getAfterTaxEstateValue());
        money("103171", year.getAdjustedGrossIncome());
        assertEquals(1, year.getIncomeSources().stream().filter(source ->
                source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).count());
        assertTrue(year.stateTaxCalculation().isEmpty());
        assertEquals(StateTaxJurisdiction.AR, year.getStateTaxJurisdiction());
        assertThrows(IllegalStateException.class, year::getMichiganIncomeTax);
        assertThrows(IllegalArgumentException.class, () -> RetainedProjectionSnapshot.capture(projection));
        var restored = retainedRoundTrip(projection);
        assertEquals(StateTaxJurisdiction.AR, restored.getFirstYear().getStateTaxJurisdiction());
        assertEquals(year.getRetainedFromTaxFundingSurplus(), restored.getFirstYear().getRetainedFromTaxFundingSurplus());
        assertEquals(year.getFederalIncomeTax(), restored.getFirstYear().getFederalIncomeTax());
        var next = projection.getLastYear();
        money("1.34", next.getBeginningRetainedNonQualifiedAssets());
        money(".05", next.getRetainedNonQualifiedAssetGrowth());
        money("0", next.getEndingRetainedNonQualifiedAssets());
        assertTrue(next.getIncomeSources().stream().anyMatch(source ->
                source.account().isEmpty() && source.grossAmount().compareTo(b("1.39")) == 0));
        money("1000000", plan.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
        projection.getYears().forEach(ArkansasProjectionIntegrationTest::cashBalances);
    }

    @Test
    void oneCentBelowTheRequiredFundingIsAProvedAnnualShortfall() {
        var result = validationEngine().projectWithOutcome(surplusPlan("103170.99", 1));
        var shortfall = assertInstanceOf(ProjectionExecutionResult.InsufficientFunds.class, result);
        assertTrue(shortfall.completedYears().isEmpty());
        money("16973.99", shortfall.fundingFailure().availableAmount());
        assertTrue(shortfall.fundingFailure().shortfallAmount().signum() > 0);
        assertEquals(2026, shortfall.fundingFailure().calendarYear());
    }

    @ParameterizedTest
    @EnumSource(WithdrawalStrategyType.class)
    void originalTargetedCaseUsesTheActualEngineAndPostConversionBalances(WithdrawalStrategyType order) {
        var plan = single(AGE_60, OPENING, 1, order, "90000");
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", PRIMARY, ZERO));
        if (order == TAXABLE_FIRST) {
            plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax cash", PRIMARY, b("20000")));
        }
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_12_PERCENT_BRACKET, RothConversionFrequency.ANNUAL));
        long start = System.nanoTime();
        var result = interestEngine("50000").project(plan);
        var year = result.getFirstYear();
        System.out.println("Arkansas validation targeted " + order + " us=" + (System.nanoTime() - start) / 1000
                + " " + year.arkansasValidationTax().orElseThrow().targetMetrics().orElseThrow());
        money(order == TAXABLE_FIRST ? "16500" : "8949.09", year.getRothConversion());
        money("7550.91", year.getTaxFundingWithdrawal());
        money(order == TAXABLE_FIRST ? "0" : "7550.91",
                year.arkansasValidationTax().orElseThrow().taxableFundingIncome());
        money("50400", year.getFederalTaxableIncome());
        money("5800", year.getFederalIncomeTax());
        money("1750.91", year.getStateIncomeTax());
        assertTrue(year.arkansasValidationTax().orElseThrow().targetMetrics().isPresent());
        var restored = retainedRoundTrip(result).getFirstYear();
        assertEquals(year.getRothConversion(), restored.getRothConversion());
        assertEquals(year.arkansasValidationTax().orElseThrow().provenance(),
                restored.arkansasValidationTax().orElseThrow().provenance());
        var captured = RetainedProjectionSnapshot.captureArkansas(result);
        var differentTarget = new java.util.HashMap<>(ArkansasValidationTax.reviewedVersions());
        differentTarget.put("arkansas-roth-target", "future-target");
        assertTrue(captured.arkansas().dependencies().containsKey("arkansas-roth-target"));
        assertFalse(restored.arkansasValidationTax().orElseThrow().currentFor(differentTarget));
        assertEquals("AR-TARGET-P3A-1", restored.arkansasValidationTax().orElseThrow().versions().get("arkansas-roth-target"));
        cashBalances(year);
    }

    @Test
    void knownPensionQualificationAndSurvivorOwnerAreRequiredForBothDeathOrders() {
        for (var death : List.of(DeathScenario.PRIMARY_DIES, DeathScenario.SPOUSE_DIES)) {
            var deceased = death == DeathScenario.PRIMARY_DIES ? PRIMARY : SPOUSE;
            var survivor = deceased == PRIMARY ? SPOUSE : PRIMARY;
            var plan = couple(OPENING, 2, death);
            var original = deceased == PRIMARY ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse();
            var pension = new Pension("Employer benefit", deceased, OPENING.minusYears(5), null,
                    b("4500"), ZERO, b("4000"));
            original.addIncomeSource(pension);
            var unavailable = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(plan));
            assertTrue(unavailable.getMessage().contains("employment-related"));
            var review = unavailable.pensionsToReview().getFirst();
            assertSame(pension, com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight
                    .resolve(plan.getHousehold(), review).orElseThrow());
            assertEquals("Employer benefit", review.name());
            assertEquals(deceased, review.originalOwner());
            assertEquals(survivor, review.recipient());
            var validated = validationEngine((year, pensions) -> {
                var qualified = pensions.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(s -> s,
                        s -> new ArkansasTaxEvidence.ReceiptFacts(Optional.empty(),
                                ArkansasTaxEvidence.SourceQualification.ORDINARY_EMPLOYMENT_PENSION,
                                ArkansasTaxEvidence.Form5329Status.UNKNOWN)));
                return new ArkansasProjectionValidation.AnnualEvidence(ZERO, List.of(), qualified, Map.of());
            }).project(plan);
            pension.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
            var fromSavedFact = validationEngine().project(plan);
            var restoredKnown = retainedRoundTrip(fromSavedFact);
            var restoredUnknown = retainedRoundTrip(validated);
            for (var restored : List.of(restoredKnown, restoredUnknown)) {
                var prior = restored.getLastYear().arkansasValidationTax().orElseThrow();
                assertEquals(FilingStatus.SINGLE, prior.filingStatus());
                assertEquals(ArkansasTaxEvidence.SourceQualification.ORDINARY_EMPLOYMENT_PENSION,
                        prior.provenance().pensions().getFirst().qualification());
                assertEquals(deceased, restored.getLastYear().getIncomeSources().stream().flatMap(s -> s.pension().stream())
                        .findFirst().orElseThrow().originalOwner());
            }
            assertEquals(RetirementIncomeSourceOrigin.UNKNOWN,
                    restoredUnknown.getLastYear().arkansasValidationTax().orElseThrow().provenance().pensions().getFirst().origin());
            assertEquals(ArkansasAnnualProvenance.ReceiptBasis.VALIDATION_INPUT,
                    restoredUnknown.getLastYear().arkansasValidationTax().orElseThrow().provenance().receipts().getFirst().basis());
            assertEquals(validated.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList(),
                    fromSavedFact.getYears().stream().map(ProjectionYear::getStateIncomeTax).toList());
            assertEquals(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN,
                    fromSavedFact.getLastYear().getIncomeSources().stream().flatMap(s -> s.pension().stream())
                            .findFirst().orElseThrow().sourceOrigin());
            assertEquals(FilingStatus.MARRIED_FILING_JOINTLY,
                    validated.getFirstYear().arkansasValidationTax().orElseThrow().filingStatus());
            assertEquals(FilingStatus.SINGLE, validated.getLastYear().arkansasValidationTax().orElseThrow().filingStatus());
            var source = validated.getLastYear().getIncomeSources().stream()
                    .filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION).findFirst().orElseThrow();
            assertEquals(deceased, source.pension().orElseThrow().originalOwner());
            assertEquals(survivor, source.owner().orElseThrow());
            assertTrue(validated.getLastYear().arkansasValidationTax().orElseThrow().calculation()
                    .limitations().stream().anyMatch(note -> note.contains("retirement exclusion 6000")));
            validated.getYears().forEach(ArkansasProjectionIntegrationTest::cashBalances);
        }
    }

    @Test
    void annualReviewListsOnlyPositiveInHorizonPensionsAndNeverMutatesThem() {
        var plan = single(AGE_60, OPENING.plusMonths(6), 1, TAXABLE_FIRST, "500000");
        var person = plan.getHousehold().getPrimaryPerson();
        var first = new Pension("First", PRIMARY, OPENING.plusMonths(6), null, b("7000"), ZERO);
        var second = new Pension("Second", PRIMARY, OPENING.plusMonths(6), null, b("1000"), ZERO);
        var future = new Pension("Future", PRIMARY, OPENING.plusYears(3), null, b("7000"), ZERO);
        var zero = new Pension("Zero", PRIMARY, OPENING, null, ZERO, ZERO);
        List.of(first, second, future, zero).forEach(person::addIncomeSource);
        var unavailable = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(plan));
        assertEquals(List.of(first, second), unavailable.pensionsToReview().stream()
                .map(r -> com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight
                        .resolve(plan.getHousehold(), r).orElseThrow()).toList());
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, first.getSourceOrigin());
        first.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var year = validationEngine().project(plan).getFirstYear();
        money("48000", year.getGuaranteedIncome());
        money("1066.41", year.getStateIncomeTax());
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, second.getSourceOrigin());
        cashBalances(year);
    }

    @Test
    void onlyRecipientsWithMaterialUnresolvedPoolRoomReceiveReviewRows() {
        var plan = couple(OPENING, 1, DeathScenario.BOTH_SURVIVE);
        var known = new Pension("Established", PRIMARY, OPENING, null, b("5000"), ZERO);
        known.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        var primaryUnknown = new Pension("Primary unknown", PRIMARY, OPENING, null, b("500"), ZERO);
        var spouseUnknown = new Pension("Spouse unknown", SPOUSE, OPENING, null, b("5000"), ZERO);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(known);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(primaryUnknown);
        plan.getHousehold().getSpouse().addIncomeSource(spouseUnknown);
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(plan));
        assertEquals(java.util.Set.of(SPOUSE), failure.issue().reviewRecipients());
        assertEquals(List.of("Spouse unknown"), failure.pensionsToReview().stream().map(r -> r.name()).toList());
        spouseUnknown.setSourceOrigin(RetirementIncomeSourceOrigin.EMPLOYER_RETIREMENT_PLAN);
        long start = System.nanoTime();
        var year = validationEngine().project(plan).getFirstYear();
        System.out.println("Arkansas ordinary two-owner pensions us=" + (System.nanoTime() - start) / 1000 + " "
                + year.arkansasValidationTax().orElseThrow().fundingMetrics());
        money("12000", year.arkansasValidationTax().orElseThrow().provenance().election().retirementExclusion());
        assertEquals(RetirementIncomeSourceOrigin.UNKNOWN, primaryUnknown.getSourceOrigin());
    }

    @Test
    void fixedConversionRunsAfterRmdAndMedicareRebuildsTheActualSpend() {
        var plan = single(LocalDate.of(1953, 1, 1), OPENING.plusMonths(6), 1, TAXABLE_FIRST, "600000");
        plan.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2026, b("600000"), b("2000")));
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax cash", PRIMARY, b("200000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Roth", PRIMARY, ZERO));
        plan.getHousehold().addExpense(new Expense("Spending", b("40000")));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, b("100000"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        long start = System.nanoTime();
        var year = validationEngine().project(plan).getFirstYear();
        System.out.println("Arkansas validation fixed/RMD/Medicare us=" + (System.nanoTime() - start) / 1000
                + " " + year.arkansasValidationTax().orElseThrow().fundingMetrics());
        assertTrue(year.getRmdDistributedInProjection().signum() > 0);
        money("100000", year.getRothConversion());
        assertTrue(year.getMedicarePremiumCalculation().totalAnnualMedicarePremium().signum() > 0);
        assertEquals(1, year.getMedicarePremiumCalculation().coveredMedicareParticipants());
        money(b("600000").subtract(year.getRmdDistributedInProjection()).subtract(b("100000")).toPlainString(),
                year.getEndingAccountSnapshots().getFirst().getEndingBalance());
        money(year.getRmdDistributedInProjection().add(b("102000")).toPlainString(), year.getAdjustedGrossIncome());
        money("2000", year.getRmdDistributedBeforeProjection());
        cashBalances(year);
    }

    @Test
    void fundedAnnualPathUsesEveryArkansasAdjustmentAndTheExitWithoutInventingBrokerageGain() {
        var plan = single(AGE_60, OPENING, 1, TAXABLE_FIRST, "0");
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Brokerage principal", PRIMARY, b("50000")));
        long start = System.nanoTime();
        for (int band = 0; band <= 29; band++) {
            var stateNet = BigDecimal.valueOf(94701L + 100L * band);
            var income = stateNet.add(b("2470"));
            var y = interestEngine(income.toPlainString()).project(plan).getFirstYear();
            var reduction = BigDecimal.valueOf(Math.max(0, 290 - 10 * band));
            var expected = stateNet.multiply(b(".037")).subtract(b("79.90")).subtract(reduction).subtract(b("29"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            money(expected.toPlainString(), y.getStateIncomeTax());
            money(income.toPlainString(), y.getAdjustedGrossIncome());
            assertTrue(y.getIncomeSources().stream().anyMatch(s -> s.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING
                    && s.modeledTaxableAmount().isEmpty()));
            cashBalances(y);
        }
        System.out.println("Arkansas validation high-band grid us=" + (System.nanoTime() - start) / 1000);
    }

    @Test
    void commercialAnnuityBasisAndNiitEnterTheActualYearExactlyOnce() {
        var plan = NonqualifiedAnnuityFinancialTest.fixture(true);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, b("294240"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var y = validationEngine().project(plan).getFirstYear();
        money("300000", y.getAdjustedGrossIncome());
        money("14400", y.getGuaranteedIncome());
        money("218.88", y.getIncomeSources().stream().flatMap(s -> s.niit().stream()).findFirst().orElseThrow().tax());
        assertEquals(1, y.getIncomeSources().stream().filter(s -> s.niit().isPresent()).count());
        money("5760", y.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).findFirst().orElseThrow().taxable());
        money("49686.88", y.getFederalIncomeTax());
        cashBalances(y);
    }

    @Test
    void openingMedicarePassChoosesIrmaaWithTaxFundingAndThenSettlesOnlyTheFinalCash() {
        var plan = single(LocalDate.of(1960, 1, 1), OPENING, 1, TAX_DEFERRED_FIRST, "250000");
        var y = interestEngine("105000").project(plan).getFirstYear();
        var premium = y.getMedicarePremiumCalculation();
        assertTrue(premium.modifiedAdjustedGrossIncome().compareTo(b("109000")) > 0);
        assertTrue(premium.irmaaBracket().getMinimumModifiedAdjustedGrossIncome().compareTo(b("109000")) >= 0);
        assertTrue(y.getAdjustedGrossIncome().compareTo(premium.modifiedAdjustedGrossIncome()) > 0);
        money(premium.totalAnnualMedicarePremium().toPlainString(), y.getHouseholdCashSettlement().medicarePremiums());
        cashBalances(y);
    }

    @Test
    void fixedConversionShortfallIsIndependentOfTheFundedTaxResult() {
        var plan = single(AGE_60, OPENING, 1, TAXABLE_FIRST, "1000");
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax cash", PRIMARY, b("30000")));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026, b("2000"), RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var engine = interestEngine("50000");
        var noDestination = engine.project(plan).getFirstYear();
        money("0", noDestination.getRothConversion());
        money("2000", noDestination.getRequestedRothConversion());
        plan.getAccountPortfolio().addAccount(new RothIRA("Owner Roth", PRIMARY, ZERO));
        var limited = engine.project(plan).getFirstYear();
        money("1000", limited.getRothConversion());
        money("2000", limited.getRequestedRothConversion());
        money("51000", limited.getAdjustedGrossIncome());
        money("0", limited.arkansasValidationTax().orElseThrow().taxableFundingIncome());
        cashBalances(limited);
    }

    @Test
    void partialOpeningSliceCanEstablishIraAgeAndAmbiguousOrEarlyReceiptsFail() {
        var birth = LocalDate.of(1966, 8, 31);
        var early = single(birth, OPENING, 1, TAX_DEFERRED_FIRST, "250000");
        early.getHousehold().addExpense(new Expense("Spending", b("100000")));
        var unsupported = assertThrows(ArkansasProjectionUnavailableException.class, () -> validationEngine().project(early));
        assertTrue(unsupported.getMessage().contains("5329"));
        var partial = single(birth, OPENING.plusMonths(8), 1, TAX_DEFERRED_FIRST, "250000");
        partial.getHousehold().addExpense(new Expense("Spending", b("150000")));
        var year = validationEngine().project(partial).getFirstYear();
        money("50000", year.getAnnualExpenses());
        cashBalances(year);
    }

    @Test
    void futureLowIncomeRefusalStopsTheProjectionRatherThanAcceptingAPrefix() {
        var plan = single(AGE_60, OPENING, 2, TAXABLE_FIRST, "90000");
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Tax cash", PRIMARY, b("30000")));
        var failure = assertThrows(ArkansasProjectionUnavailableException.class, () -> interestEngine("31000").project(plan));
        assertEquals(2027, failure.year());
        assertTrue(failure.getMessage().contains("low-income"));
    }

    @Test
    void productionInputsPersistArkansasAndRequireItsProjectionScope(@TempDir Path folder) throws Exception {
        assertEquals(21, StateTaxJurisdiction.productionChoices().size());
        assertEquals(StateTaxJurisdiction.AR, StateTaxJurisdiction.fromCode("AR"));
        assertThrows(IllegalArgumentException.class, () -> StateTaxJurisdiction.valueOf("CO"));
        var plan = surplusPlan("1000000", 1);
        var inputs = CompleteProjectionSnapshot.freeze(plan);
        var normal = new ProjectionEngine().project(plan);
        assertTrue(normal.getFirstYear().arkansasValidationTax().isEmpty());
        var isolated = new RetirementPlanScenarioCopyService().copy(plan);
        validationEngine().project(isolated);
        assertEquals(inputs, CompleteProjectionSnapshot.freeze(plan));
        var repo = new JsonRetirementPlanRepository();
        var file = folder.resolve("plan.json");
        repo.save(plan, file);
        var original = Files.readString(file);
        var changed = original.replaceFirst("\"jurisdiction\"\\s*:\\s*\"MI\"", "\"jurisdiction\":\"AR\"");
        assertNotEquals(original, changed);
        Files.writeString(file, changed);
        var loaded = repo.load(file);
        assertEquals(StateTaxAssumptions.simplified(StateTaxJurisdiction.AR), loaded.getPlanningAssumptions().getStateTaxAssumptions());
        assertThrows(ArkansasProjectionUnavailableException.class, () -> new ProjectionEngine().project(loaded));
        var engine = ProjectionEngine.forArkansasProduction();
        assertThrows(IllegalStateException.class, () -> engine.project(loaded));
        var completed = assertInstanceOf(ProjectionExecutionResult.Completed.class, engine.projectArkansasWithOutcome(loaded,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none(), ignored -> { }));
        assertEquals(StateTaxJurisdiction.AR, completed.projection().getFirstYear().getStateTaxJurisdiction());
        assertEquals(ArkansasValidationTax.reviewedVersions(), completed.projection().getFirstYear().arkansasValidationTax().orElseThrow().versions());
        var report = com.daviddunn.retirementplanner.app.export.ArkansasValidationReport.captureAccepted(completed.projection(), true);
        var csv = folder.resolve("arkansas.csv");
        report.writeCsv(csv);
        assertTrue(Files.readString(csv).contains("Arkansas planning estimate"));
        assertTrue(Files.readString(csv).contains("Arkansas income tax,Gross tax funding,Taxable funding income"));
        var pdf = folder.resolve("arkansas.pdf");
        report.writePdf(pdf);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("Arkansas planning estimate"));
            assertTrue(text.contains("Arkansas income tax:"));
        }
    }

    public static ProjectionEngine validationEngine() {
        return validationEngine((year, pensions) -> ArkansasProjectionValidation.AnnualEvidence.EMPTY);
    }

    public static ProjectionEngine interestEngine(String interest) {
        return validationEngine((year, pensions) -> {
            var amount = b(interest);
            var source = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.INTEREST,
                    IncomeSourceAttribution.Purpose.INCOME, "Modeled investment income for validation", amount,
                    Optional.of(amount)).inYear(year);
            return new ArkansasProjectionValidation.AnnualEvidence(amount, List.of(source), Map.of(), Map.of());
        });
    }

    public static ProjectionEngine confirmedPensionEngine() {
        return validationEngine((year, pensions) -> {
            var receipts = pensions.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(s -> s,
                    s -> new ArkansasTaxEvidence.ReceiptFacts(Optional.empty(),
                            ArkansasTaxEvidence.SourceQualification.ORDINARY_EMPLOYMENT_PENSION,
                            ArkansasTaxEvidence.Form5329Status.UNKNOWN)));
            return new ArkansasProjectionValidation.AnnualEvidence(ZERO, List.of(), receipts, Map.of());
        });
    }

    static ProjectionEngine validationEngine(ArkansasProjectionValidation.EvidenceProvider evidence) {
        return new ProjectionEngine(new TaxFundingCalculator(), new ArkansasProjectionValidation(evidence));
    }

    public static RetirementPlan surplusPlan(String balance, int years) {
        var plan = single(AGE_60, OPENING, years, TAX_DEFERRED_FIRST, balance);
        plan.getHousehold().addExpense(new Expense("Opening spending", b("86197"), GrowthCategory.NONE,
                OPENING, LocalDate.of(2026, 12, 31), ExpenseType.RECURRING));
        if (years > 1) {
            plan.getHousehold().addExpense(new Expense("Following-year retained spending", b("1.39"), GrowthCategory.NONE,
                    OPENING.plusYears(1), null, ExpenseType.RECURRING));
        }
        return plan;
    }

    public static RetirementPlan selectArkansas(RetirementPlan plan) {
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.AR)));
        return plan;
    }

    public static RetirementPlan single(LocalDate birth, LocalDate start, int years, WithdrawalStrategyType order, String balance) {
        var household = new Household(new Person("Primary", "Example", birth));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(new TraditionalIRA("IRA", PRIMARY, b(balance)));
        return new RetirementPlan(household, portfolio, assumptions(start, years,
                FilingStatus.SINGLE, order, DeathScenario.BOTH_SURVIVE));
    }

    public static RetirementPlan couple(LocalDate start, int years, DeathScenario death) {
        var household = new Household(new Person("Primary", "Example", AGE_60),
                new Person("Spouse", "Example", AGE_60.plusYears(1)));
        var portfolio = new AccountPortfolio();
        portfolio.addAccount(new TraditionalIRA("Primary IRA", PRIMARY, b("250000")));
        portfolio.addAccount(new TraditionalIRA("Spouse IRA", SPOUSE, b("250000")));
        return new RetirementPlan(household, portfolio,
                assumptions(start, years, FilingStatus.MARRIED_FILING_JOINTLY, TAXABLE_FIRST, death));
    }

    private static PlanningAssumptions assumptions(LocalDate start, int years, FilingStatus filing,
            WithdrawalStrategyType order, DeathScenario death) {
        var defaults = new PlanningAssumptions(ZERO, ZERO, years, start);
        return new PlanningAssumptions(defaults.getEconomicAssumptions(),
                new TaxAssumptions(ZERO, ZERO, ZERO, ZERO, filing, b(".25")), new WithdrawalAssumptions(order),
                new DeathScenarioAssumptions(death, death == DeathScenario.BOTH_SURVIVE ? null : start.getYear()), years, start);
    }

    static void cashBalances(ProjectionYear year) {
        var cash = year.getHouseholdCashSettlement();
        var sources = cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal())
                .add(cash.taxFundingWithdrawal());
        var uses = cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus());
        money(sources.toPlainString(), uses);
    }

    public static BigDecimal b(String value) { return new BigDecimal(value); }
    static Projection retainedRoundTrip(Projection projection) {
        try {
            var codec = new com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec();
            var retained = RetainedProjectionSnapshot.captureArkansas(projection);
            var encoded = codec.encode(retained);
            var decoded = codec.decode(encoded.bytes());
            assertEquals(retained, decoded);
            assertArrayEquals(encoded.bytes(), codec.encode(decoded).bytes());
            return decoded.restoreProjection();
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
    static void money(String amount, BigDecimal actual) { assertEquals(0, b(amount).compareTo(actual), actual.toPlainString()); }
}
