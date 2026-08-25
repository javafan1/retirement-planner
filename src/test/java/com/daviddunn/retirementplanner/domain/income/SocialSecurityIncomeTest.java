package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityIncomeTest {

    private final Person person =
            new Person(
                    "John",
                    "Doe",
                    LocalDate.of(1963, 1, 1));

    @Test
    void claimingAt67ReturnsFullRetirementBenefit() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("36000.00"),
                annualIncome);
    }

    @Test
    void claimingAt62ReturnsReducedBenefit() {

        SocialSecurityIncome income =
                createIncome(62);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("25200.00"),
                annualIncome);
    }

    @Test
    void claimingAt70ReturnsDelayedBenefit() {

        SocialSecurityIncome income =
                createIncome(70);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("44640.00"),
                annualIncome);
    }

    @Test
    void beforeStartDateReturnsZero() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2029, 12, 31));

        assertEquals(
                BigDecimal.ZERO,
                annualIncome);
    }

    @Test
    void socialSecurityAppliesColaAfterFirstYear() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2031, 1, 1));

        assertEquals(
                new BigDecimal("36900.00"),
                annualIncome);
    }

    private SocialSecurityIncome createIncome(int claimingAge) {

        return new SocialSecurityIncome(
                "Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1),
                null,
                new BigDecimal("3000"),
                claimingAge,
                new BigDecimal("0.025"));
    }

    @Test
    void projectedMonthlyBenefitUsesRetirementBenefitAndCola() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome socialSecurity =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO);

        BigDecimal monthlyBenefit =
                socialSecurity.getProjectedMonthlyBenefit(
                        person,
                        LocalDate.of(2035, 1, 1));

        BigDecimal expected =
                SocialSecurityBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                person.getBirthDate(),
                                67);

        assertEquals(
                0,
                expected.compareTo(monthlyBenefit));
    }

    @Test
    void monthlyBenefitAtDeathUsesActualClaimedBenefit() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome income =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO);

        BigDecimal result =
                income.getMonthlyBenefitAtDeath(
                        person,
                        LocalDate.of(2035, 1, 1));

        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }


    @Test
    void monthlyBenefitAtDeathUsesFullRetirementBenefitWhenNotYetClaimed() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome income =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2038, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        70,
                        new BigDecimal("0.025"));

        BigDecimal result =
                income.getMonthlyBenefitAtDeath(
                        person,
                        LocalDate.of(2035, 1, 1));


        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }

    @ParameterizedTest
    @ValueSource(ints = {62, 63, 64, 65, 66, 67, 68, 69, 70})
    void claimYearBenefitUsesValuationYearColaForEveryClaimingAge(
            int claimingAge) {

        Person david = new Person(
                "David",
                "Dunn",
                LocalDate.of(1963, 6, 4));

        LocalDate claimDate = david.getBirthDate()
                .plusYears(claimingAge);

        SocialSecurityIncome income = new SocialSecurityIncome(
                "Social Security",
                AccountOwnership.PRIMARY,
                claimDate,
                null,
                new BigDecimal("3000"),
                claimingAge,
                BigDecimal.ZERO,
                2026);

        BigDecimal cola = new BigDecimal("0.025");
        BigDecimal expected = SocialSecurityBenefitCalculator
                .calculateMonthlyBenefit(
                        new BigDecimal("3000"),
                        david.getBirthDate(),
                        claimingAge)
                .multiply(BigDecimal.ONE.add(cola).pow(
                        Math.max(claimDate.getYear() - 2026, 0)))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertEquals(expected, income.getProjectedMonthlyBenefit(
                david, claimDate, cola));
        assertEquals(2026, income.getBenefitValuationYear());
    }

    @Test
    void positiveColaAppliesBeforeAndAfterClaiming() {

        Person david = new Person("David", "Dunn",
                LocalDate.of(1963, 6, 4));
        SocialSecurityIncome income = new SocialSecurityIncome(
                "Social Security", AccountOwnership.PRIMARY,
                LocalDate.of(2033, 6, 4), null,
                new BigDecimal("3000"), 70, BigDecimal.ZERO, 2026);

        BigDecimal cola = new BigDecimal("0.025");
        BigDecimal claimYear = income.getProjectedMonthlyBenefit(
                david, LocalDate.of(2033, 12, 31), cola);
        BigDecimal nextYear = income.getProjectedMonthlyBenefit(
                david, LocalDate.of(2034, 12, 31), cola);

        assertEquals(new BigDecimal("4421.91"), claimYear);
        assertEquals(new BigDecimal("4532.46"), nextYear);
        assertEquals(new BigDecimal("30953.37"), income.getAnnualIncome(
                david, LocalDate.of(2033, 12, 31), cola));
    }

    @Test
    void jacksonPersistsValuationYearAndLegacyJsonDefaultsToStartYear()
            throws Exception {

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        SocialSecurityIncome current = new SocialSecurityIncome(
                "Social Security", AccountOwnership.PRIMARY,
                LocalDate.of(2033, 6, 4), null,
                new BigDecimal("3000"), 70, BigDecimal.ZERO, 2026);

        SocialSecurityIncome roundTrip = (SocialSecurityIncome)
                mapper.readValue(
                        mapper.writerFor(IncomeSource.class)
                                .writeValueAsString(current),
                        IncomeSource.class);

        assertEquals(2026, roundTrip.getBenefitValuationYear());

        String legacyJson = """
                {"incomeType":"socialSecurity","name":"Social Security","ownership":"PRIMARY",
                "startDate":[2033,6,4],"endDate":null,
                "fullRetirementMonthlyBenefit":3000,"claimingAge":70,
                "annualColaRate":0.025}
                """;

        SocialSecurityIncome legacy = (SocialSecurityIncome)
                mapper.readValue(legacyJson, IncomeSource.class);

        assertEquals(2033, legacy.getBenefitValuationYear());
    }


}
