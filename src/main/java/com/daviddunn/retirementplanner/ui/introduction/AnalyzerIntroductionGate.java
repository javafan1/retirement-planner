package com.daviddunn.retirementplanner.ui.introduction;

import com.daviddunn.retirementplanner.application.settings.AnalyzerIntroduction;
import com.daviddunn.retirementplanner.application.settings.ApplicationSettingsService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.stage.Window;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** FX-owned pre-launch gate. One active introduction/launch per host; no financial work or new executor. */
public final class AnalyzerIntroductionGate {
    private final ApplicationSettingsService settings;
    private Request active;

    private static final class Request {
        AnalyzerIntroductionDialog dialog;
        ChangeListener<Boolean> ownerListener;
        boolean continued;
    }

    public AnalyzerIntroductionGate(ApplicationSettingsService settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    /** The launcher must release its token when the existing analyzer closes, or immediately for navigation. */
    public void request(Window owner, AnalyzerIntroduction entry, boolean single,
            BooleanSupplier valid, Consumer<Runnable> open) {
        Objects.requireNonNull(entry);
        Objects.requireNonNull(valid);
        Objects.requireNonNull(open);
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("FX owner required.");
        if (active != null || owner == null || !owner.isShowing() || !valid.getAsBoolean()) return;
        var request = new Request();
        active = request;
        request.ownerListener = (observable, old, showing) -> {
            if (!showing) {
                if (request.dialog != null) request.dialog.dismiss();
                release(request, owner);
            }
        };
        owner.showingProperty().addListener(request.ownerListener);
        Runnable launch = () -> {
            if (active != request || !owner.isShowing() || !valid.getAsBoolean()) {
                if (request.dialog != null) request.dialog.dismiss();
                release(request, owner);
                return;
            }
            request.continued = true;
            if (request.dialog != null) {
                var dialog = request.dialog;
                request.dialog = null;
                dialog.dismiss();
            }
            try { open.accept(() -> release(request, owner)); }
            catch (RuntimeException failure) {
                release(request, owner);
                throw failure;
            }
        };
        if (!settings.snapshot().showsIntroduction(entry)) {
            launch.run();
            return;
        }
        request.dialog = new AnalyzerIntroductionDialog(owner, AnalyzerIntroductionContent.forEntry(entry, single), suppress -> {
            if (active != request || request.continued) return;
            if (!valid.getAsBoolean()) {
                request.dialog.dismiss();
                release(request, owner);
                return;
            }
            if (!suppress) {
                launch.run();
                return;
            }
            request.dialog.saving(true);
            settings.suppressIntroduction(entry).whenComplete((ignored, failure) -> Platform.runLater(() -> {
                if (active != request) return;
                if (failure != null) {
                    request.dialog.failed();
                    return;
                }
                launch.run();
            }));
        });
        request.dialog.setOnHidden(event -> {
            if (!request.continued) release(request, owner);
        });
        request.dialog.show();
    }

    private void release(Request request, Window owner) {
        owner.showingProperty().removeListener(request.ownerListener);
        request.dialog = null;
        if (active == request) active = null;
    }
}
