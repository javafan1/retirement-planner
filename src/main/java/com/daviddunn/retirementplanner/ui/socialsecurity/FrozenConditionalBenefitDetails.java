package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section;
import java.util.List;

/** Reads completed report context, never the active plan. */
final class FrozenConditionalBenefitDetails {
    private FrozenConditionalBenefitDetails() { }
    static String text(List<Section> sections) {
        var lines = sections.stream().flatMap(s -> s.lines().stream())
                .filter(s -> s.contains("Conditional benefit assumptions") || s.contains("conditional benefits configured")
                        || s.contains("fully taxable ordinary non-pension") || s.contains("Configured benefits are conditional")
                        || s.contains("life insurance") || s.contains("qualification assertion") || s.contains("Future investment earnings"))
                .toList();
        return lines.isEmpty() ? "" : "\n\nOriginal conditional benefit assumptions\n" + String.join("\n", lines);
    }
}
