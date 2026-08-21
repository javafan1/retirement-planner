package com.daviddunn.retirementplanner.domain.noninvestable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NonInvestableAssetProjectionService {

    public BigDecimal projectValue(
            NonInvestableAsset asset,
            int years) {

        Objects.requireNonNull(
                asset,
                "Asset is required.");

        if (years < 0) {
            throw new IllegalArgumentException(
                    "Years cannot be negative.");
        }

        BigDecimal growthFactor =
                BigDecimal.ONE.add(
                        asset.getAnnualGrowthRate());

        return asset.getCurrentValue()
                .multiply(
                        growthFactor.pow(years))
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }


    public List<NonInvestableAssetProjection>
    project(
            List<NonInvestableAsset> assets,
            int startYear,
            int endYear) {

        Objects.requireNonNull(
                assets,
                "Assets are required.");

        if (startYear > endYear) {
            throw new IllegalArgumentException(
                    "Start year cannot be after end year.");
        }

        List<NonInvestableAssetProjection>
                projections =
                new ArrayList<>();

        for (int year = startYear;
             year <= endYear;
             year++) {

            List<NonInvestableAssetValue>
                    assetValues =
                    new ArrayList<>();

            BigDecimal totalValue =
                    BigDecimal.ZERO;

            int yearsFromStart =
                    year - startYear;

            for (NonInvestableAsset asset :
                    assets) {

                BigDecimal projectedValue =
                        projectValue(
                                asset,
                                yearsFromStart);

                assetValues.add(
                        new NonInvestableAssetValue(
                                asset.getName(),
                                projectedValue));

                totalValue =
                        totalValue.add(
                                projectedValue);
            }

            projections.add(
                    new NonInvestableAssetProjection(
                            year,
                            assetValues,
                            totalValue
                                    .setScale(
                                            2,
                                            RoundingMode.HALF_UP)));
        }

        return projections;
    }

    public NonInvestableAssetProjection
    findForYear(
            List<NonInvestableAssetProjection> projections,
            int calendarYear) {

        Objects.requireNonNull(
                projections,
                "Projections are required.");

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .orElse(null);
    }

}