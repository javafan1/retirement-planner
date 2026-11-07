package com.daviddunn.retirementplanner.app.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Removes only run-only variability from the known financial expense location. */
public final class DeterministicExpenseInputs {
    private DeterministicExpenseInputs() { }

    /** Normalize only the new closed form; every legacy expense tree remains exact. */
    public static void normalizePremiums(JsonNode inputs) {
        JsonNode expenses = inputs.path("household").path("expenses");
        if (!(expenses instanceof com.fasterxml.jackson.databind.node.ArrayNode array)) return;
        var slots = new java.util.ArrayList<Integer>();
        var premiums = new java.util.ArrayList<JsonNode>();
        for (int i = 0; i < array.size(); i++) {
            if (!"CONTRACTUAL_INSURANCE_PREMIUM".equals(array.get(i).path("expenseType").asText())) continue;
            var premium = (ObjectNode) array.get(i);
            premium.remove("description");
            premium.set("annualAmount", com.fasterxml.jackson.databind.node.DecimalNode.valueOf(premium.get("annualAmount").decimalValue().stripTrailingZeros()));
            slots.add(i); premiums.add(premium);
        }
        premiums.sort(java.util.Comparator.comparing(JsonNode::toString));
        for (int i=0;i<slots.size();i++) array.set(slots.get(i),premiums.get(i));
    }

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
