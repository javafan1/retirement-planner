package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.Projection;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;

public class PortfolioChartView extends BorderPane {

    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();

    private final LineChart<Number, Number> chart;

    private final ProjectionChartBuilder builder =
            new ProjectionChartBuilder();

    public PortfolioChartView() {

        xAxis.setLabel("Year");
        yAxis.setLabel("Portfolio Value");
        xAxis.setTickLabelFormatter(new StringConverter<>() {

            @Override
            public String toString(Number value) {
                return Integer.toString(value.intValue());
            }

            @Override
            public Number fromString(String string) {
                return Integer.parseInt(string);
            }
        });

        chart = new LineChart<>(xAxis, yAxis);

        chart.setAnimated(false);
        chart.setCreateSymbols(false);

        setCenter(chart);
    }

    public void load(Projection projection) {

        chart.getData().clear();

        if (projection == null || projection.isEmpty()) {
            return;
        }

        int firstYear =
                projection.getYears()
                        .get(0)
                        .getCalendarYear();

        int lastYear =
                projection.getYears()
                        .get(projection.getYears().size() - 1)
                        .getCalendarYear();

        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(firstYear);
        xAxis.setUpperBound(lastYear);
        xAxis.setTickUnit(5);

        chart.getData().add(
                builder.buildEndingAssetsSeries(projection));
    }
}