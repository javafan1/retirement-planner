package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.HouseholdLifetimeScenarioMapper;
import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;

import java.util.Objects;

/**
 * Prepares mortality once, then generates one independently indexed world at a time.
 * No worlds, paths, or mutable random streams are retained, and no world is executed.
 *
 * Reproducibility requires the master seed AND scenario index, the market model
 * (MonteCarloScenarioGenerator.MODEL_VERSION / legacy MARKET_RETURNS_V1), market
 * assumptions and calendar origin, HOUSEHOLD_MORTALITY_V1 stream protocol,
 * mortality table contents/version, birth dates, Person mortality categories,
 * adjustments, conditioning date and existing mortality timing convention.
 * The authoritative couple joint ordering and exact sampling protocol are preserved.
 * Individuals use PRIMARY_MORTALITY_V1 and the existing individual distribution directly;
 * no spouse stream or joint scenarios are created for them.
 * Simulation count, generation order and unrelated execution/cancellation do not
 * enter world identity. Longer market coverage only appends draws to its prefix.
 */
public final class MonteCarloWorldGenerator {

    private final MonteCarloSettings settings;
    private final int firstYear;
    private final java.util.Optional<HouseholdLongevityScenarios> mortalityScenarios;
    private final java.util.List<IndividualLongevityScenarios.DeathScenario> individualScenarios;
    private final DiscreteProbabilitySampler mortalitySampler;
    private final MonteCarloScenarioGenerator marketGenerator = new MonteCarloScenarioGenerator();
    private final HouseholdLifetimeScenarioMapper lifetimeMapper = new HouseholdLifetimeScenarioMapper();

    public MonteCarloWorldGenerator(MonteCarloMortalityRequest request) {
        Objects.requireNonNull(request, "Mortality request is required.");
        settings = request.settings();
        firstYear = request.conditioningDate().getYear();
        if (request.hasSpouse()) {
            var couple = new HouseholdLongevityScenarioFactory(request.mortalityTable())
                    .create(request.primaryBirthDate(), request.spouseBirthDate(), request.longevityAssumptions());
            mortalityScenarios = java.util.Optional.of(couple);
            individualScenarios = java.util.List.of();
            mortalitySampler = new DiscreteProbabilitySampler(couple.scenarios().stream()
                    .map(SocialSecurityJointMortalityScenario::jointProbability).toList());
        } else {
            mortalityScenarios = java.util.Optional.empty();
            individualScenarios = new IndividualLongevityScenarios(
                    new SocialSecurityMortalityDistributionProvider(request.mortalityTable())
                            .createDistribution(request.primary())).scenarios();
            mortalitySampler = new DiscreteProbabilitySampler(individualScenarios.stream()
                    .map(IndividualLongevityScenarios.DeathScenario::probability).toList());
        }
    }
    public MonteCarloWorld generate(int scenarioIndex) {
        MonteCarloLifetime lifetime;
        if (mortalityScenarios.isPresent()) {
            var random = MonteCarloRandomStreams.create(settings.seed(), scenarioIndex,
                    MonteCarloRandomStreams.HOUSEHOLD_MORTALITY,
                    MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1);
            var selected = mortalityScenarios.orElseThrow().scenarios().get(mortalitySampler.sample(random));
            lifetime = MonteCarloLifetime.couple(lifetimeMapper.map(selected));
        } else {
            var random = MonteCarloRandomStreams.create(settings.seed(), scenarioIndex,
                    MonteCarloRandomStreams.PRIMARY_MORTALITY, MonteCarloRandomStreams.PRIMARY_MORTALITY_V1);
            var selected = individualScenarios.get(mortalitySampler.sample(random));
            lifetime = MonteCarloLifetime.individual(java.time.Year.from(selected.deathDate()));
        }
        int lastLivingYear = lifetime.terminalDeathYear() - 1;
        var economicPath = marketGenerator.generate(firstYear, lastLivingYear, settings, scenarioIndex);
        var inflationPath = new MonteCarloInflationGenerator().generate(firstYear, lastLivingYear, settings, scenarioIndex);
        return new MonteCarloWorld(scenarioIndex, economicPath, lifetime, inflationPath);
    }

    public HouseholdLongevityScenarios mortalityScenarios() {
        return mortalityScenarios.orElseThrow(() -> new IllegalStateException("Individual sampling has no joint scenarios."));
    }
}
