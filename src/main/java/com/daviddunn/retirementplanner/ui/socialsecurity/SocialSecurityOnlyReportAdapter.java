package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import java.time.Instant;
import java.util.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.AnalysisPdfData.*;

/** Pure accepted SS output -> report. Never calls an optimizer or reads current inputs. */
final class SocialSecurityOnlyReportAdapter {
    static IntegratedAnalyzerReport capture(SocialSecurityReportSession session, ClaimingAgeGridView.Pair selected,
                                            List<String> status, Instant exported) {
        var model = session.presentation();
        var grid = SocialSecurityClaimingGridAdapter.from(model);
        var cells = grid.cells().stream().map(c -> new SocialSecurityCell(c.pair().primary(), c.pair().spouse(),
                c.source().expectedPresentValue(), money(c.source().expectedPresentValue()), ClaimingHeatMapPalette.forPercent(c.percent()),
                c.optimal(), c.retained(), c.pair().equals(selected))).toList();
        var summary = new ArrayList<String>();
        summary.add("Highest expected-PV tested complete strategy (Stage 2)");
        var best = model.rankedStrategies().getFirst().cell();
        summary.addAll(elections(best.strategy()));
        summary.add("Stage 2 expected PV: " + money(best.expectedPresentValue()));
        summary.add("Stage 2 expected real benefits: " + money(best.expectedRealBenefits()) + "; expected nominal benefits: " + money(best.expectedNominalBenefits()));
        summary.add("Exact highest complete-strategy ties: " + model.result().highestExpectedPresentValueStrategies().size());
        summary.add("Original Stage 1 optimal age pairs: " + model.result().retirementGrid().highestExpectedPresentValue().highestCells().stream()
                .map(c -> c.primaryClaimAge() + "/" + c.spouseClaimAge()).toList());
        grid.cells().stream().filter(c -> c.pair().equals(selected)).findFirst().ifPresent(cell -> {
            summary.add("Export-time selected claiming-age pair (Primary/Spouse): " + selected.primary() + "/" + selected.spouse());
            summary.add(cell.display().description());
            summary.add("Selected Stage 1 real: " + money(cell.source().expectedRealBenefits()) + "; nominal: " + money(cell.source().expectedNominalBenefits()));
            model.bestForRetirement(selected.primary(), selected.spouse()).ifPresent(expanded -> {
                summary.add("Best available expanded Stage 2 PV for selected pair: " + money(expanded.expectedPresentValue()));
                summary.addAll(elections(expanded.strategy()));
            });
        });
        var elections = new ArrayList<List<String>>();
        var values = new ArrayList<List<String>>();
        for (int i = 0; i < model.rankedStrategies().size(); i++) {
            var row = model.rankedStrategies().get(i); var cell = row.cell();
            elections.add(electionRow(i + 1, row.rank(), cell.strategy()));
            values.add(List.of(Integer.toString(i + 1), Integer.toString(row.rank()), money(cell.expectedPresentValue()),
                    money(cell.expectedRealBenefits()), money(cell.expectedNominalBenefits()), money(cell.expectedPrimarySelectedBenefits()), money(cell.expectedSpouseSelectedBenefits())));
        }
        var context = new ArrayList<>(status);
        context.add("Original analysis completed: " + session.completed());
        context.add("Original participants: " + session.context().primaryName() + " / " + session.context().spouseName());
        context.add("Analysis run ID: " + session.runId());
        if (!session.householdRecorded()) context.add(SocialSecurityReportSession.MISSING_NAME);
        String type = status.stream().anyMatch(line -> line.startsWith("Historical Analysis — Outdated"))
                ? "Historical Analysis — Outdated | Social Security Only" : "Social Security Only";
        return new IntegratedAnalyzerReport(type, session.reportHousehold(), session.started(), exported,
                context, summary, new SocialSecurityGrid(grid.primaryAges(), grid.spouseAges(), cells),
                List.of(new Section("Original Effective Assumptions", List.of(SocialSecurityStrategyAnalyzerDialog.assumptionText(session.context(), model.result()))),
                        new Section("Original Social Security Records", List.of(
                                "Primary FRA monthly benefit: " + money(session.context().primaryFraMonthlyBenefit()) + "; valuation year " + session.context().primaryBenefitValuationYear(),
                                "Spouse FRA monthly benefit: " + money(session.context().spouseFraMonthlyBenefit()) + "; valuation year " + session.context().spouseBenefitValuationYear(),
                                "Original primary claiming date: " + session.context().primaryCurrentClaimDate(), "Original spouse claiming date: " + session.context().spouseCurrentClaimDate()))),
                List.of(new Table("All Ranked Complete Strategies — Elections", electionColumns(), elections),
                        new Table("All Ranked Complete Strategies — Expected Values", List.of(new Column("Order", 45, true), new Column("Rank", 45, true),
                                new Column("Expected PV", 120, true), new Column("Expected real", 120, true), new Column("Expected nominal", 120, true),
                                new Column("Primary selected benefits", 130, true), new Column("Spouse selected benefits", 130, true)), values)),
                List.of(new Section("Limitations", List.of("Ranks use original precise expected PV values with original tie ordering; rounded display values do not establish ties.",
                        "Social Security-only mortality weighting does not run the retirement-plan projection engine. These are tested strategies, not a guarantee of benefits or investment results.",
                        "Stage 1 retirement-grid and Stage 2 complete-survivor-election objectives remain distinct. No calculation was rerun for this PDF."))));
    }
}
