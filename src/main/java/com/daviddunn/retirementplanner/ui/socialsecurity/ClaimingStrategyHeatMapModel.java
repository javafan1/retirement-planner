package com.daviddunn.retirementplanner.ui.socialsecurity;

import java.math.BigDecimal;
import java.util.*;

/** Immutable projection of finished analyzer results. No financial engine or strategy search is invoked. */
record ClaimingStrategyHeatMapModel(
        List<Integer> primaryAges,
        List<Integer> spouseAges,
        List<ClaimingStrategyHeatMapCell> cells,
        Optional<BigDecimal> optimalValue,
        String context) {
    ClaimingStrategyHeatMapModel {
        primaryAges = List.copyOf(primaryAges);
        spouseAges = List.copyOf(spouseAges);
        cells = List.copyOf(cells);
        Objects.requireNonNull(optimalValue);
        Objects.requireNonNull(context);
    }

    ClaimingStrategyHeatMapCell cell(int primaryAge, int spouseAge) {
        int column = primaryAges.indexOf(primaryAge);
        int row = spouseAges.indexOf(spouseAge);
        if (column < 0 || row < 0) throw new IllegalArgumentException("Claiming ages are outside the heat map.");
        return cells.get(row * primaryAges.size() + column);
    }

}
