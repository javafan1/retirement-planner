package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.*;
import java.util.*;
import java.util.function.Supplier;

/** Explicit read-only navigation. History performs artifact verification; no slot or control is promoted. */
public final class AssociatedAnalyzerAction extends Button {
    private final ApplicationController source;
    private final Supplier<AnalysisKind> kind;

    public AssociatedAnalyzerAction(ApplicationController source, Supplier<AnalysisKind> kind) {
        super("View Current Projection's Associated Result");
        this.source = Objects.requireNonNull(source);
        this.kind = Objects.requireNonNull(kind);
        setAccessibleHelp("Open the accepted current projection's recorded analysis in read-only History. The saved artifact is verified on opening.");
        setOnAction(event -> {
            var projection = source.getAcceptedProjectionSnapshot();
            var selected = kind.get();
            if (!available() || projection == null || selected == null) { refresh(); return; }
            var history = new ProjectionAnalyzerHistoryDialog(source, projection.id(), selected);
            if (getScene() != null) history.initOwner(getScene().getWindow());
            history.showAndWait();
        });
        refresh();
    }

    public boolean available() {
        var projection = source.getAcceptedProjectionSnapshot();
        var selected = kind.get();
        return projection != null && projection.owner().equals(source.getAnalysisSessionId()) && selected != null
                && source.getAnalyzerAssociations(projection.id()).stream().anyMatch(ref ->
                        ref.owner().equals(source.getAnalysisSessionId()) && ref.kind() == selected
                                && ref.projectionInputDigest().equals(projection.inputDigest()));
    }

    public void refresh() {
        boolean present = available();
        setDisable(!present);
        setTooltip(new Tooltip(present
                ? "View Current's separate saved analysis without recalculation. Integrity is verified on opening; pending pins require ordinary Save. Current controls and the displayed last-success result are unchanged."
                : "No recorded analysis of this kind is associated with the accepted Current projection. Legacy results are not inferred."));
    }
}
