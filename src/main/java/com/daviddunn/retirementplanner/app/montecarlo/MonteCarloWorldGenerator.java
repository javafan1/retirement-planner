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
 * The authoritative joint ordering and exact sampling protocol are also preserved.
 * Simulation count, generation order and unrelated execution/cancellation do not
 * enter world identity. Longer market coverage only appends draws to its prefix.
 */
public final class MonteCarloWorldGenerator {

    private final MonteCarloSettings settings;
    private final int firstYear;
    private final HouseholdLongevityScenarios mortalityScenarios;
    private final DiscreteProbabilitySampler mortalitySampler;
    private final MonteCarloScenarioGenerator marketGenerator = new MonteCarloScenarioGenerator();
    private final HouseholdLifetimeScenarioMapper lifetimeMapper = new HouseholdLifetimeScenarioMapper();

    public MonteCarloWorldGenerator(MonteCarloMortalityRequest request) {
        Objects.requireNonNull(request, "Mortality request is required.");
        settings = request.settings();
        firstYear = request.longevityAssumptions().mortalityBaseDate().getYear();
        mortalityScenarios = new HouseholdLongevityScenarioFactory(request.mortalityTable())
                .create(request.primaryBirthDate(), request.spouseBirthDate(), request.longevityAssumptions());
        mortalitySampler = new DiscreteProbabilitySampler(mortalityScenarios.scenarios().stream()
                .map(SocialSecurityJointMortalityScenario::jointProbability).toList());
    }

    public MonteCarloWorld generate(int scenarioIndex) {
        var random = MonteCarloRandomStreams.create(settings.seed(), scenarioIndex,
                MonteCarloRandomStreams.HOUSEHOLD_MORTALITY,
                MonteCarloRandomStreams.HOUSEHOLD_MORTALITY_V1);
        var selected = mortalityScenarios.scenarios().get(mortalitySampler.sample(random));
        var lifetime = lifetimeMapper.map(selected);
        int lastLivingYear = Math.max(lifetime.primaryDeathYear().orElseThrow().getValue(),
                lifetime.spouseDeathYear().orElseThrow().getValue()) - 1;
        var economicPath = marketGenerator.generate(firstYear, lastLivingYear, settings, scenarioIndex);
        return new MonteCarloWorld(scenarioIndex, economicPath, lifetime);
    }

    public HouseholdLongevityScenarios mortalityScenarios() {
        return mortalityScenarios;
    }
}
