package com.daviddunn.retirementplanner.domain.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

/** Authoritative post-tax settlement of one projection year's household cash. */
@JsonDeserialize(using = HouseholdCashSettlement.Reader.class)
public record HouseholdCashSettlement(
        BigDecimal guaranteedIncome,
        BigDecimal projectedPeriodRmdCash,
        BigDecimal spendingWithdrawal,
        BigDecimal taxFundingWithdrawal,
        BigDecimal ordinaryExpenses,
        BigDecimal medicarePremiums,
        BigDecimal incomeTaxes,
        BigDecimal retainedHouseholdSurplus,
        BigDecimal retainedFromExcessRmd,
        BigDecimal retainedFromGuaranteedIncome,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroAmount.class)
        BigDecimal retainedFromTaxFundingSurplus) {

    private static final BigDecimal RECONCILIATION_TOLERANCE =
            new BigDecimal("0.02");

    public HouseholdCashSettlement(BigDecimal guaranteedIncome, BigDecimal projectedPeriodRmdCash,
            BigDecimal spendingWithdrawal, BigDecimal taxFundingWithdrawal, BigDecimal ordinaryExpenses,
            BigDecimal medicarePremiums, BigDecimal incomeTaxes, BigDecimal retainedHouseholdSurplus,
            BigDecimal retainedFromExcessRmd, BigDecimal retainedFromGuaranteedIncome) {
        this(guaranteedIncome, projectedPeriodRmdCash, spendingWithdrawal, taxFundingWithdrawal,
                ordinaryExpenses, medicarePremiums, incomeTaxes, retainedHouseholdSurplus,
                retainedFromExcessRmd, retainedFromGuaranteedIncome, BigDecimal.ZERO);
    }

    public HouseholdCashSettlement {
        requireNonNegative(guaranteedIncome, "Guaranteed income");
        requireNonNegative(projectedPeriodRmdCash, "Projected-period RMD cash");
        requireNonNegative(spendingWithdrawal, "Spending withdrawal");
        requireNonNegative(taxFundingWithdrawal, "Tax-funding withdrawal");
        requireNonNegative(ordinaryExpenses, "Ordinary expenses");
        requireNonNegative(medicarePremiums, "Medicare premiums");
        requireNonNegative(incomeTaxes, "Income taxes");
        requireNonNegative(retainedHouseholdSurplus, "Retained household surplus");
        requireNonNegative(retainedFromExcessRmd, "Retained from excess RMD");
        requireNonNegative(retainedFromGuaranteedIncome,
                "Retained from guaranteed income");
        requireNonNegative(retainedFromTaxFundingSurplus, "Retained from tax-funding surplus");
        if (retainedFromTaxFundingSurplus.signum() == 0) {
            retainedFromTaxFundingSurplus = BigDecimal.ZERO;
        }

        BigDecimal attributed = retainedFromExcessRmd
                .add(retainedFromGuaranteedIncome).add(retainedFromTaxFundingSurplus);
        if (attributed.compareTo(retainedHouseholdSurplus) != 0) {
            throw new IllegalArgumentException(
                    "Retained-surplus attribution must equal total retained surplus.");
        }

        BigDecimal sources = guaranteedIncome
                .add(projectedPeriodRmdCash)
                .add(spendingWithdrawal)
                .add(taxFundingWithdrawal);
        BigDecimal uses = ordinaryExpenses
                .add(medicarePremiums)
                .add(incomeTaxes)
                .add(retainedHouseholdSurplus);
        if (sources.subtract(uses).abs()
                .compareTo(RECONCILIATION_TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Household cash sources and uses do not reconcile: sources="
                            + sources + ", uses=" + uses);
        }
        if (retainedFromTaxFundingSurplus.signum() > 0) {
            var available = taxCash(guaranteedIncome, projectedPeriodRmdCash, ordinaryExpenses, medicarePremiums);
            var exactSpending = ordinaryExpenses.add(medicarePremiums).subtract(guaranteedIncome)
                    .subtract(projectedPeriodRmdCash).max(BigDecimal.ZERO);
            if (sources.compareTo(uses) != 0 || exactSpending.compareTo(spendingWithdrawal) != 0
                    || available.guaranteed().subtract(incomeTaxes).max(BigDecimal.ZERO)
                            .compareTo(retainedFromGuaranteedIncome) != 0
                    || available.rmd().subtract(incomeTaxes.subtract(available.guaranteed()).max(BigDecimal.ZERO))
                            .max(BigDecimal.ZERO).compareTo(retainedFromExcessRmd) != 0
                    || taxFundingWithdrawal.subtract(incomeTaxes.subtract(available.total()).max(BigDecimal.ZERO))
                            .compareTo(retainedFromTaxFundingSurplus) != 0) {
                throw new IllegalArgumentException("Tax-funding surplus violates exact cash source priority.");
            }
        }
    }

    public static HouseholdCashSettlement calculate(
            BigDecimal guaranteedIncome,
            BigDecimal projectedPeriodRmdCash,
            BigDecimal spendingWithdrawal,
            BigDecimal taxFundingWithdrawal,
            BigDecimal ordinaryExpenses,
            BigDecimal medicarePremiums,
            BigDecimal incomeTaxes) {

        BigDecimal nonTaxObligations =
                ordinaryExpenses.add(medicarePremiums);

        BigDecimal guaranteedIncomeRemaining =
                guaranteedIncome.subtract(nonTaxObligations)
                        .max(BigDecimal.ZERO);
        BigDecimal obligationsAfterGuaranteedIncome =
                nonTaxObligations.subtract(guaranteedIncome)
                        .max(BigDecimal.ZERO);
        BigDecimal rmdRemaining =
                projectedPeriodRmdCash
                        .subtract(obligationsAfterGuaranteedIncome)
                        .max(BigDecimal.ZERO);

        BigDecimal retainedFromGuaranteedIncome =
                guaranteedIncomeRemaining.subtract(incomeTaxes)
                        .max(BigDecimal.ZERO);
        BigDecimal taxesAfterGuaranteedIncome =
                incomeTaxes.subtract(guaranteedIncomeRemaining)
                        .max(BigDecimal.ZERO);
        BigDecimal retainedFromExcessRmd =
                rmdRemaining.subtract(taxesAfterGuaranteedIncome)
                        .max(BigDecimal.ZERO);
        BigDecimal retainedSurplus =
                retainedFromGuaranteedIncome.add(retainedFromExcessRmd);

        return new HouseholdCashSettlement(
                guaranteedIncome,
                projectedPeriodRmdCash,
                spendingWithdrawal,
                taxFundingWithdrawal,
                ordinaryExpenses,
                medicarePremiums,
                incomeTaxes,
                retainedSurplus,
                retainedFromExcessRmd,
                retainedFromGuaranteedIncome);
    }

    public static HouseholdCashSettlement zero() {
        return calculate(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public static HouseholdCashSettlement settleMinimumSufficient(
            BigDecimal guaranteedIncome, BigDecimal projectedPeriodRmdCash, BigDecimal spendingWithdrawal,
            BigDecimal executedTaxCash, BigDecimal ordinaryExpenses, BigDecimal medicarePremiums, BigDecimal incomeTaxes) {
        var cash = taxCash(guaranteedIncome, projectedPeriodRmdCash, ordinaryExpenses, medicarePremiums);
        var spending = ordinaryExpenses.add(medicarePremiums).subtract(guaranteedIncome)
                .subtract(projectedPeriodRmdCash).max(BigDecimal.ZERO);
        var remaining = incomeTaxes.subtract(cash.total()).max(BigDecimal.ZERO);
        if (spending.compareTo(spendingWithdrawal) != 0 || executedTaxCash.compareTo(remaining) < 0) {
            throw new IllegalArgumentException("Minimum-sufficient settlement requires exact spending and tax coverage.");
        }
        var guaranteedRetained = cash.guaranteed().subtract(incomeTaxes).max(BigDecimal.ZERO);
        var rmdRetained = cash.rmd().subtract(incomeTaxes.subtract(cash.guaranteed()).max(BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
        var fundingRetained = executedTaxCash.subtract(remaining);
        return new HouseholdCashSettlement(guaranteedIncome, projectedPeriodRmdCash, spendingWithdrawal,
                executedTaxCash, ordinaryExpenses, medicarePremiums, incomeTaxes,
                guaranteedRetained.add(rmdRetained).add(fundingRetained), rmdRetained, guaranteedRetained, fundingRetained);
    }

    private record TaxCash(BigDecimal guaranteed, BigDecimal rmd) {
        BigDecimal total() { return guaranteed.add(rmd); }
    }

    private static TaxCash taxCash(BigDecimal guaranteed, BigDecimal rmd, BigDecimal expenses, BigDecimal medicare) {
        var remaining = expenses.add(medicare).subtract(guaranteed);
        return new TaxCash(remaining.negate().max(BigDecimal.ZERO),
                rmd.subtract(remaining.max(BigDecimal.ZERO)).max(BigDecimal.ZERO));
    }

    public static final class ZeroAmount {
        @Override public boolean equals(Object other) {
            return other instanceof BigDecimal amount && amount.signum() == 0;
        }
        @Override public int hashCode() { return 0; }
    }

    /** Keep every legacy field required and preserve the input decimal scale. */
    public static final class Reader extends JsonDeserializer<HouseholdCashSettlement> {
        private static final String FUNDING_SURPLUS = "retainedFromTaxFundingSurplus";
        private static final Set<String> REQUIRED = Set.of("guaranteedIncome", "projectedPeriodRmdCash",
                "spendingWithdrawal", "taxFundingWithdrawal", "ordinaryExpenses", "medicarePremiums",
                "incomeTaxes", "retainedHouseholdSurplus", "retainedFromExcessRmd", "retainedFromGuaranteedIncome");

        @Override
        public HouseholdCashSettlement deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedStartObjectToken()) {
                throw JsonMappingException.from(parser, "Household settlement object required.");
            }
            var values = new HashMap<String, BigDecimal>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var name = parser.currentName();
                var token = parser.nextToken();
                if ((!REQUIRED.contains(name) && !FUNDING_SURPLUS.equals(name))
                        || token == null || !token.isNumeric() || values.containsKey(name)) {
                    throw JsonMappingException.from(parser, "Invalid household settlement field " + name);
                }
                values.put(name, parser.getDecimalValue());
            }
            if (parser.currentToken() != JsonToken.END_OBJECT || !values.keySet().containsAll(REQUIRED)) {
                throw JsonMappingException.from(parser, "Missing household settlement field.");
            }
            try {
                return new HouseholdCashSettlement(values.get("guaranteedIncome"), values.get("projectedPeriodRmdCash"),
                        values.get("spendingWithdrawal"), values.get("taxFundingWithdrawal"), values.get("ordinaryExpenses"),
                        values.get("medicarePremiums"), values.get("incomeTaxes"), values.get("retainedHouseholdSurplus"),
                        values.get("retainedFromExcessRmd"), values.get("retainedFromGuaranteedIncome"),
                        values.getOrDefault(FUNDING_SURPLUS, BigDecimal.ZERO));
            } catch (IllegalArgumentException invalid) {
                throw JsonMappingException.from(parser, invalid.getMessage(), invalid);
            }
        }
    }

    public BigDecimal availableHouseholdCashBeforeTaxes() {
        return guaranteedIncome
                .add(projectedPeriodRmdCash)
                .subtract(ordinaryExpenses)
                .subtract(medicarePremiums)
                .max(BigDecimal.ZERO);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " is required.");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative.");
        }
    }
}
