package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetainedProjectionCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static com.daviddunn.retirementplanner.domain.roth.RothEligibilityExecutionTest.account;
import static org.junit.jupiter.api.Assertions.*;

class RothEligibilityFinancialTest {
    @TempDir Path directory;

    static Stream<Arguments> strategiesAndHouseholds() {
        return Stream.of(true, false).flatMap(couple -> Stream.of(RothConversionStrategy.values())
                .map(strategy -> Arguments.of(couple, strategy)));
    }

    @ParameterizedTest
    @MethodSource("strategiesAndHouseholds")
    void rolloverAndTraditionalIraHaveIdenticalFinancialOutcomes(boolean couple, RothConversionStrategy strategy) {
        var traditional = plan(couple, AccountType.TRADITIONAL_IRA, AccountType.ROTH_IRA, strategy);
        var rollover = plan(couple, AccountType.ROLLOVER_IRA, AccountType.ROTH_IRA, strategy);
        String configured = CompleteProjectionSnapshot.freeze(rollover);
        var expected = new ProjectionEngine().project(traditional).getYearAt(0);
        var actual = new ProjectionEngine().project(rollover).getYearAt(0);
        assertTrue(actual.getRothConversion().signum() > 0);
        assertEquals(expected.getRothConversion(), actual.getRothConversion());
        assertEquals(expected.getFederalIncomeTax(), actual.getFederalIncomeTax());
        assertEquals(expected.getAdjustedGrossIncome(), actual.getAdjustedGrossIncome());
        assertEquals(expected.getTaxFundingWithdrawal(), actual.getTaxFundingWithdrawal());
        assertEquals(expected.getEndingInvestableAssets(), actual.getEndingInvestableAssets());
        assertEquals(expected.getAfterTaxEstateValue(), actual.getAfterTaxEstateValue());
        if (strategy == RothConversionStrategy.FIXED_AMOUNT) {
            assertEquals(0, actual.getRothConversion().compareTo(new BigDecimal("50000.00")));
        }
        assertEquals(configured, CompleteProjectionSnapshot.freeze(rollover));
        assertEquals(3, rollover.getAccountPortfolio().getAccounts().size());
    }

    @ParameterizedTest
    @EnumSource(RothConversionStrategy.class)
    void unsupportedSourcesAndDestinationsCannotGenerateTaxableConversions(RothConversionStrategy strategy) {
        for (var destination : List.of(AccountType.ROTH_401K, AccountType.INHERITED_ROTH_IRA)) {
            var plan = plan(true, AccountType.TRADITIONAL_IRA, destination, strategy);
            var year = new ProjectionEngine().project(plan).getYearAt(0);
            assertEquals(0, year.getRothConversion().signum());
            assertEquals(0, year.getFederalIncomeTax().signum());
            assertEquals(0, year.getAdjustedGrossIncome().signum());
            assertEquals(3, plan.getAccountPortfolio().getAccounts().size());
        }
        var unsupportedSource = plan(true, AccountType.ROTH_401K, AccountType.ROTH_IRA, strategy);
        var year = new ProjectionEngine().project(unsupportedSource).getYearAt(0);
        assertEquals(0, year.getRothConversion().signum());
        assertEquals(0, year.getFederalIncomeTax().signum());
        assertEquals(0, year.getAdjustedGrossIncome().signum());
    }

    @Test
    void saveReloadAndRetainedRestorationPreserveOriginalDocumentsAndResults() throws Exception {
        var plan = plan(true, AccountType.ROLLOVER_IRA, AccountType.ROTH_IRA, RothConversionStrategy.FIXED_AMOUNT);
        var before = CompleteProjectionSnapshot.freeze(plan);
        var repository = new JsonRetirementPlanRepository();
        var file = directory.resolve("plan.json");
        repository.save(plan, file);
        var loaded = repository.load(file);
        assertEquals(before, CompleteProjectionSnapshot.freeze(loaded));
        assertEquals(CompleteProjectionSnapshot.digest(before),
                CompleteProjectionSnapshot.digest(CompleteProjectionSnapshot.freeze(loaded)));
        assertFalse(before.contains("eligibleForRothConversion"));
        assertFalse(before.contains("employerDistributionAvailability"));
        assertFalse(before.contains("usesLegacyEmployerAssumption"));
        var retained = RetainedProjectionSnapshot.capture(new ProjectionEngine().project(loaded));
        var codec = new RetainedProjectionCodec();
        var encoded = codec.encode(retained);
        var decoded = codec.decode(encoded.bytes());
        assertEquals(retained, RetainedProjectionSnapshot.capture(decoded.restoreProjection()));
        assertEquals(before, CompleteProjectionSnapshot.freeze(plan));
        repository.save(loaded, directory.resolve("copy.json"));
        assertEquals(before, CompleteProjectionSnapshot.freeze(repository.load(directory.resolve("copy.json"))));
    }

    static RetirementPlan plan(boolean couple, AccountType source, AccountType destination, RothConversionStrategy strategy) {
        var primary = new Person("Primary", "Person", LocalDate.of(1965, 1, 1));
        var household = couple ? new Household(primary,
                new Person("Spouse", "Person", LocalDate.of(1966, 1, 1))) : new Household(primary);
        var portfolio = new AccountPortfolio(List.of(
                account(source, AccountOwnership.PRIMARY, new BigDecimal("500000.00")),
                account(destination, AccountOwnership.PRIMARY, BigDecimal.ZERO),
                account(AccountType.BROKERAGE, AccountOwnership.PRIMARY, new BigDecimal("500000.00"))));
        var plan = new RetirementPlan(household, portfolio,
                // Isolate federal conversion arithmetic: Michigan intentionally has
                // different qualification rules for traditional and rollover IRAs.
                new PlanningAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, 1, LocalDate.of(2026, 1, 1))
                        .withStateTaxAssumptions(new StateTaxAssumptions(StateTaxJurisdiction.FL,
                                StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE, null)));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2026,
                strategy == RothConversionStrategy.FIXED_AMOUNT ? new BigDecimal("50000.00") : BigDecimal.ZERO,
                RothConversionStopRule.NEVER, strategy, RothConversionFrequency.ONE_TIME,
                strategy == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET ? new BigDecimal("60000") : null));
        return plan;
    }
}
