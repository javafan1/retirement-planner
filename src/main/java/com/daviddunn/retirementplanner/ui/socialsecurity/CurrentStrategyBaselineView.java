package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Dialog-session inputs. Never writes to the plan. */
final class CurrentStrategyBaselineView extends VBox {
    final TextField primarySurvivor = new TextField();
    final TextField spouseSurvivor = new TextField();
    private final Label primaryRetirement = new Label();
    private final Label spouseRetirement = new Label();
    private final Label primarySource = new Label();
    private final Label spouseSource = new Label();
    private final Label validation = new Label();
    private RetirementPlan plan;
    private boolean loading;
    private boolean primaryEdited;
    private boolean spouseEdited;
    private Runnable changed = () -> { };

    CurrentStrategyBaselineView(RetirementPlan plan) {
        super(6);
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, new Label("Primary retirement claiming age:"), primaryRetirement, new Label("Plan"));
        grid.addRow(1, new Label("Spouse retirement claiming age:"), spouseRetirement, new Label("Plan"));
        grid.addRow(2, new Label("Primary Survivor Benefit Claiming Age:"), primarySurvivor, primarySource);
        grid.addRow(3, new Label("Spouse Survivor Benefit Claiming Age:"), spouseSurvivor, spouseSource);
        primarySurvivor.setPromptText("Not specified");
        spouseSurvivor.setPromptText("Not specified");
        primarySurvivor.setTooltip(HelpIcon.createTooltip("Used when the spouse dies first. This is the age at which "
                + "the primary person would claim a survivor benefit if eligible. It completes the Current Strategy "
                + "baseline for longevity-weighted analysis and does not change the deterministic plan's death scenario."));
        spouseSurvivor.setTooltip(HelpIcon.createTooltip("Used when the primary dies first. This is the age at which "
                + "the spouse would claim a survivor benefit if eligible. It completes the Current Strategy "
                + "baseline for longevity-weighted analysis and does not change the deterministic plan's death scenario."));
        var help = new Label("Comparison baseline only; candidate strategies are unchanged. Survivor ages are whole years "
                + "60 or older, kept only while this analyzer is open. Benefits cannot begin before death. "
                + "This election is separate from retirement claiming; waiting past Survivor FRA adds no benefit increase.");
        help.setWrapText(true);
        help.setMinHeight(Region.USE_PREF_SIZE);
        validation.setWrapText(true);
        validation.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(help, grid, validation);
        setMinHeight(Region.USE_PREF_SIZE);
        primarySurvivor.textProperty().addListener((o, before, after) -> edited(true));
        spouseSurvivor.textProperty().addListener((o, before, after) -> edited(false));
        load(plan);
    }

    void onChanged(Runnable action) { changed = action; }

    void load(RetirementPlan next) {
        loading = true;
        if (plan != next) {
            primaryEdited = false;
            spouseEdited = false;
        }
        plan = next;
        if (!primaryEdited) primarySurvivor.setText(CurrentStrategyBaseline.text(CurrentStrategyBaseline.planSurvivorAge(plan, true)));
        if (!spouseEdited) spouseSurvivor.setText(CurrentStrategyBaseline.text(CurrentStrategyBaseline.planSurvivorAge(plan, false)));
        loading = false;
        refresh();
    }

    CurrentStrategyBaseline snapshot() {
        return CurrentStrategyBaseline.capture(plan, primarySurvivor.getText(), spouseSurvivor.getText());
    }

    private void edited(boolean primary) {
        if (loading) return;
        if (primary) primaryEdited = true; else spouseEdited = true;
        refresh();
        changed.run();
    }

    private void refresh() {
        var baseline = snapshot();
        primaryRetirement.setText(baseline.elections().get(0).value());
        spouseRetirement.setText(baseline.elections().get(1).value());
        primarySource.setText(baseline.elections().get(2).source());
        spouseSource.setText(baseline.elections().get(3).source());
        validation.setText(baseline.strategy().isPresent() ? "Complete baseline. Run weighted analysis to value it."
                : baseline.problem() + " Candidate analysis can still run.");
    }
}
