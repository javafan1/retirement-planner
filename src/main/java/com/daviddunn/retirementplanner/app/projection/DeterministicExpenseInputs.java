package com.daviddunn.retirementplanner.app.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Removes only run-only variability from the known financial expense location. */
public final class DeterministicExpenseInputs {
    private DeterministicExpenseInputs() { }

    public static void removeVolatility(JsonNode inputs) {
        JsonNode expenses = inputs.path("household").path("expenses");
        if (!expenses.isArray()) return;
        for (JsonNode expense : expenses) {
            if (expense instanceof ObjectNode object
                    && "RECURRING".equals(expense.path("expenseType").asText("RECURRING"))) {
                object.remove("annualExpenseVolatility");
            }
        }
    }
}
