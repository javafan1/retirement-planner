package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecurityLegacyColaIsolationTest {

    @Test
    void implicitProjectionApisCannotUseLegacyCola() {

        Person person = person();
        SocialSecurityIncome income = socialSecurity(
                new BigDecimal("0.20"));

        assertThrows(
                UnsupportedOperationException.class,
                () -> income.getAnnualIncome(
                        person,
                        LocalDate.of(2033, 12, 31)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> income.getProjectedMonthlyBenefit(
                        person,
                        LocalDate.of(2033, 12, 31)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> income.getMonthlyBenefitAtDeath(
                        person,
                        LocalDate.of(2033, 12, 31)));
    }

    @Test
    void genericGuaranteedIncomeKeepsPensionAndExcludesSocialSecurity() {

        Person person = person();
        person.addIncomeSource(socialSecurity(new BigDecimal("0.20")));
        person.addIncomeSource(new Pension(
                "Pension",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1),
                null,
                new BigDecimal("1000"),
                new BigDecimal("0.02")));

        assertEquals(
                0,
                new BigDecimal("12000.00").compareTo(
                        person.getGuaranteedIncome(
                                LocalDate.of(2030, 12, 31))));
    }

    @Test
    void nonzeroLegacyColaRemainsJacksonCompatible() throws Exception {

        String json = """
                {"incomeType":"socialSecurity","name":"Social Security",
                "ownership":"PRIMARY","startDate":[2030,6,4],"endDate":null,
                "fullRetirementMonthlyBenefit":3000,"claimingAge":67,
                "annualColaRate":0.20,"benefitValuationYear":2030}
                """;

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        SocialSecurityIncome loaded = (SocialSecurityIncome)
                mapper.readValue(json, IncomeSource.class);

        assertEquals(
                0,
                new BigDecimal("0.20").compareTo(
                        loaded.getAnnualColaRate()));

        String roundTrip = mapper.writerFor(IncomeSource.class)
                .writeValueAsString(loaded);

        assertEquals(true, roundTrip.contains("annualColaRate"));

        Person primary = person();
        primary.addIncomeSource(loaded);

        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("0.025")),
                new TaxAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                2,
                LocalDate.of(2030, 1, 1));

        Projection projection = new ProjectionEngine().project(
                new RetirementPlan(
                        new Household(
                                primary,
                                new Person(
                                        "Lisa",
                                        "Dunn",
                                        LocalDate.of(1965, 2, 28))),
                        new AccountPortfolio(),
                        assumptions));

        assertEquals(
                0,
                loaded.getAnnualIncome(
                                primary,
                                LocalDate.of(2031, 12, 31),
                                new BigDecimal("0.025"))
                        .compareTo(projection.getLastYear()
                                .getSocialSecurityResult()
                                .primaryOwnBenefit()));
    }

    private Person person() {

        return new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));
    }

    private SocialSecurityIncome socialSecurity(
            BigDecimal legacyCola) {

        return new SocialSecurityIncome(
                "Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4),
                null,
                new BigDecimal("3000"),
                67,
                legacyCola,
                2030);
    }
}
