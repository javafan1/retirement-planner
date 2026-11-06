package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.Household;
import java.util.*;

/** Collection ownership and household validity, independent of execution admission. */
public final class ConditionalBenefits {
    public static final int MAX_DEFINITIONS = 100;
    private ConditionalBenefits() { }

    public static List<ConditionalBenefitDefinition> validatedCopy(List<ConditionalBenefitDefinition> values, Household household) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > MAX_DEFINITIONS) throw new IllegalArgumentException("Too many conditional benefit definitions.");
        var ids = new HashSet<UUID>();
        for (var value : values) {
            Objects.requireNonNull(value, "Conditional benefit collection cannot contain null.").validateFor(household);
            if (!ids.add(value.id())) throw new IllegalArgumentException("Duplicate conditional benefit ID.");
        }
        return List.copyOf(values);
    }
}
