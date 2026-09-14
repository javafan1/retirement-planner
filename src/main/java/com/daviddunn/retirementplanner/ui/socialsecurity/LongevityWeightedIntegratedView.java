package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** FX-confined controls and formatting. No calculations or background lifecycle ownership. */
final class LongevityWeightedIntegratedView extends VBox {
    static final String LIMITATION = "Model limitation: Inherited-account retitling and distribution rules are not modeled. "
            + "Deceased-owner balances remain modeled household assets. This can materially affect estate estimates. "
            + "The estate metric excludes non-investable assets.";
    static final String METHODOLOGY = "This analysis uses mortality probabilities for both spouses to evaluate possible "
            + "combinations of death years, assuming their mortality is independent. Each claiming strategy is evaluated "
            + "through the retirement plan, including taxes, RMDs, Roth conversions, pensions, Medicare, withdrawals and "
            + "investment growth. After-tax investable estate is measured at household second death, adjusted for general "
            + "inflation, discounted at the real discount rate to the analyzer valuation date, and probability weighted.";
    static final String MISSING_BASELINE = "Current-strategy comparison unavailable. Configure a complete survivor claiming "
            + "policy to compare against the current strategy. No survivor age has been assumed.";
    final Button run = new Button("Run Longevity-Weighted Exhaustive Search");
    final Button exportPdf = new Button("Export PDF");
    final Label stale = label("");
    final Label status = label("Choose longevity assumptions, then run analysis. No Social Security-only run is required.");
    final TableView<LongevityWeightedIntegratedStrategyComparisonEntry> table = new TableView<>();
    private final Label highest = label("Run analysis to display the highest tested strategy.");
    private final Label current = label("Current strategy will appear after analysis.");
    private final Label detail = label("");
    private final TitledPane detailSection = fold("Selected Strategy Details", detail, false);
    final ClaimingStrategyHeatMapView heatMap = new ClaimingStrategyHeatMapView(this::openHeatMapStrategy);
    final VBox rankedContent = new VBox(6);
    final Tab rankedTab = new Tab("Ranked Strategies", rankedContent);
    final Tab heatMapTab = new Tab("Claiming-Age Heat Map", heatMap);
    final TabPane resultTabs = new IntegratedAnalysisResultTabs(rankedTab, heatMapTab);
    private final Label technical = label("");
    private final VBox comparison = new VBox(6);
    private final Label methodology = label(METHODOLOGY);
    private LongevityWeightedIntegratedPresentation model;
    private LongevityWeightedIntegratedStrategyComparisonEntry selectedEntry;
    private IntegratedSocialSecurityCompleteStrategySearchResult deterministicReference;

    LongevityWeightedIntegratedView() {
        super(6);
        Label limitation = label(LIMITATION);
        limitation.setStyle("-fx-border-color: #9a6700; -fx-padding: 6;");
        getChildren().addAll(label("Rank complete claiming strategies by Expected PV After-Tax Estate across modeled household lifespans."),
                run, status, stale, limitation);
        HBox cards = new HBox(12, card("Highest Longevity-Weighted Tested Strategy", highest), card("Current Strategy", current));
        rankedContent.getChildren().add(fold("Highest and Current Strategy", cards, false));
        configureTable();
        rankedContent.getChildren().add(table);
        VBox.setVgrow(table, Priority.NEVER);
        table.setMinHeight(110);
        exportPdf.setDisable(true);
        getChildren().addAll(new HBox(10, label("Completed Analysis"), exportPdf), resultTabs);
        heatMap.resultNotice.textProperty().bind(stale.textProperty());
        rankedContent.getChildren().addAll(fold("Compare Analysis Outcomes", comparison, false),
                detailSection,
                fold("Methodology and Result-Time Assumptions", methodology, false),
                fold("Technical Details", technical, false));
        comparison.getChildren().add(label(IntegratedAnalysisComparisonPresentation.EXPLANATION));
        table.getSelectionModel().selectedItemProperty().addListener((o, before, selected) -> showDetail(selected));
    }

    private void configureTable() {
        var rank = column("Weighted Rank", 90, entry -> entry.rank().isPresent() ? entry.rank().getAsInt() : null,
                value -> value.toString());
        rank.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                var row = getTableRow() == null ? null : getTableRow().getItem();
                setText(empty || row == null ? null : (value == null ? "Failed" : value.toString())
                        + (model == null || model.marker(row).isEmpty() ? "" : "\n" + model.marker(row)));
            }
        });
        column("Primary Retirement", 100, entry -> entry.strategy().primaryRetirementAge(), value -> "Age " + value);
        column("Spouse Retirement", 100, entry -> entry.strategy().spouseRetirementAge(), value -> "Age " + value);
        column("Primary Survivor", 110, entry -> entry.strategy().primarySurvivorElection().ageYears() * 12
                + entry.strategy().primarySurvivorElection().ageMonths(), LongevityWeightedIntegratedView::age);
        column("Spouse Survivor", 110, entry -> entry.strategy().spouseSurvivorElection().ageYears() * 12
                + entry.strategy().spouseSurvivorElection().ageMonths(), LongevityWeightedIntegratedView::age);
        column("Expected Investable Assets at Second Death", 165, entry -> entry.aggregate()
                .map(LongevityWeightedStrategyAggregate::expectedInvestableAssetsAtSecondDeath).orElse(null), LongevityWeightedIntegratedView::money);
        column("Expected After-Tax Heir Value", 155, entry -> entry.aggregate()
                .map(LongevityWeightedStrategyAggregate::expectedNominalEstateAtSecondDeath).orElse(null), LongevityWeightedIntegratedView::money);
        column("Expected PV After-Tax Estate", 160, entry -> entry.aggregate()
                .map(LongevityWeightedStrategyAggregate::expectedPvAfterTaxEstate).orElse(null), LongevityWeightedIntegratedView::money);
        column("Difference vs Current", 140, entry -> entry.pvDifferenceFromBaseline().orElse(null), LongevityWeightedIntegratedView::signed);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getSortOrder().add(rank);
        table.setPlaceholder(label("Run longevity-weighted exhaustive search to display strategies."));
    }

    private <T extends Comparable<? super T>> TableColumn<LongevityWeightedIntegratedStrategyComparisonEntry, T> column(
            String title, double width, Function<LongevityWeightedIntegratedStrategyComparisonEntry, T> value,
            Function<T, String> format) {
        TableColumn<LongevityWeightedIntegratedStrategyComparisonEntry, T> column = new TableColumn<>();
        Label header = label(title);
        header.setMaxWidth(width - 12);
        column.setGraphic(header);
        header.setTooltip(new Tooltip(title.startsWith("Expected PV")
                ? "Valuation-date present value; ranking objective"
                : title.startsWith("Expected") ? "Nominal future-dollar expected value across second-death dates"
                : title.equals("Difference vs Current") ? "Candidate expected PV minus current expected PV" : title));
        column.setPrefWidth(width);
        column.setMinWidth(65);
        column.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "Unavailable" : format.apply(item));
            }
        });
        table.getColumns().add(column);
        return column;
    }

    void render(LongevityWeightedIntegratedPresentation value, String knownRetirementElections) {
        render(value, value.result().baseline().map(base -> elections(base.strategy())).orElse(knownRetirementElections), MISSING_BASELINE);
    }

    void render(LongevityWeightedIntegratedPresentation value, CurrentStrategyBaseline baseline) {
        render(value, baseline.summary(), baseline.problem());
    }

    private void render(LongevityWeightedIntegratedPresentation value, String knownElections, String baselineProblem) {
        model = value;
        stale.setText("");
        var result = value.result();
        String date = "\nValuation date: " + result.metadata().valuationDate();
        highest.setText(value.highest().map(entry -> elections(entry.strategy())
                + metrics(entry.aggregate().orElseThrow()) + date
                + "\nWeighted rank: " + entry.rank().orElseThrow()
                + "\nTied highest strategies: " + value.highestTieCount()
                + entry.pvDifferenceFromBaseline().map(delta -> "\nPV Difference vs Current: " + signed(delta)
                        + " / " + value.improvementPercent().map(percent -> percent + "%").orElse("percentage unavailable"))
                        .orElse("\nPV Difference vs Current: Unavailable"))
                .orElse("No successful candidate; no highest strategy is available."));
        current.setText(result.baseline().map(base -> knownElections
                + base.aggregate().map(aggregate -> metrics(aggregate) + "\nPV Difference vs Current: " + money(BigDecimal.ZERO)
                        + date + "\n" + (value.currentHasCandidateRank() ? "Weighted rank: " : "Metric position among tested strategies: ")
                        + value.currentPosition().orElseThrow()
                        + "\nTested strategies tied at current expected PV: " + value.currentTieCount()
                        + value.highest().map(best -> "\nDifference from Highest Strategy (current minus highest PV): "
                                + signed(aggregate.expectedPvAfterTaxEstate().subtract(LongevityWeightedIntegratedPresentation.pv(best))))
                                .orElse("\nDifference from Highest Strategy: Unavailable")
                        + value.candidate(base.strategy()).map(entry -> "\nMatching candidate's proven-equivalence group size: "
                                + value.provenEquivalentCount(entry.inputOrder())).orElse(""))
                        .orElseGet(() -> "\nBaseline evaluation unavailable: " + base.failure().orElseThrow().message()))
                .orElse(knownElections + "\n" + baselineProblem));
        table.getItems().setAll(result.orderedEntries());
        table.sort();
        table.getSelectionModel().selectFirst();
        heatMap.render(LongevityWeightedHeatMapAdapter.from(value));
        status.setText("Complete: " + result.completedStrategyCount() + " successful; " + result.failedStrategyCount()
                + " failed candidates. " + (result.baseline().filter(base -> !base.successful()).isPresent() ? "Baseline failed separately." : ""));
        methodology.setText(METHODOLOGY + "\n\nJanuary 1 modeled deaths use the preceding December 31 ending estate or matching opening snapshot. "
                + "Each mortality scenario projects only through its required second-death estate snapshot; "
                + "the configured plan horizon does not extend that scenario. Opening snapshots require no projection. "
                + "Discount timing is actual days / 365.25. Original probabilities are not renormalized.\n"
                + "Mortality conditioning convention: " + result.metadata().longevityAssumptions().partialYearConvention()
                + "\nMortality conditioning date: " + result.metadata().longevityAssumptions().mortalityBaseDate()
                + "\nValuation date: " + result.metadata().valuationDate()
                + "\nReal discount rate: " + result.metadata().realDiscountRate()
                + "\nGeneral inflation rate: " + result.metadata().generalInflationRate()
                + "\nMortality assumptions: " + result.metadata().longevityAssumptions()
                + "\nAccount tax classification remains unchanged after death; estimated heir-tax haircut remains simplified.\n"
                + String.join("\n", result.metadata().financialLimitations()));
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    void comparisons(List<IntegratedAnalysisComparisonPresentation.Row> rows) {
        comparison.getChildren().setAll(label(IntegratedAnalysisComparisonPresentation.EXPLANATION),
                label("Unavailable deterministic values require a current compatible deterministic exhaustive result. Highest rows represent the first tied strategy when applicable."));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        String[] headers = {"Strategy / Role", "Deterministic - Configured Horizon",
                "Longevity-Weighted - Second Death"};
        for (int column = 0; column < headers.length; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(column == 0 ? 30 : 35);
            grid.getColumnConstraints().add(constraint);
            grid.add(label(headers[column]), column, 0);
        }
        int index = 1;
        for (var row : rows) {
            grid.addRow(index++, label(row.role() + "\n" + elections(row.strategy())),
                    label("Estate rank: " + rank(row.deterministicRank())
                            + "\nFuture dollars at configured horizon:"
                            + "\nEnding Investable Assets: " + optionalMoney(row.deterministicInvestableAssets())
                            + "\nAfter-Tax Heir Value: " + optionalMoney(row.deterministicEstate())),
                    label("Weighted rank: " + rank(row.weightedRank())
                            + "\nNominal future-dollar expectations:"
                            + "\nExpected Investable Assets at Second Death: " + optionalMoney(row.weightedInvestableAssets())
                            + "\nExpected After-Tax Heir Value: " + optionalMoney(row.weightedHeirValue())
                            + "\nExpected PV After-Tax Estate (valuation-date dollars; ranking objective): "
                            + optionalMoney(row.weightedPv())));
        }
        comparison.getChildren().add(grid);
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void openHeatMapStrategy(int inputOrder) {
        if (model == null) return;
        model.result().orderedEntries().stream().filter(entry -> entry.inputOrder() == inputOrder).findFirst().ifPresent(entry -> {
            resultTabs.getSelectionModel().select(rankedTab);
            table.getSelectionModel().select(entry);
            table.scrollTo(entry);
            detailSection.setExpanded(true);
            detailSection.requestFocus();
            javafx.application.Platform.runLater(() -> IntegratedAnalysisResultTabs.reveal(detailSection));
        });
    }

    void deterministicReference(IntegratedSocialSecurityCompleteStrategySearchResult result) {
        deterministicReference = result;
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    void initialCurrent(String knownElections, boolean missingPolicy) {
        current.setText(knownElections + (missingPolicy ? "\nSurvivor policy: incomplete\n" + MISSING_BASELINE
                : "\nRun analysis to value the complete current strategy."));
    }

    void initialCurrent(CurrentStrategyBaseline baseline) {
        current.setText(baseline.summary() + "\n" + (baseline.strategy().isPresent()
                ? "Run analysis to value the complete current strategy." : baseline.problem()));
    }

    private void showDetail(LongevityWeightedIntegratedStrategyComparisonEntry entry) {
        if (entry == null || model == null) return;
        selectedEntry = entry;
        var result = model.result();
        technical.setText("Reported job work: " + result.work() + "\nEquivalence groups: "
                + result.equivalencePlan().map(plan -> Integer.toString(plan.equivalenceGroupCount())).orElse("Unavailable")
                + "\nElapsed: " + result.elapsedTime() + "\nEvaluations avoided: " + result.evaluationsAvoided()
                + "\nSelected strategy reported ProjectionEngine runs: "
                + entry.aggregate().map(aggregate -> Integer.toString(aggregate.actualProjectionRunCount())).orElse("Unavailable"));
        String text = "Original strategy occurrence: " + entry.inputOrder() + "\n" + elections(entry.strategy());
        if (!entry.successful()) {
            detail.setText(text + "\nUnavailable: " + entry.failure().orElseThrow().message());
            return;
        }
        var aggregate = entry.aggregate().orElseThrow();
        text += metrics(aggregate)
                + "\nMinimum nominal scenario estate: " + money(aggregate.minimumNominalScenarioEstate())
                + "\nMaximum nominal scenario estate: " + money(aggregate.maximumNominalScenarioEstate())
                + "\nEvaluated probability coverage: " + aggregate.totalEvaluatedProbability()
                + "\nScenario count: " + aggregate.originalScenarioCount()
                + "\nPV Difference vs Current: " + entry.pvDifferenceFromBaseline().map(LongevityWeightedIntegratedView::signed).orElse("Unavailable")
                + "\nProven-equivalence group size: " + model.provenEquivalentCount(entry.inputOrder())
                + " (equal rounded values do not establish ties or equivalence)";
        text += "\nDeterministic rank: " + (deterministicReference == null ? "Unavailable"
                : rank(deterministicReference.rankOf(entry.strategy())))
                + "\n\n" + METHODOLOGY + "\n\n" + LIMITATION
                + "\n\nScenario detail loading is not available in this release; compact aggregates are shown.";
        detail.setText(text);
    }

    private static String metrics(LongevityWeightedStrategyAggregate aggregate) {
        return "\nNominal future-dollar expectations across second-death dates:"
                + "\nExpected Investable Assets at Second Death: " + money(aggregate.expectedInvestableAssetsAtSecondDeath())
                + "\nExpected After-Tax Heir Value: " + money(aggregate.expectedNominalEstateAtSecondDeath())
                + "\nExpected PV After-Tax Estate (ranking objective; valuation-date dollars): " + money(aggregate.expectedPvAfterTaxEstate());
    }

    LongevityWeightedIntegratedStrategyComparisonEntry reportSelection() { return selectedEntry; }

    static String elections(SocialSecurityHouseholdClaimingStrategy strategy) {
        return "Primary retirement: Age " + strategy.primaryRetirementAge() + " — " + strategy.primaryRetirementClaimDate()
                + "\nSpouse retirement: Age " + strategy.spouseRetirementAge() + " — " + strategy.spouseRetirementClaimDate()
                + "\nPrimary survivor: " + strategy.primarySurvivorElection().label() + " — " + strategy.primarySurvivorElection().claimDate()
                + "\nSpouse survivor: " + strategy.spouseSurvivorElection().label() + " — " + strategy.spouseSurvivorElection().claimDate();
    }
    private static String optionalMoney(Optional<BigDecimal> value) { return value.map(LongevityWeightedIntegratedView::money).orElse("Unavailable"); }
    private static String rank(OptionalInt rank) { return rank.isPresent() ? Integer.toString(rank.getAsInt()) : "Unavailable"; }
    private static String age(Integer months) { return "Age " + months / 12 + (months % 12 == 0 ? "" : "y " + months % 12 + "m"); }
    private static String money(BigDecimal amount) { return UIFormatters.money(amount); }
    private static String signed(BigDecimal amount) { return (amount.signum() > 0 ? "+" : "") + money(amount); }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
    private static VBox card(String title, Label text) {
        VBox box = new VBox(4, label(title), text); box.setMinWidth(0); box.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(box, Priority.ALWAYS); return box;
    }
    private static TitledPane fold(String title, javafx.scene.Node content, boolean expanded) {
        TitledPane pane = new TitledPane(title, content);
        // Keep the full width-dependent content height; the outer viewport scrolls.
        pane.setMinHeight(Region.USE_PREF_SIZE);
        pane.setMaxHeight(Region.USE_PREF_SIZE);
        pane.setExpanded(expanded);
        pane.setAnimated(false);
        return pane;
    }
}
