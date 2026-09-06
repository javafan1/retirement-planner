package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionYearReportingAgeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsPrimaryAgeAtEndOfEachProjectionYear() {

        Projection projection =
                project(
                        LocalDate.of(1963, 6, 4),
                        LocalDate.of(2027, 1, 1),
                        4);

        assertEquals(64, projection.getYearAt(0).getPrimaryPersonAge());
        assertEquals(65, projection.getYearAt(1).getPrimaryPersonAge());
        assertEquals(66, projection.getYearAt(2).getPrimaryPersonAge());
        assertEquals(67, projection.getYearAt(3).getPrimaryPersonAge());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2027-01-01",
            "2027-03-01",
            "2027-06-03",
            "2027-06-04",
            "2027-06-05",
            "2027-12-31"
    })
    void firstYearReportingAgeDoesNotDependOnProjectionStartDate(
            String projectionStartDate) {

        ProjectionYear year =
                project(
                        LocalDate.of(1963, 6, 4),
                        LocalDate.parse(projectionStartDate),
                        1)
                        .getFirstYear();

        assertEquals(64, year.getPrimaryPersonAge());
    }

    @Test
    void usesExistingPersonAgeBehaviorForFebruaryTwentyNinthBirthDate() {

        ProjectionYear year =
                project(
                        LocalDate.of(1964, 2, 29),
                        LocalDate.of(2027, 6, 1),
                        1)
                        .getFirstYear();

        assertEquals(63, year.getPrimaryPersonAge());
    }

    @Test
    void yearEndReportingAgeDoesNotMakeFirstYearMedicareEligibleEarly() {

        ProjectionYear year =
                project(
                        LocalDate.of(1962, 6, 4),
                        LocalDate.of(2027, 3, 1),
                        1)
                        .getFirstYear();

        assertEquals(65, year.getPrimaryPersonAge());
        assertEquals(
                0,
                year.getMedicarePremiumCalculation()
                        .coveredMedicareParticipants());
    }

    @Test
    void csvUsesStoredYearEndReportingAge()
            throws IOException {

        Projection projection =
                project(
                        LocalDate.of(1963, 6, 4),
                        LocalDate.of(2027, 3, 1),
                        1);

        Path csv =
                temporaryDirectory.resolve(
                        "projection-age.csv");

        new ProjectionCsvExporter().export(
                projection,
                List.of(),
                csv);

        String[] values =
                Files.readAllLines(csv)
                        .get(1)
                        .split(",", -1);

        assertEquals("2027", values[1]);
        assertEquals("64", values[2]);
    }

    private Projection project(
            LocalDate primaryBirthDate,
            LocalDate projectionStartDate,
            int projectionLengthYears) {

        Person primary =
                new Person(
                        "Primary",
                        "Person",
                        primaryBirthDate);

        Person spouse =
                new Person(
                        "Spouse",
                        "Person",
                        LocalDate.of(1990, 1, 1));

        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Age reporting funding account",
                AccountOwnership.PRIMARY,
                new BigDecimal("100000")));

        RetirementPlan plan =
                new RetirementPlan(
                        new Household(primary, spouse),
                        portfolio,
                        new PlanningAssumptions(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                projectionLengthYears,
                                projectionStartDate));

        return new ProjectionEngine().project(plan);
    }
}
