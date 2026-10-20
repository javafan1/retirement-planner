package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.summary.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RetainedProjectionCodecTest {
    final RetainedProjectionCodec codec = new RetainedProjectionCodec();

    @ParameterizedTest @EnumSource(StateTaxJurisdiction.class)
    void actualStateProjectionsPreserveEveryFieldNestedValueAndGetter(StateTaxJurisdiction state) throws Exception {
        var plan = state == StateTaxJurisdiction.PA || state == StateTaxJurisdiction.IA
                ? PennsylvaniaProductionProjectionTest.plan(true, state) : FloridaProductionProjectionTest.plan(true, state);
        plan.addNonInvestableAsset(new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset(
                "Synthetic home", new BigDecimal("123456.789000"), new BigDecimal("0.02")));
        var original = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan);
        var frozen = RetainedProjectionSnapshot.capture(original);
        var encoded = codec.encode(frozen);
        var copy = codec.decode(encoded.bytes());
        assertEquals(frozen, copy);
        var restored = copy.restoreStrategyResult();
        assertGraph(original, restored, new IdentityHashMap<>(), "result");
        assertYearGetters(original.projection(), restored.projection());
        assertEquals(14, ProjectionMetrics.class.getRecordComponents().length);
        assertEquals(original.metrics(), restored.metrics());
        assertFalse(original.warnings().isEmpty());
        assertEquals(original.warnings(), restored.warnings());
        assertTrue(restored.projection().size() > 1);
        assertTrue(restored.projection().getFirstYear().getRequiredMinimumDistribution().signum() > 0);
        assertTrue(restored.projection().getFirstYear().getRothConversion().signum() > 0);
        assertEquals(state, restored.projection().getFirstYear().getStateTaxJurisdiction());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void supportedSingleAndCoupleShapesPreserveSpouseAbsence(boolean couple) throws Exception {
        var plan = SinglePersonCoreProjectionTest.plan(couple, couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        var result = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan);
        var projection = result.projection();
        var frozen = RetainedProjectionSnapshot.capture(result);
        var restoredResult = codec.decode(codec.encode(frozen).bytes()).restoreStrategyResult();
        var restored = restoredResult.projection();
        assertEquals(result.metrics(), restoredResult.metrics());
        assertEquals(couple, restoredResult.metrics().spouseSocialSecurity().isPresent());
        assertGraph(projection, restored, new IdentityHashMap<>(), "projection");
        assertYearGetters(projection, restored);
        for (var year : restored.getYears()) {
            assertEquals(couple, year.getSocialSecurityResult().hasSpouse());
            assertEquals(couple, year.spouseRothConversion().isPresent());
            if (!couple) {
                assertNull(year.getSocialSecurityResult().spouseOwnBenefit());
                assertThrows(IllegalStateException.class, year::getSpouseRothConversion);
            }
        }
    }

    @Test void allSupportedAccountTypesOwnersInheritedFactsAndIdentityLinksAreLossless() throws Exception {
        var plan = FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.FL);
        var portfolio = plan.getAccountPortfolio();
        int i = 0;
        for (var type : AccountFactory.getSupportedTypes()) {
            var owner = i++ % 2 == 0 ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
            if (type.allowsOwnership(AccountOwnership.JOINT)) owner = AccountOwnership.JOINT;
            var balance = new BigDecimal("1000000.000000");
            var inherited = new InheritedAccountInformation(LocalDate.of(1930, 1, 1), LocalDate.of(2018, 1, 1), BeneficiaryRelationship.CHILD);
            Account account = type == AccountType.INHERITED_ROTH_IRA || type == AccountType.INHERITED_TRADITIONAL_IRA
                    ? AccountFactory.createInherited(type, "Same name", owner, balance, inherited)
                    : AccountFactory.create(type, "Same name", owner, balance);
            if (type.isSubjectToOwnerRmd()) account.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, new BigDecimal("1100000.0000"), new BigDecimal("500.00")));
            if (account.supportsRetirementParticipantFacts()) account.setRetirementParticipantFacts(new RetirementParticipantFacts(
                    RetirementParticipantFacts.SeparationStatus.SEPARATED, LocalDate.of(2020, 1, 1),
                    RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2020, 1, 2)));
            portfolio.addAccount(account);
        }
        var original = new ProjectionEngine().project(plan);
        var frozen = RetainedProjectionSnapshot.capture(original);
        var restored = codec.decode(codec.encode(frozen).bytes()).restoreProjection();
        assertGraph(original, restored, new IdentityHashMap<>(), "projection");
        assertYearGetters(original, restored);
        assertEquals(new HashSet<>(AccountFactory.getSupportedTypes()), frozen.accounts().stream().map(RetainedProjectionSnapshot.AccountData::type).collect(java.util.stream.Collectors.toSet()));
        var first = restored.getFirstYear();
        for (int index = 0; index < first.getEndingAccountSnapshots().size(); index++) {
            var account = first.getEndingAccountSnapshots().get(index).getAccount();
            assertNotSame(original.getFirstYear().getEndingAccountSnapshots().get(index).getAccount(), account);
            for (var year : restored.getYears()) {
                assertSame(account, year.getEndingAccountSnapshots().get(index).getAccount());
                assertEquals(year.getEndingAccountSnapshots().get(index).getEndingBalance(), year.getEndingBalance(account));
            }
        }
        // DTOs retain immutable values even if the source portfolio is edited after capture.
        portfolio.getAccounts().getFirst().setCurrentBalance(new BigDecimal("7.00"));
        assertEquals(frozen, codec.decode(codec.encode(frozen).bytes()));
    }

    @Test void frozenParticipantFactsRemainDistinctFromLaterAccountFacts() throws Exception {
        var plan = FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.FL);
        var employer = new Traditional401K("Employer", AccountOwnership.PRIMARY, new BigDecimal("100000.00"));
        employer.setOpeningRmdAccountData(new OpeningRmdAccountData(2027, new BigDecimal("100000.00"), BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(employer);
        var projection = new ProjectionEngine().project(plan);
        assertTrue(projection.getYears().stream().flatMap(y -> y.getIncomeSources().stream()).anyMatch(s ->
                s.account().map(a -> a.equals(IncomeSourceAttribution.AccountReference.capture(employer))).orElse(false)));
        employer.setRetirementParticipantFacts(new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2020, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.UNKNOWN, null));
        var snapshot = RetainedProjectionSnapshot.capture(projection);
        var restored = codec.decode(codec.encode(snapshot).bytes()).restoreProjection();
        assertGraph(projection, restored, new IdentityHashMap<>(), "projection");
        var restoredEmployer = restored.getFirstYear().getEndingAccountSnapshots().stream().map(ProjectedAccountSnapshot::getAccount)
                .filter(a -> a.getName().equals("Employer")).findFirst().orElseThrow();
        var reference = restored.getFirstYear().getIncomeSources().stream().flatMap(s -> s.account().stream())
                .filter(a -> a.name().equals("Employer")).findFirst().orElseThrow();
        assertEquals(RetirementParticipantFacts.UNKNOWN, reference.participantFacts());
        assertNotEquals(reference.participantFacts(), restoredEmployer.getRetirementParticipantFacts());
        assertEquals(reference, IncomeSourceAttribution.AccountReference.capture(restoredEmployer));
    }

    @Test void legacyMissingCalculationsAndEmptyProjectionRemainExact() throws Exception {
        for (boolean empty : List.of(false, true)) {
            var original = new Projection();
            if (!empty) original.addYear(new ProjectionYear(0, 2027, new BigDecimal("100.00"), new BigDecimal("1.0000"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("101.0000"), 72));
            var restored = codec.decode(codec.encode(RetainedProjectionSnapshot.capture(original)).bytes()).restoreProjection();
            assertGraph(original, restored, new IdentityHashMap<>(), "legacy");
            assertYearGetters(original, restored);
        }
    }

    @ParameterizedTest @ValueSource(ints = {1024, 1070, 4096})
    void longDecimalsRetainExactValueAndScale(int length) throws Exception {
        var value = new BigDecimal("1234." + "1".repeat(length - 5));
        var original = new Projection();
        original.addYear(new ProjectionYear(0, 2027, value, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, value, 72));
        var restored = codec.decode(codec.encode(RetainedProjectionSnapshot.capture(original)).bytes()).restoreProjection();
        assertEquals(value, restored.getFirstYear().getBeginningInvestableAssets());
        assertEquals(value.scale(), restored.getFirstYear().getBeginningInvestableAssets().scale());
        assertGraph(original, restored, new IdentityHashMap<>(), "long");
    }

    @Test void survivorBenefitsAndFrozenElectionsRemainExact() throws Exception {
        var plan = FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI);
        var pension = new Pension("Detailed survivor pension", AccountOwnership.PRIMARY, LocalDate.of(2027, 1, 1),
                LocalDate.of(2029, 12, 31), new BigDecimal("1234.567800"), new BigDecimal("0.0200"), new BigDecimal("321.987600"));
        pension.setRetirementParticipantFacts(new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2026, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2026, 2, 1)));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2028, 67), a.getProjectionLengthYears(), a.getProjectionStartDate(), a.getStateTaxAssumptions()));
        var original = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(plan);
        assertTrue(original.projection().getYears().stream().anyMatch(y -> y.getSocialSecurityResult().spouseSelection() == SocialSecurityBenefitSelection.SURVIVOR));
        var copy = codec.decode(codec.encode(RetainedProjectionSnapshot.capture(original)).bytes()).restoreStrategyResult();
        assertGraph(original, copy, new IdentityHashMap<>(), "survivor");
        assertYearGetters(original.projection(), copy.projection());
        assertTrue(copy.projection().getYears().stream().flatMap(y -> y.getIncomeSources().stream()).flatMap(s -> s.pension().stream())
                .anyMatch(p -> p.monthlyBenefit().equals(new BigDecimal("1234.567800")) && p.endDate().isPresent() && p.survivorMonthlyBenefit().isPresent()));
    }

    @Test void actualSpendingAndTaxFundingWithdrawalsPreserveUnknownTaxableAttribution() throws Exception {
        var plan = FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI);
        plan.getHousehold().addExpense(new Expense("Additional spending", new BigDecimal("150000.0000")));
        var original = new ProjectionEngine().project(plan);
        assertTrue(original.getYears().stream().anyMatch(y -> y.getTaxFundingWithdrawal().signum() > 0));
        assertTrue(original.getYears().stream().flatMap(y -> y.getIncomeSources().stream()).anyMatch(s ->
                s.account().isPresent() && s.modeledTaxableAmount().isEmpty()));
        var restored = codec.decode(codec.encode(RetainedProjectionSnapshot.capture(original)).bytes()).restoreProjection();
        assertGraph(original, restored, new IdentityHashMap<>(), "funding");
        assertYearGetters(original, restored);
    }

    @Test void capacityLimitedActualConversionsRetainRequestedActualOwnerAndDestinationDetails() throws Exception {
        var plan = PennsylvaniaProductionProjectionTest.plan(true, StateTaxJurisdiction.IA);
        for (var account : plan.getAccountPortfolio().getAccounts()) if (account.getType() == AccountType.CHECKING)
            account.setCurrentBalance(new BigDecimal("20000000.0000"));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2027,
                new BigDecimal("5000000.00000"), com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
        var original = new ProjectionEngine().project(plan);
        assertTrue(original.getFirstYear().getRothConversionShortfall().signum() > 0);
        assertTrue(original.getFirstYear().getPrimaryRothConversion().signum() > 0);
        assertTrue(original.getFirstYear().getSpouseRothConversion().signum() > 0);
        var restored = codec.decode(codec.encode(RetainedProjectionSnapshot.capture(original)).bytes()).restoreProjection();
        assertGraph(original, restored, new IdentityHashMap<>(), "conversion");
        assertYearGetters(original, restored);
        assertTrue(restored.getFirstYear().getIncomeSources().stream().anyMatch(s -> s.destination().isPresent()));
    }

    @Test void optionalAttributionDistinguishesUnknownTaxableFromKnownZeroAndPreservesPensionFacts() throws Exception {
        var original = new ProjectionEngine().project(FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI));
        var snapshot = RetainedProjectionSnapshot.capture(original);
        var annual = snapshot.years().getFirst();
        var sources = new ArrayList<>(annual.sources());
        sources.add(new RetainedProjectionSnapshot.Income(IncomeSourceAttribution.Kind.OTHER, IncomeSourceAttribution.Purpose.INCOME,
                null, null, null, "Unknown taxable amount", new BigDecimal("0.0000"), null, null, null));
        sources.add(new RetainedProjectionSnapshot.Income(IncomeSourceAttribution.Kind.OTHER, IncomeSourceAttribution.Purpose.INCOME,
                null, null, null, "Known zero taxable", new BigDecimal("0.0000"), new BigDecimal("0.000000"), null, null));
        var amended = replaceAnnual(snapshot, 0, "sources", sources);
        var copy = codec.decode(codec.encode(amended).bytes());
        assertEquals(amended, copy);
        var values = copy.restoreProjection().getFirstYear().getIncomeSources();
        assertTrue(values.get(values.size() - 2).modeledTaxableAmount().isEmpty());
        assertEquals(new BigDecimal("0.000000"), values.getLast().modeledTaxableAmount().orElseThrow());
        assertTrue(values.getLast().owner().isEmpty()); assertTrue(values.getLast().taxYear().isEmpty());
        var pension = values.stream().filter(i -> i.pension().isPresent()).findFirst().orElseThrow().pension().orElseThrow();
        assertTrue(pension.survivorMonthlyBenefit().isEmpty()); assertTrue(pension.endDate().isEmpty());
    }

    @Test void invalidReferencesSchemaUnknownFieldsTrailingJsonAndNumericLimitsAreRejected() throws Exception {
        var original = new ProjectionEngine().project(FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI));
        var snapshot = RetainedProjectionSnapshot.capture(original);
        var bad = replaceAnnual(snapshot, 0, "balances", List.of(new RetainedProjectionSnapshot.Balance(snapshot.accounts().size(), BigDecimal.ZERO)));
        assertThrows(java.io.IOException.class, () -> codec.encode(bad));
        var tooLong = replaceAnnual(snapshot, 0, "beginning", new BigDecimal("1234." + "1".repeat(4092)));
        assertThrows(java.io.IOException.class, () -> codec.encode(tooLong));
        String raw;
        try (var gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(codec.encode(snapshot).bytes()))) {
            raw = new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String invalid : List.of(raw + " {}", raw.replace("\"schemaVersion\":1", "\"schemaVersion\":99"),
                raw.replace("\"schemaVersion\":1,", ""), raw.replaceFirst("\\{", "{\"@class\":\"java.lang.Runtime\","))) {
            assertThrows(java.io.IOException.class, () -> codec.decode(gzip(invalid)));
        }
        assertThrows(java.io.IOException.class, () -> codec.decode(new byte[AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES + 1]));
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip("x".repeat(AnalysisSnapshotCodec.MAX_RAW_BYTES + 1))));
        assertThrows(java.io.IOException.class, () -> codec.decode(gzip("[".repeat(81) + "0" + "]".repeat(81))));
    }

    @Test void attributionOnlyAccountsAreRejectedRatherThanInventingBalancesOrOpeningHistory() {
        var projection = new ProjectionEngine().project(FloridaProductionProjectionTest.plan(true, StateTaxJurisdiction.MI));
        // There is no public ProjectionYear with an attribution source but no corresponding retained account in engine output.
        // A separately captured opaque reference exposes no opening balance/history; its complete facts must not be fabricated.
        var orphan = new TraditionalIRA("Not retained", AccountOwnership.PRIMARY, new BigDecimal("123.45"));
        var s = RetainedProjectionSnapshot.capture(projection);
        var sources = new ArrayList<>(s.years().getFirst().sources());
        sources.add(new RetainedProjectionSnapshot.Income(IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW,
                AccountOwnership.PRIMARY, new RetainedProjectionSnapshot.Reference(s.accounts().size(), RetirementParticipantFacts.UNKNOWN), null,
                orphan.getName(), BigDecimal.ZERO, BigDecimal.ZERO, 2027, null));
        assertThrows(java.io.IOException.class, () -> codec.encode(replaceAnnual(s, 0, "sources", sources)));
    }

    @Test void immutableSharedLeafSchemasAndProjectionFieldInventoryAreExplicit() {
        var fields = "incomeSources,michiganTaxCalculation,stateTaxCalculation,projectionYear,calendarYear,beginningInvestableAssets,investmentGrowth,"
                + "guaranteedIncome,socialSecurityResult,annualExpenses,cashFlowNeed,portfolioWithdrawal,requiredMinimumDistribution,rmdDistributedBeforeProjection,"
                + "rmdDistributedInProjection,excessRmd,taxFundingWithdrawal,endingInvestableAssets,endingAccountSnapshots,adjustedGrossIncome,taxableSocialSecurity,"
                + "federalTaxableIncome,federalStandardDeduction,federalIncomeTax,michiganIncomeTax,primaryPersonAge,michiganRetirementIncome,michiganRetirementDeduction,"
                + "michiganTaxableIncome,medicarePremiumCalculation,requestedRothConversion,rothConversion,primaryRothConversion,spouseRothConversion,"
                + "beginningRetainedNonQualifiedAssets,retainedNonQualifiedAssetGrowth,endingRetainedNonQualifiedAssets,householdCashSettlement,combinedEffectiveTaxRate,"
                + "estimatedHeirTax,afterTaxEstateValue";
        assertEquals(new HashSet<>(Arrays.asList(fields.split(","))), Arrays.stream(ProjectionYear.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).map(Field::getName).collect(java.util.stream.Collectors.toSet()));
        Map.of(HouseholdSocialSecurityResult.class, "primaryOwnBenefit,spouseOwnBenefit,primarySpousalExcessBenefit,spouseSpousalExcessBenefit,primarySurvivorCandidate,spouseSurvivorCandidate,primarySelection,spouseSelection,householdBenefit",
                HouseholdCashSettlement.class, "guaranteedIncome,projectedPeriodRmdCash,spendingWithdrawal,taxFundingWithdrawal,ordinaryExpenses,medicarePremiums,incomeTaxes,retainedHouseholdSurplus,retainedFromExcessRmd,retainedFromGuaranteedIncome",
                ProjectionMetrics.class, "totalInvestmentGrowth,totalIncome,totalTaxes,peakAnnualTax,endingInvestableAssets,endingNetWorth,afterTaxEstate,lifetimePortfolioWithdrawals,lifetimeRothConversions,lifetimeRequiredMinimumDistributions,lifetimeMedicarePremiums,lifetimeHouseholdSocialSecurity,lifetimePrimarySocialSecurity,lifetimeSpouseSocialSecurity",
                RetirementParticipantFacts.class, "separationStatus,separationDate,retirementConditionsStatus,retirementConditionsMetDate")
                .forEach((type, names) -> assertEquals(names, String.join(",", Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList()), "Explicitly revise the snapshot schema if a shared leaf changes."));
    }

    static RetainedProjectionSnapshot replaceAnnual(RetainedProjectionSnapshot snapshot, int index, String name, Object value) throws Exception {
        var annual = snapshot.years().get(index); var components = RetainedProjectionSnapshot.Annual.class.getRecordComponents();
        var args = new Object[components.length];
        for (int i = 0; i < args.length; i++) args[i] = components[i].getName().equals(name) ? value : components[i].getAccessor().invoke(annual);
        var replacement = RetainedProjectionSnapshot.Annual.class.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(args);
        var years = new ArrayList<>(snapshot.years()); years.set(index, replacement);
        return new RetainedProjectionSnapshot(snapshot.schemaVersion(), snapshot.accounts(), years, snapshot.metrics(), snapshot.strategy(), snapshot.advancedSocialSecurityPathUsed(), snapshot.warnings());
    }
    static byte[] gzip(String raw) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(output)) { gzip.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return output.toByteArray();
    }

    /** Independent oracle: compares every domain instance field, including ignored/opaque data, not DTO recapture. */
    static void assertGraph(Object expected, Object actual, IdentityHashMap<Account, Account> identities, String path) throws Exception {
        if (expected == null) { assertNull(actual, path); return; }
        assertNotNull(actual, path);
        if (expected instanceof Optional<?> e) {
            var a = (Optional<?>) actual; assertEquals(e.isPresent(), a.isPresent(), path);
            if (e.isPresent()) assertGraph(e.get(), a.get(), identities, path + ".optional"); return;
        }
        if (expected instanceof List<?> e) {
            var a = (List<?>) actual; assertEquals(e.size(), a.size(), path);
            for (int i = 0; i < e.size(); i++) assertGraph(e.get(i), a.get(i), identities, path + "[" + i + "]"); return;
        }
        if (expected instanceof Account e) {
            var previous = identities.putIfAbsent(e, (Account) actual);
            if (previous != null) { assertSame(previous, actual, path + ".identity"); return; }
        }
        if (expected.getClass().getPackageName().startsWith("com.daviddunn.retirementplanner.domain")
                || expected instanceof IntegratedSocialSecurityStrategyResult) {
            if (expected instanceof Enum<?>) { assertEquals(expected, actual, path); return; }
            assertEquals(expected.getClass(), actual.getClass(), path);
            for (var type = expected.getClass(); type != Object.class; type = type.getSuperclass()) for (var field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true); assertGraph(field.get(expected), field.get(actual), identities, path + "." + field.getName());
                }
            }
        } else assertEquals(expected, actual, path); // BigDecimal.equals is deliberately scale-sensitive.
    }
    static void assertYearGetters(Projection expected, Projection actual) throws Exception {
        for (int i = 0; i < expected.size(); i++) for (var method : ProjectionYear.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) continue;
            var actualYear = actual.getYearAt(i);
            Object value;
            try { value = method.invoke(expected.getYearAt(i)); }
            catch (InvocationTargetException absent) {
                var actualFailure = assertThrows(InvocationTargetException.class, () -> method.invoke(actualYear));
                assertEquals(absent.getCause().getClass(), actualFailure.getCause().getClass(), method.getName()); continue;
            }
            assertGraph(value, method.invoke(actual.getYearAt(i)), new IdentityHashMap<>(), method.getName());
        }
    }
}
