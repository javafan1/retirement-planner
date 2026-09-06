package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomeSourcesColaPresentationTest {

    @Test
    void socialSecurityIsNotApplicableWhilePensionShowsConfiguredCola() {

        SocialSecurityIncome socialSecurity = new SocialSecurityIncome(
                "Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4),
                null,
                new BigDecimal("3000"),
                67,
                new BigDecimal("0.20"),
                2030);

        Pension pension = new Pension(
                "Pension",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1),
                null,
                new BigDecimal("1000"),
                new BigDecimal("0.02"));

        assertEquals(
                "N/A",
                IncomeSourcesView.formatColaForIncomeSource(
                        socialSecurity));
        assertEquals(
                "2%",
                IncomeSourcesView.formatColaForIncomeSource(
                        pension));
    }
}
