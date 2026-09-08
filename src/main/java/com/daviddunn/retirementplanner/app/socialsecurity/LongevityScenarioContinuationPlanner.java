package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario;
import java.util.*;

/** Groups only pre-second-death runs. Every carrier is an actual positive-probability member. */
final class LongevityScenarioContinuationPlanner {
    record Path(int firstDecedent, int firstDeathYear) { }
    record Group(int carrierIndex, List<Integer> members) {
        Group { members = List.copyOf(members); }
    }

    Map<Path, Group> plan(List<SocialSecurityJointMortalityScenario> scenarios, int configuredLastYear,
            AnalysisCancellationToken token) {
        Map<Path, List<Integer>> members = new LinkedHashMap<>();
        for (int index = 0; index < scenarios.size(); index++) {
            token.throwIfCancellationRequested();
            var scenario = scenarios.get(index);
            if (scenario.jointProbability().signum() > 0 && secondDeathYear(scenario) > configuredLastYear) {
                members.computeIfAbsent(path(scenario), ignored -> new ArrayList<>()).add(index);
            }
        }
        Map<Path, Group> result = new LinkedHashMap<>();
        members.forEach((path, indices) -> {
            int carrier = indices.getFirst();
            for (int index : indices) {
                if (secondDeathYear(scenarios.get(index)) > secondDeathYear(scenarios.get(carrier))) carrier = index;
            }
            result.put(path, new Group(carrier, indices));
        });
        return Map.copyOf(result);
    }

    static int secondDeathYear(SocialSecurityJointMortalityScenario scenario) {
        return Math.max(scenario.primaryDeathDate().getYear(), scenario.spouseDeathDate().getYear());
    }

    static Path path(SocialSecurityJointMortalityScenario scenario) {
        int primary = scenario.primaryDeathDate().getYear();
        int spouse = scenario.spouseDeathDate().getYear();
        // All simultaneous cases share their both-alive prefix; no synthetic survival date.
        return primary == spouse ? new Path(0, 0)
                : new Path(primary < spouse ? 1 : 2, Math.min(primary, spouse));
    }
}
