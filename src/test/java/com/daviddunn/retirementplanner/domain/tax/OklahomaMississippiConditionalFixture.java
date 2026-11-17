package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitReceipt;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import java.util.List;

/** Test-only access to the actual shared attribution boundary; no production visibility change. */
public final class OklahomaMississippiConditionalFixture {
    public TaxIncome attach(TaxIncome income, ConditionalBenefitReceipt receipt) {
        return TaxIncomeAttribution.attach(income,List.of(IncomeSourceAttribution.conditional(receipt)),receipt.year());
    }
}
