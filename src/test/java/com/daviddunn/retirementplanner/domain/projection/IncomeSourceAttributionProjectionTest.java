package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncomeSourceAttributionProjectionTest {
    private static BigDecimal sum(List<IncomeSourceAttribution> sources) {
        return sources.stream().map(IncomeSourceAttribution::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void mixedAgeCoupleKeepsOnlyEligibleOwnersRmdWithoutLosingSpouseIncome() throws Exception {
        var plan = SinglePersonCoreProjectionTest.plan(true, FilingStatus.MARRIED_FILING_JOINTLY);
        plan.getHousehold().requireSpouse("Test").setBirthDate(java.time.LocalDate.of(1962, 6, 1));
        plan.getHousehold().requireSpouse("Test").setIncomeSources(java.util.List.of(
                new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(
                        "Spouse SS", AccountOwnership.SPOUSE, java.time.LocalDate.of(2029, 6, 1), null,
                        new BigDecimal("1800"), 67, BigDecimal.ZERO, 2026)));
        plan.getAccountPortfolio().getAccounts(AccountOwnership.SPOUSE).stream()
                .filter(account -> account.getType().isSubjectToOwnerRmd()).forEach(account ->
                        account.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                                2027, account.getCurrentBalance(), BigDecimal.ZERO)));
        var result = new ProjectionEngine().project(plan);
        for (var year : result.getYears()) {
            var rmds = year.getIncomeSources().stream().filter(source ->
                    source.kind() == IncomeSourceAttribution.Kind.RMD).toList();
            assertFalse(rmds.isEmpty());
            assertTrue(rmds.stream().allMatch(source -> source.owner().orElseThrow() == AccountOwnership.PRIMARY));
            assertEquals(0, year.getRmdDistributedInProjection().compareTo(sum(rmds)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lifetimeProjectionRetainsAuthoritativePensionOwner(boolean couple) throws Exception {
        var plan = SinglePersonCoreProjectionTest.plan(couple,
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        var context = ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(java.util.Optional.empty(), java.util.Optional.empty()));
        var result = new ProjectionEngine().project(plan, context);
        for (var year : result.getYears()) {
            var pensions = year.getIncomeSources().stream().filter(source ->
                    source.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(1, pensions.size());
            assertEquals(AccountOwnership.PRIMARY, pensions.getFirst().owner().orElseThrow());
            assertTrue(pensions.getFirst().pension().isPresent());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allAnnualMoneyFlowsReachFinalTaxInputWithoutDoubleCounting(boolean couple) throws Exception {
        var plan = SinglePersonCoreProjectionTest.plan(couple,
                couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE);
        var result = new ProjectionEngine().project(plan);
        for (var year : result.getYears()) {
            var sources = year.getIncomeSources();
            assertFalse(sources.isEmpty());
            assertTrue(sources.stream().allMatch(source -> source.taxYear().orElseThrow() == year.getCalendarYear()));
            if (!couple) {
                assertTrue(sources.stream().noneMatch(source -> source.owner().orElse(null) == AccountOwnership.SPOUSE));
            }
            var ss = sources.stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.SOCIAL_SECURITY).toList();
            assertEquals(0, year.getSocialSecurityResult().householdBenefit().compareTo(sum(ss)));
            assertTrue(ss.stream().allMatch(source -> source.owner().isPresent() && source.modeledTaxableAmount().isEmpty()));
            var conversions = sources.stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.ROTH_CONVERSION).toList();
            assertEquals(0, year.getRothConversion().compareTo(sum(conversions)));
            assertTrue(conversions.stream().allMatch(source -> source.account().isPresent() && source.destination().isPresent()));
            var opening = sources.stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.OPENING_RMD).toList();
            assertEquals(0, year.getRmdDistributedBeforeProjection().compareTo(sum(opening)));
            assertTrue(opening.stream().allMatch(source -> source.account().isPresent()));
            var rmds = sources.stream().filter(source -> source.kind() == IncomeSourceAttribution.Kind.RMD).toList();
            assertEquals(0, year.getRmdDistributedInProjection().compareTo(sum(rmds)));
            var funding = sources.stream().filter(source -> source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
            assertEquals(0, year.getTaxFundingWithdrawal().compareTo(sum(funding)));
            var projectedWithdrawals = sources.stream().filter(source ->
                    source.purpose() == IncomeSourceAttribution.Purpose.CASH_FLOW
                            || source.purpose() == IncomeSourceAttribution.Purpose.TAX_FUNDING).toList();
            assertEquals(0, year.getPortfolioWithdrawal().compareTo(sum(projectedWithdrawals)));
            assertThrows(UnsupportedOperationException.class, () -> sources.clear());
        }
        if (couple) {
            String expected = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/test/resources/single-person-stage2-couple-golden.txt")).replace("\r\n", "\n");
            assertEquals(expected, SinglePersonCoreProjectionTest.financialRows(result));
        }
    }
}
