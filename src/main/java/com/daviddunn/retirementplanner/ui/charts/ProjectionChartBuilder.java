package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import javafx.scene.chart.XYChart;

public class ProjectionChartBuilder {

    public XYChart.Series<Number, Number>
    buildEndingAssetsSeries(Projection projection) {

        XYChart.Series<Number, Number> series =
                new XYChart.Series<>();

        series.setName("Portfolio Value");

        for (ProjectionYear year : projection.getYears()) {

//            System.out.println(
//                    "Year = " + year.getCalendarYear()
//                            + ", Assets = "
//                            + year.getEndingInvestableAssets());

            series.getData().add(
                    new XYChart.Data<>(
                            year.getCalendarYear(),
                            year.getEndingInvestableAssets().doubleValue()));
        }
        return series;
    }
}