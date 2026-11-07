package com.daviddunn.retirementplanner.domain.financial;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Keeps the original six strict creator requirements; only volatility is newly optional. */
public final class ExpenseDeserializer extends StdDeserializer<Expense> {
    public ExpenseDeserializer() { super(Expense.class); }

    @Override
    public Expense deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            return (Expense) context.handleUnexpectedToken(Expense.class, parser);
        }
        String description = null;
        BigDecimal amount = null;
        BigDecimal volatility = null;
        GrowthCategory category = null;
        ExpenseType type = null;
        LocalDate start = null;
        LocalDate end = null;
        ContractualPremiumPolicy premium = null;
        Set<String> seen = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            parser.nextToken();
            if (!seen.add(name)) throw JsonMappingException.from(parser, "Duplicate expense property: " + name);
            switch (name) {
                case "description" -> description = context.readValue(parser, String.class);
                case "annualAmount" -> amount = context.readValue(parser, BigDecimal.class);
                case "growthCategory" -> category = context.readValue(parser, GrowthCategory.class);
                case "startDate" -> start = context.readValue(parser, LocalDate.class);
                case "endDate" -> end = context.readValue(parser, LocalDate.class);
                case "expenseType" -> type = context.readValue(parser, ExpenseType.class);
                case "annualExpenseVolatility" -> volatility = context.readValue(parser, BigDecimal.class);
                case "contractualPremium" -> {
                    if (!parser.isExpectedStartObjectToken()) throw JsonMappingException.from(parser, "Premium policy must be an object.");
                    var fields = new java.util.HashMap<String, String>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String field = parser.currentName();
                        parser.nextToken();
                        if (!Set.of("model", "insured").contains(field) || fields.containsKey(field) || parser.currentToken() != JsonToken.VALUE_STRING)
                            throw JsonMappingException.from(parser, "Unknown, duplicate or invalid premium policy property.");
                        fields.put(field, parser.getText());
                    }
                    if (fields.size() != 2) throw JsonMappingException.from(parser, "A closed premium model and named insured are required.");
                    try {
                        premium = new ContractualPremiumPolicy(
                                ContractualPremiumPolicy.Model.valueOf(fields.get("model")),
                                ContractualPremiumPolicy.Insured.valueOf(fields.get("insured")));
                    } catch (IllegalArgumentException invalid) {
                        throw JsonMappingException.from(parser, "Invalid contractual premium policy.", invalid);
                    }
                }
                default -> parser.skipChildren(); // Expense has always ignored unknown properties.
            }
        }
        if (type == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM && !Set.of("description", "annualAmount", "growthCategory",
                "startDate", "endDate", "expenseType", "annualExpenseVolatility", "contractualPremium").containsAll(seen))
            throw JsonMappingException.from(parser, "Unknown contractual premium property.");
        if (context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)) {
            for (String required : Set.of("description", "annualAmount", "growthCategory", "startDate", "endDate", "expenseType")) {
                if (!seen.contains(required)) throw JsonMappingException.from(parser, "Missing expense property: " + required);
            }
        }
        try {
            return new Expense(description, amount, category, start, end, type, volatility, premium);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw JsonMappingException.from(parser, "Invalid expense: " + failure.getMessage(), failure);
        }
    }
}
