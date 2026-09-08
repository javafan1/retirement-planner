package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityStrategyCalculator.ScheduleKey;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Planner-only coverage metadata. No financial continuation or synthetic scenarios. */
final class LongevityEquivalenceCoverage {
    private record Path(int firstDecedent, int firstDeathYear) { }
    record Coverage(Map<Integer, Integer> carrierByMember, int groupCount) {
        Coverage { carrierByMember = Map.copyOf(carrierByMember); }
    }

    static Coverage plan(List<SocialSecurityJointMortalityScenario> scenarios, int configuredLast,
            AnalysisCancellationToken token) {
        Map<Path, List<Integer>> paths = new LinkedHashMap<>();
        for (int index = 0; index < scenarios.size(); index++) {
            token.throwIfCancellationRequested();
            var scenario = scenarios.get(index);
            if (scenario.jointProbability().signum() <= 0 || secondDeath(scenario) <= configuredLast) continue;
            int primary = scenario.primaryDeathDate().getYear();
            int spouse = scenario.spouseDeathDate().getYear();
            Path path = primary == spouse ? new Path(0, 0)
                    : new Path(primary < spouse ? 1 : 2, Math.min(primary, spouse));
            paths.computeIfAbsent(path, ignored -> new ArrayList<>()).add(index);
        }
        Map<Integer, Integer> assignments = new HashMap<>();
        for (var indices : paths.values()) {
            int carrier = indices.getFirst();
            for (int index : indices) {
                token.throwIfCancellationRequested();
                if (secondDeath(scenarios.get(index)) > secondDeath(scenarios.get(carrier))) carrier = index;
            }
            for (int index : indices) {
                token.throwIfCancellationRequested();
                assignments.put(index, carrier);
            }
        }
        return new Coverage(assignments, paths.size());
    }

    static int secondDeath(SocialSecurityJointMortalityScenario scenario) {
        return Math.max(scenario.primaryDeathDate().getYear(), scenario.spouseDeathDate().getYear());
    }

    /**
     * Both keys MUST come from successful finite provider validation. Equal elections,
     * COLA and monthly alive/entitlement inputs imply identical monthly arithmetic;
     * identical January-December inputs imply identical annual values AND scales.
     * An entitlement is ignorable only when it cannot pay in the member's interval.
     */
    static boolean covers(ScheduleKey member, ScheduleKey carrier) {
        if (!member.start().equals(carrier.start()) || member.end().isAfter(carrier.end())
                || member.start().getDayOfYear() != 1
                || !member.end().equals(LocalDate.of(member.end().getYear(), 12, 31))
                || !carrier.end().equals(LocalDate.of(carrier.end().getYear(), 12, 31))
                || !member.primary().equals(carrier.primary()) || !member.spouse().equals(carrier.spouse())
                || !member.cola().equals(carrier.cola())) return false;
        if (!Objects.equals(member.primaryDeath(), within(carrier.primaryDeath(), member.end()))
                || !Objects.equals(member.spouseDeath(), within(carrier.spouseDeath(), member.end()))) return false;
        return Objects.equals(activeEntitlement(member, true, member.end()), activeEntitlement(carrier, true, member.end()))
                && Objects.equals(activeEntitlement(member, false, member.end()), activeEntitlement(carrier, false, member.end()));
    }

    private static LocalDate within(LocalDate death, LocalDate end) {
        return death != null && !death.isAfter(end) ? death : null;
    }

    private static YearMonth activeEntitlement(ScheduleKey key, boolean primary, LocalDate end) {
        YearMonth entitlement = primary ? key.primarySurvivorEntitlement() : key.spouseSurvivorEntitlement();
        LocalDate ownDeath = primary ? key.primaryDeath() : key.spouseDeath();
        LocalDate otherDeath = primary ? key.spouseDeath() : key.primaryDeath();
        if (entitlement == null || otherDeath == null) return null;
        YearMonth first = YearMonth.from(key.start());
        if (entitlement.isAfter(first)) first = entitlement;
        YearMonth last = YearMonth.from(end);
        if (ownDeath != null && !YearMonth.from(ownDeath).isAfter(last)) last = YearMonth.from(ownDeath).minusMonths(1);
        return first.isAfter(last) ? null : entitlement;
    }
}
