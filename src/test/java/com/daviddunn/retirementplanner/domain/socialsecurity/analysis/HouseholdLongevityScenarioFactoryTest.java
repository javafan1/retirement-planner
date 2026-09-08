package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HouseholdLongevityScenarioFactoryTest {

    private final SocialSecurityMortalityTable table = SocialSecurityMortalityTables.ssaPeriod2022();
    private final LocalDate primaryBirth = LocalDate.of(1960, 2, 29);
    private final LocalDate spouseBirth = LocalDate.of(1963, 7, 4);
    private final LocalDate conditioning = LocalDate.of(2026, 7, 1);

    @Test
    void providerDistributionsAndLegacyJointScenariosAreExactlyPreserved() {
        var assumptions = assumptions("0.80", "1.50");
        var prepared = new HouseholdLongevityScenarioFactory(table)
                .create(primaryBirth, spouseBirth, assumptions);
        var provider = new SocialSecurityMortalityDistributionProvider(table);
        var primary = provider.createDistribution(new SocialSecurityMortalityDistributionRequest(
                primaryBirth, conditioning, assumptions.primaryCategory(), assumptions.primaryAdjustment()));
        var spouse = provider.createDistribution(new SocialSecurityMortalityDistributionRequest(
                spouseBirth, conditioning, assumptions.spouseCategory(), assumptions.spouseAdjustment()));

        assertEquals(primary.distribution().probabilities(), prepared.primary().distribution().probabilities());
        assertEquals(spouse.distribution().probabilities(), prepared.spouse().distribution().probabilities());
        assertEquals(primary.firstModeledAttainedAge(), prepared.primary().firstModeledAttainedAge());
        assertEquals(spouse.firstModeledAttainedAge(), prepared.spouse().firstModeledAttainedAge());
        assertEquals(legacyScenarios(primaryBirth, spouseBirth,
                primary.distribution(), spouse.distribution()), prepared.scenarios());
        assertSame(assumptions, prepared.assumptions());
        assertEquals(table.metadata(), prepared.primary().tableMetadata());
        assertEquals(assumptions.partialYearConvention(), prepared.primary().partialYearConvention());
        assertEquals(120, prepared.primary().terminalDeathAge());
        assertEquals(120, prepared.spouse().terminalDeathAge());
        assertEquals(120, prepared.scenarios().getLast().primaryDeathAge());
        assertEquals(120, prepared.scenarios().getLast().spouseDeathAge());
        assertThrows(UnsupportedOperationException.class, () -> prepared.scenarios().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> prepared.primary().distribution().probabilities().clear());
        BigDecimal total = BigDecimal.ZERO;
        for (var scenario : prepared.scenarios()) {
            assertEquals(scenario.primaryProbability().multiply(scenario.spouseProbability()),
                    scenario.jointProbability());
            total = total.add(scenario.jointProbability());
        }
        assertEquals(0, BigDecimal.ONE.compareTo(total));
    }

    @Test
    void adjustmentsOnlyChangeTheIntendedPersonAndExplicitOneMatchesDefaultProvider() {
        var factory = new HouseholdLongevityScenarioFactory(table);
        var standard = factory.create(primaryBirth, spouseBirth, assumptions("1.00", "1.00"));
        var primaryAdjusted = factory.create(primaryBirth, spouseBirth, assumptions("0.80", "1.00"));
        var spouseAdjusted = factory.create(primaryBirth, spouseBirth, assumptions("1.00", "1.50"));
        assertNotEquals(standard.primary().distribution().probabilities(), primaryAdjusted.primary().distribution().probabilities());
        assertEquals(standard.spouse().distribution().probabilities(), primaryAdjusted.spouse().distribution().probabilities());
        assertEquals(standard.primary().distribution().probabilities(), spouseAdjusted.primary().distribution().probabilities());
        assertNotEquals(standard.spouse().distribution().probabilities(), spouseAdjusted.spouse().distribution().probabilities());
        var provider = new SocialSecurityMortalityDistributionProvider(table);
        assertEquals(provider.createDistribution(new SocialSecurityMortalityDistributionRequest(
                        primaryBirth, conditioning, SocialSecurityMortalityCategory.MALE)).distribution().probabilities(),
                standard.primary().distribution().probabilities());
        assertEquals(provider.createDistribution(new SocialSecurityMortalityDistributionRequest(
                        spouseBirth, conditioning, SocialSecurityMortalityCategory.FEMALE)).distribution().probabilities(),
                standard.spouse().distribution().probabilities());
    }

    @Test
    void birthdayPartialYearAndTerminalBoundaryRemainProviderDefined() {
        var factory = new HouseholdLongevityScenarioFactory(table);
        for (LocalDate date : List.of(primaryBirth.plusYears(66),
                primaryBirth.plusYears(66).plusDays(1), primaryBirth.plusYears(119))) {
            var assumptions = new AnalyzerLongevityAssumptions(
                    SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityAdjustment.standard(),
                    SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityAdjustment.standard(),
                    date, table.metadata(), SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
            var result = factory.create(primaryBirth, spouseBirth, assumptions);
            var direct = new SocialSecurityMortalityDistributionProvider(table).createDistribution(
                    new SocialSecurityMortalityDistributionRequest(primaryBirth, date, SocialSecurityMortalityCategory.MALE));
            assertEquals(direct.distribution().probabilities(), result.primary().distribution().probabilities());
            if (date.equals(primaryBirth.plusYears(119))) {
                assertEquals(List.of(new SocialSecurityMortalityProbability(120, BigDecimal.ONE)),
                        result.primary().distribution().probabilities());
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(conditioning.plusDays(1), spouseBirth, assumptions("1", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(conditioning.minusYears(120), spouseBirth, assumptions("1", "1")));
    }

    @Test
    void rejectsMismatchedTableIdentity() {
        var metadata = table.metadata();
        var different = new SocialSecurityMortalityTableMetadata(
                metadata.tableId(), metadata.displayName(), metadata.sourceName(), "different-version",
                metadata.tableType(), metadata.minimumAge(), metadata.maximumAge(), metadata.sourceDescription());
        var assumptions = new AnalyzerLongevityAssumptions(
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityAdjustment.standard(),
                conditioning, different, SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        assertThrows(IllegalArgumentException.class,
                () -> new HouseholdLongevityScenarioFactory(table).create(primaryBirth, spouseBirth, assumptions));
    }

    private AnalyzerLongevityAssumptions assumptions(String primary, String spouse) {
        return new AnalyzerLongevityAssumptions(
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityAdjustment.of(new BigDecimal(primary)),
                SocialSecurityMortalityCategory.FEMALE, SocialSecurityMortalityAdjustment.of(new BigDecimal(spouse)),
                conditioning, table.metadata(), SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
    }

    /** Frozen pre-extraction grid algorithm, deliberately independent of the new factory. */
    static List<SocialSecurityJointMortalityScenario> legacyScenarios(
            LocalDate primaryBirth, LocalDate spouseBirth,
            SocialSecurityMortalityDistribution primary, SocialSecurityMortalityDistribution spouse) {
        List<SocialSecurityJointMortalityScenario> scenarios = new ArrayList<>();
        var jointCalculator = new SocialSecurityIndependentJointMortalityCalculator();
        BigDecimal total = BigDecimal.ZERO;
        for (var primaryProbability : primary.probabilities()) {
            LocalDate primaryDeath = SocialSecurityDeathDateCalculator.calculateDeathDate(
                    primaryBirth, primaryProbability.deathAge());
            for (var spouseProbability : spouse.probabilities()) {
                LocalDate spouseDeath = SocialSecurityDeathDateCalculator.calculateDeathDate(
                        spouseBirth, spouseProbability.deathAge());
                BigDecimal joint = jointCalculator.calculate(primaryProbability.probability(), spouseProbability.probability());
                scenarios.add(new SocialSecurityJointMortalityScenario(
                        primaryProbability.deathAge(), spouseProbability.deathAge(), primaryDeath, spouseDeath,
                        primaryProbability.probability(), spouseProbability.probability(), joint));
                total = total.add(joint);
            }
        }
        assertEquals(0, total.compareTo(BigDecimal.ONE));
        return List.copyOf(scenarios);
    }
}
