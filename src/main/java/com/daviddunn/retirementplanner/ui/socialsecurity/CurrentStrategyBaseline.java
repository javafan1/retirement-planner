package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.util.*;

/** Immutable analyzer-only elections and validation, captured with a weighted request. */
record CurrentStrategyBaseline(List<Election> elections,
        Optional<SocialSecurityHouseholdClaimingStrategy> strategy, String problem) {
    record Election(String name, String value, String source) { }

    CurrentStrategyBaseline {
        elections = List.copyOf(elections);
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(problem);
    }

    static Integer planSurvivorAge(RetirementPlan plan, boolean primary) {
        var death = plan.getPlanningAssumptions().getDeathScenarioAssumptions();
        return death.getDeathScenario() == (primary ? DeathScenario.SPOUSE_DIES : DeathScenario.PRIMARY_DIES)
                ? death.getSurvivorClaimingAge() : null;
    }

    static CurrentStrategyBaseline fromPlan(RetirementPlan plan) {
        return capture(plan, text(planSurvivorAge(plan, true)), text(planSurvivorAge(plan, false)));
    }

    static String text(Integer age) { return age == null ? "" : age.toString(); }

    static CurrentStrategyBaseline capture(RetirementPlan plan, String primaryAge, String spouseAge) {
        List<Election> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        var primaryRetirement = retirement(primary, AccountOwnership.PRIMARY, "Primary", rows, errors);
        var spouseRetirement = retirement(spouse, AccountOwnership.SPOUSE, "Spouse", rows, errors);
        var primarySurvivor = survivor(primary, "Primary", primaryAge, planSurvivorAge(plan, true), rows, errors);
        var spouseSurvivor = survivor(spouse, "Spouse", spouseAge, planSurvivorAge(plan, false), rows, errors);
        Optional<SocialSecurityHouseholdClaimingStrategy> strategy = Optional.empty();
        if (errors.isEmpty()) {
            strategy = Optional.of(new SocialSecurityHouseholdClaimingStrategy(
                    primaryRetirement.getClaimingAge(), spouseRetirement.getClaimingAge(),
                    primaryRetirement.getStartDate(), spouseRetirement.getStartDate(), primarySurvivor, spouseSurvivor));
        }
        return new CurrentStrategyBaseline(rows, strategy, errors.isEmpty() ? ""
                : "Current Strategy comparison unavailable: " + String.join(" ", errors));
    }

    private static SocialSecurityIncome retirement(Person person, AccountOwnership owner, String name,
            List<Election> rows, List<String> errors) {
        var sources = person == null ? List.<SocialSecurityIncome>of() : person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance).map(SocialSecurityIncome.class::cast)
                .filter(source -> source.getOwnership() == owner).toList();
        var source = sources.size() == 1 ? sources.getFirst() : null;
        rows.add(new Election(name + " retirement claiming age", source == null ? "Not specified"
                : Integer.toString(source.getClaimingAge()), "Plan"));
        if (source == null) {
            errors.add(name + " requires one correctly owned Social Security record.");
        } else if (source.getClaimingAge() < 62 || source.getClaimingAge() > 70
                || !SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(person.getBirthDate(),
                        source.getClaimingAge()).equals(source.getStartDate())) {
            errors.add(name + " retirement claiming age/date is invalid.");
        }
        return source;
    }

    private static SocialSecuritySurvivorClaimingCandidate survivor(Person person, String name, String input,
            Integer planAge, List<Election> rows, List<String> errors) {
        String value = input == null ? "" : input.trim();
        rows.add(new Election(name + " Survivor Benefit Claiming Age", value.isEmpty() ? "Not specified" : value,
                value.equals(text(planAge)) && planAge != null ? "Plan death scenario"
                        : planAge == null ? "Analyzer" : "Analyzer override"));
        if (value.isEmpty()) {
            errors.add(name + " Survivor Benefit Claiming Age is not specified.");
            return null;
        }
        if (person == null) {
            errors.add(name + " person is not configured.");
            return null;
        }
        try {
            int age = Integer.parseInt(value);
            // A persisted immediate-at-death election can be later than age 70.
            var election = new SocialSecuritySurvivorClaimingCandidate(person.getBirthDate().plusYears(age),
                    age, 0, "Age " + age);
            if (election.claimDate().isBefore(SocialSecuritySurvivorBenefitCalculator
                    .calculateEarliestSurvivorClaimDate(person.getBirthDate()))) throw new IllegalArgumentException();
            return election;
        } catch (RuntimeException invalid) {
            errors.add(name + " Survivor Benefit Claiming Age must be a whole year of at least 60.");
            return null;
        }
    }

    String summary() {
        return String.join("\n", elections.stream().map(row -> row.name() + ": " + row.value()
                + " (" + row.source() + ")").toList());
    }
}
