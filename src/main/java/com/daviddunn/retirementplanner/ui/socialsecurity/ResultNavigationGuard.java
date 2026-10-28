package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.application.Platform;
import javafx.scene.control.*;
import java.util.*;
import java.util.function.*;

/** FX-confined, presentation-only arbitration. It never accepts or publishes financial results. */
final class ResultNavigationGuard implements AutoCloseable {
    record Ticket(long epoch, Object binding) { }
    private final Supplier<Object> binding;
    private record Displayed(Object binding, Object identity) { }
    private final Map<Object, Displayed> initialized = new HashMap<>();
    private long epoch;
    private boolean automatic;
    private boolean closed;

    ResultNavigationGuard(Supplier<Object> binding) { this.binding = binding; }
    Ticket capture() { return new Ticket(epoch, binding.get()); }
    void navigated() { if (!automatic) epoch++; }
    void watch(TabPane tabs) {
        if (tabs instanceof IntegratedAnalysisResultTabs integrated) integrated.onNavigation(this::navigated);
        else tabs.getSelectionModel().selectedItemProperty().addListener((o, before, after) -> navigated());
    }
    private boolean valid(Ticket ticket) {
        return !closed && ticket != null && ticket.epoch == epoch && Objects.equals(ticket.binding, binding.get());
    }
    java.util.function.BooleanSupplier revealGuard() {
        var ticket = capture();
        return () -> valid(ticket);
    }
    void display(Object kind, Object resultIdentity, Ticket ticket, TabPane tabs, Tab target,
                 BooleanSupplier workspaceVisible) {
        var displayed = new Displayed(binding.get(), resultIdentity);
        if (closed || ticket == null || !Objects.equals(ticket.binding, displayed.binding)) return;
        var before = initialized.get(kind);
        boolean sameResult = before != null && (before.identity == resultIdentity
                || before.identity instanceof UUID && before.identity.equals(resultIdentity));
        if (before != null && Objects.equals(before.binding, displayed.binding) && sameResult) return;
        initialized.put(kind, displayed);
        if (!valid(ticket)) return;
        automatic = true;
        try {
            if (tabs instanceof IntegratedAnalysisResultTabs integrated) integrated.selectInitial(target);
            else tabs.getSelectionModel().select(target);
        }
        finally { automatic = false; }
        Platform.runLater(() -> {
            if (valid(ticket) && initialized.get(kind) == displayed
                    && tabs.getSelectionModel().getSelectedItem() == target && workspaceVisible.getAsBoolean())
                IntegratedAnalysisResultTabs.reveal(tabs);
        });
    }
    @Override public void close() { closed = true; epoch++; initialized.clear(); }
}
