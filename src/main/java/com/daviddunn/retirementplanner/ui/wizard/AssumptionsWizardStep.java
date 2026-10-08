package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class AssumptionsWizardStep implements NewPlanWizardStep {

    private final NewPlanDraft draft;
    private final AssumptionsView editor = new AssumptionsView(true);
    private final VBox content;

    public AssumptionsWizardStep(NewPlanDraft draft) {
        this.draft = java.util.Objects.requireNonNull(draft);
        Label description = new Label("Set the main plan assumptions. Advanced taxes, death scenarios and Roth settings remain available in the tabs. You can adjust longevity and present-value settings when running an analysis.");
        description.setWrapText(true);
        description.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        content = new VBox(10, description, editor);
        editor.load(draft.getPlan());
    }

    @Override
    public String title() { return "Assumptions"; }

    @Override
    public Node content() { return content; }

    @Override
    public void onEntering() { editor.refresh(draft.getPlan()); }

    @Override
    public boolean validateAndApply() { return editor.applyChanges(); }
}
