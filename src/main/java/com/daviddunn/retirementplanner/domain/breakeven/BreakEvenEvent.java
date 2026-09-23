package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.time.LocalDate;
import java.util.List;

/** Exact stored retirement elections grouped only by person and calendar year. */
public record BreakEvenEvent(int year, AccountOwnership person, String name, List<Election> elections) {
    public enum PlanScope { BASELINE, CURRENT }
    public record Election(PlanScope plan, int claimingAge, LocalDate claimDate) { }
    public BreakEvenEvent { elections = List.copyOf(elections); }
}
